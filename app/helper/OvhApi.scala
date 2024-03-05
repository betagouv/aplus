package helper

import cats.syntax.all._
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.ZonedDateTime
import models.{Error, EventType}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{PlayBodyParsers, Request}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

object OvhApi {

  case class SmsId(underlying: Long)

  object SmsId {
    implicit val reads: Reads[SmsId] = implicitly[Reads[Long]].map(SmsId.apply)

    implicit val writes: Writes[SmsId] =
      implicitly[Writes[Long]].contramap((id: SmsId) => id.underlying)

  }

  /** https://eu.api.ovh.com/console/#/sms/{serviceName}/jobs#POST */
  case class SmsJob(
      message: String,
      receivers: List[String],
      // Note: the sender is not always visible
      // Note 2: {"message":"must have less than 14 chars for value sender"}
      sender: Option[String],
      senderForResponse: Option[Boolean]
  )

  object SmsJob {
    implicit val reads: Reads[SmsJob] = Json.reads[SmsJob]
    implicit val writes: Writes[SmsJob] = Json.writes[SmsJob]
  }

  case class SmsSendingReport(
      ids: List[SmsId], // usable in /sms/{serviceName}/jobs/{id} & /sms/{serviceName}/outgoing/{id}
      invalidReceivers: List[String],
      totalCreditsRemoved: Double,
      validReceivers: List[String]
  )

  object SmsSendingReport {
    implicit val reads: Reads[SmsSendingReport] = Json.reads[SmsSendingReport]
    implicit val writes: Writes[SmsSendingReport] = Json.writes[SmsSendingReport]
  }

  case class IncomingSms(
      id: SmsId,
      message: String,
      // International Phone Number (beginning with '+')
      sender: String,
      creationDatetime: ZonedDateTime
  )

  object IncomingSms {
    implicit val reads: Reads[IncomingSms] = Json.reads[IncomingSms]
    implicit val writes: Writes[IncomingSms] = Json.writes[IncomingSms]
  }

}

/** For the Auth, see https://docs.ovh.com/gb/en/customer/first-steps-with-ovh-api/
  *
  * API Doc: https://docs.ovh.com/fr/sms/api_sms_cookbook/ https://eu.api.ovh.com/console/#/sms
  */
final class OvhApi(
    bodyParsers: PlayBodyParsers,
    ws: WSClient,
    serviceName: String,
    applicationKey: String,
    applicationSecret: String,
    consumerKey: String,
    requestTimeout: FiniteDuration,
    implicit val executionContext: ExecutionContext,
    implicit val materializer: Materializer
) {
  import OvhApi._

  /** API Doc: https://eu.api.ovh.com/console/#/sms/{serviceName}/jobs#POST
    */
  def sendSms(
      message: String,
      recipient: String
  ): Future[Either[Error, SmsId]] =
    createJob(message, List(recipient)).map(_.flatMap { report =>
      def anonymousPhone(intlPhone: String): String =
        (intlPhone.take(4) ++ intlPhone.drop(4).map(_ => '*')).mkString
      def errorMessage: String =
        s"Erreur dans l'envoi de SMS à ${anonymousPhone(recipient)}. " +
          s"Jobs ids: ${report.ids}. " +
          s"Numéros invalides: ${report.invalidReceivers.map(anonymousPhone)}. " +
          s"Numéros valides: ${report.validReceivers.map(anonymousPhone)}. " +
          s"Crédits utilisés: ${report.totalCreditsRemoved}"

      (report.ids, report.validReceivers) match {
        case (id :: otherIds, validReceiver :: otherReceivers) =>
          if (validReceiver === recipient && otherIds.isEmpty && otherReceivers.isEmpty) {
            Right(id)
          } else {
            Left(
              Error.MiscException(
                EventType.SmsSendError,
                errorMessage,
                new Exception(errorMessage),
                none
              )
            )
          }
        case _ =>
          Left(
            Error.MiscException(
              EventType.SmsSendError,
              errorMessage,
              new Exception(errorMessage),
              none
            )
          )
      }
    })

  /** OVH Interface:
    *   - Do put the callback URL under "General options"
    *   - Put the callback URL in "Reply options"
    *
    * API:
    *   - Content-Type: application/x-www-form-urlencoded
    */
  def smsReceivedCallback(request: Request[String]): Future[Either[Error, IncomingSms]] =
    bodyParsers
      .tolerantFormUrlEncoded(request) // Do not check Content-Type
      .run(ByteString(request.body))
      .map(
        _.fold(
          errorResult => throw new Exception(s"Cannot read webhook request body: $errorResult"),
          _.get("id") match {
            case Some(stringIds) =>
              SmsId(stringIds.head.toLong)
            case _ =>
              throw new Exception(s"Cannot read field 'id' in webhook request")
          }
        )
      )
      .flatMap(fetchIncomingSmsById)
      .recover(e =>
        Left(
          Error.MiscException(
            EventType.SmsCallbackError,
            "Impossible de lire le callback",
            e,
            none
          )
        )
      )

  /** API Doc: https://eu.api.ovh.com/console/#/sms/{serviceName}/incoming/{id}#DELETE
    */
  def deleteIncomingSms(id: SmsId): Future[Either[Error, Unit]] = {
    val url = s"https://eu.api.ovh.com/1.0/sms/$serviceName/incoming/${id.underlying}"
    val errorMessage = s"Impossible de supprimer le message reçu ${id.underlying}"
    ws.url(url)
      .addHttpHeaders(requestHeaders("DELETE", url, ""): _*)
      .withRequestTimeout(requestTimeout)
      .delete()
      .map(readResponse(_ => (), EventType.SmsDeleteError, errorMessage))
      .recover(e => recoverApiCall(e, EventType.SmsDeleteError, errorMessage))
  }

  /** API Doc: https://eu.api.ovh.com/console/#/sms/{serviceName}/outgoing/{id}#DELETE
    */
  def deleteOutgoingSms(id: SmsId): Future[Either[Error, Unit]] = {
    val url = s"https://eu.api.ovh.com/1.0/sms/$serviceName/outgoing/${id.underlying}"
    val errorMessage = s"Impossible de supprimer le message envoyé ${id.underlying}"
    ws.url(url)
      .addHttpHeaders(requestHeaders("DELETE", url, ""): _*)
      .withRequestTimeout(requestTimeout)
      .delete()
      .map(readResponse(_ => (), EventType.SmsDeleteError, errorMessage))
      .recover(e => recoverApiCall(e, EventType.SmsDeleteError, errorMessage))
  }

  /** API Doc: https://eu.api.ovh.com/console/#/sms/{serviceName}/jobs#POST
    *
    * List of experienced errors:
    * ```
    * java.lang.Exception
    * $response.status = 500
    * $response = AhcWSResponse(StandaloneAhcWSResponse(500, Internal Server Error))
    * $response.body = {"message":"Internal server error"}
    *
    * java.lang.Exception
    * $response.status = 503
    * $response = AhcWSResponse(StandaloneAhcWSResponse(503, Service Unavailable))
    * $response.body = <html> ...(HTML).. <title>Error 503: server unavailable</title> ...
    *
    * java.lang.Exception
    * $response.status = 402
    * $response = AhcWSResponse(StandaloneAhcWSResponse(402, Payment Required))
    * $response.body = {"message":"Not enough credits (left: 2.00)"}
    *
    * java.util.concurrent.TimeoutException
    * Request timeout to eu.api.ovh.com/51.38.17.223:443 after 5000 ms
    * ```
    */
  def createJob(
      message: String,
      recipients: List[String]
  ): Future[Either[Error, SmsSendingReport]] = {
    val url = s"https://eu.api.ovh.com/1.0/sms/$serviceName/jobs"
    val errorMessage = s"Impossible d'envoyer le SMS"
    val request = SmsJob(
      message = message,
      receivers = recipients,
      sender = None,
      senderForResponse = Some(true)
    )
    val body = Json.stringify(Json.toJson(request))
    ws.url(url)
      .addHttpHeaders(requestHeaders("POST", url, body): _*)
      .withRequestTimeout(requestTimeout)
      .post(body)
      .map(readResponse(_.body[JsValue].as[SmsSendingReport], EventType.SmsSendError, errorMessage))
      .recover(e => recoverApiCall(e, EventType.SmsSendError, errorMessage))
  }

  /** API Doc: https://eu.api.ovh.com/console/#/sms/{serviceName}/incoming/{id}#GET
    */
  def fetchIncomingSmsById(id: SmsId): Future[Either[Error, IncomingSms]] = {
    val url = s"https://eu.api.ovh.com/1.0/sms/$serviceName/incoming/${id.underlying}"
    val errorMessage = s"Impossible de lire le SMS ${id.underlying} chez le provider distant"
    ws.url(url)
      .addHttpHeaders(requestHeaders("GET", url, ""): _*)
      .withRequestTimeout(requestTimeout)
      .get()
      .map(readResponse(_.body[JsValue].as[IncomingSms], EventType.SmsSendError, errorMessage))
      .recover(e => recoverApiCall(e, EventType.SmsReadError, errorMessage))
  }

  private def readResponse[A](
      onStatus200: WSResponse => A,
      errorType: EventType,
      errorDescription: => String
  )(response: WSResponse): Either[Error, A] =
    if (response.status === 200) {
      onStatus200(response).asRight
    } else {
      Error
        .UnexpectedServerResponse(
          errorType,
          errorDescription + s" (status ${response.status})",
          response.status,
          new Exception(
            s"Unexpected response from OVH server (status ${response.status})" +
              s": $response - ${response.body}"
          ),
          s"Réponse '$response' / '${response.body}'".some
        )
        .asLeft
    }

  private def recoverApiCall[A](
      e: Throwable,
      errorType: EventType,
      errorDescription: String
  ): Either[Error, A] = e match {
    case e: java.util.concurrent.TimeoutException =>
      Error.Timeout(errorType, errorDescription, e, none).asLeft
    case e =>
      Error.MiscException(errorType, errorDescription, e, none).asLeft
  }

  private def requestHeaders(method: String, query: String, body: String): Seq[(String, String)] = {
    // Note: OVH provides an API if the server clock deviates too much
    // https://eu.api.ovh.com/1.0/auth/time
    val timestamp: String = (System.currentTimeMillis() / 1000).toString

    // "$1$" + SHA1_HEX(AS+"+"+CK+"+"+METHOD+"+"+QUERY+"+"+BODY+"+"+TSTAMP)
    val toHash = List(applicationSecret, consumerKey, method, query, body, timestamp).mkString("+")
    val hashBlob: List[Byte] =
      MessageDigest.getInstance("SHA-1").digest(toHash.getBytes(UTF_8)).toList
    val hash = "$1$" + hashBlob.map("%02x".format(_)).mkString
    val headers = Seq(
      "Accept" -> "application/json",
      "Content-Type" -> "application/json",
      "X-Ovh-Application" -> applicationKey,
      "X-Ovh-Timestamp" -> timestamp,
      "X-Ovh-Signature" -> hash, // Note: a hash as a signature is weird
      "X-Ovh-Consumer" -> consumerKey
    )
    headers
  }

}
