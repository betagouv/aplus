package services

import helper.Time
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, User}
import play.api.libs.json.{JsPath, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.Request
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import serializers.ApiModel.{ApiSms, CompleteSms, SmsSendRequest}

/** Various infos:
  * https://support.messagebird.com/hc/en-us/articles/208015009-France
  * "Each Virtual Number has a daily limit of approximately 500 SMS per number, per day."
  */
@Singleton
class SmsService @Inject() (
    bodyParsers: play.api.mvc.PlayBodyParsers,
    configuration: play.api.Configuration,
    eventService: EventService,
    futures: play.api.libs.concurrent.Futures,
    ws: WSClient
)(implicit ec: ExecutionContext) {

  private val useLiveApi: Boolean = configuration
    .getOptional[Boolean]("app.smsUseLiveApi")
    .getOrElse(false)

  private val apiKey: String =
    if (useLiveApi) configuration.get[String]("app.smsApiKey") else ""

  private val signingKey: String =
    if (useLiveApi) configuration.get[String]("app.smsSigningKey") else ""

  private val aplusPhoneNumber: String =
    if (useLiveApi) configuration.get[String]("app.smsPhoneNumber") else ""

  private val requestTimeout = 2.seconds

  private val serverPort: String =
    Option(System.getProperty("http.port"))
      .getOrElse(configuration.underlying.getInt("play.server.http.port").toString)

  def sendSms(body: String, recipients: List[String]): Future[Either[Error, CompleteSms]] =
    if (useLiveApi)
      liveSendSms(body, recipients)
    else
      fakeSendSms(body, recipients)

  def smsReceivedCallback(request: Request[String]): Future[Either[Error, CompleteSms]] =
    if (useLiveApi)
      liveSmsReceivedCallback(request)
    else
      fakeSmsReceivedCallback(request)

  //
  // Live API for review and prod
  //

  /** API Doc:
    * https://developers.messagebird.com/api/sms-messaging/#send-outbound-sms
    */
  private def liveSendSms(
      body: String,
      recipients: List[String]
  ): Future[Either[Error, CompleteSms]] = {
    val request = SmsSendRequest(
      originator = aplusPhoneNumber,
      body = body,
      recipients = recipients
    )
    ws.url("https://rest.messagebird.com/messages")
      .addHttpHeaders("Accept" -> "application/json", "Authorization" -> s"AccessKey $apiKey")
      .withRequestTimeout(requestTimeout)
      .post(Json.toJson(request))
      .map { response =>
        if ((response.status: Int) == 201) {
          val json = response.body[JsValue]
          Right(CompleteSms(json.as[ApiSms], json))
        } else {
          throw new Exception(
            s"Unknown response from MB server (status ${response.status})" +
              s": $response - ${response.body}"
          )
        }
      }
      .recover {
        case (e: Throwable) =>
          Left(
            Error.MiscException(
              EventType.SmsSendError,
              s"Impossible d'envoyer un SMS à partir du numéro $aplusPhoneNumber",
              e
            )
          )
      }
  }

  /** https://developers.messagebird.com/api/#verifying-http-requests */
  private def checkMessageBirdSignature(
      key: String,
      signature: String,
      timestamp: String,
      queryString: String,
      body: String
  ): Boolean = {
    val secretKey = new SecretKeySpec(key.getBytes(UTF_8), "HmacSHA256")
    val expectedSignature: List[Byte] = Base64.getDecoder().decode(signature).toList
    val blobToHmacPart1: String = timestamp + "\n" + queryString + "\n"
    val bodyHash: List[Byte] =
      MessageDigest.getInstance("SHA-256").digest(body.getBytes(UTF_8)).toList
    val blobToHmac: List[Byte] = blobToHmacPart1.getBytes(UTF_8).toList ::: bodyHash
    val mac = Mac.getInstance("HmacSHA256");
    mac.init(secretKey);
    val requestSignature: List[Byte] = mac.doFinal(blobToHmac.toArray).toList
    (requestSignature: List[Byte]) == (expectedSignature: List[Byte])
  }

  private def checkSignature(request: Request[String]): Boolean = {
    val mbTimestamp = request.headers
      .get("MessageBird-Request-Timestamp")
      .getOrElse(
        throw new Exception(s"Cannot read webhook request header MessageBird-Request-Timestamp")
      )
    val mbSignature = request.headers
      .get("MessageBird-Signature")
      .getOrElse(throw new Exception(s"Cannot read webhook request header MessageBird-Signature"))

    val requestTimestampInSecs: Long = mbTimestamp.toLong
    val nowInSecs: Long = Instant.now().toEpochMilli / 1000
    val timestampIsWithing1d: Boolean =
      scala.math.abs(nowInSecs - requestTimestampInSecs) < 60 * 60 * 24
    if (!timestampIsWithing1d)
      throw new Exception(s"MB Timestamp is too old ($requestTimestampInSecs)")

    checkMessageBirdSignature(
      key = signingKey,
      signature = mbSignature,
      timestamp = mbTimestamp,
      queryString = request.rawQueryString,
      body = request.body
    )
  }

  private def liveSmsReceivedCallback(
      request: Request[String]
  ): Future[Either[Error, CompleteSms]] =
    Try {
      val signatureChecks = checkSignature(request)
      if (!signatureChecks) throw new Exception(s"Callback signature invalid")
      val json = Json.parse(request.body)
      val id = (JsPath \ "id")
        .read[String]
        .reads(json)
        .getOrElse(throw new Exception(s"Cannot read 'id' field of webhook request"))
      ApiSms.Id(id)
    }.fold(
      e =>
        Future(
          Left(
            Error.MiscException(
              EventType.SmsCallbackError,
              s"Impossible de lire le callback",
              e
            )
          )
        ),
      liveFetchSmsById
    )

  /** API Doc:
    * https://developers.messagebird.com/api/sms-messaging/#view-an-sms
    */
  private def liveFetchSmsById(id: ApiSms.Id): Future[Either[Error, CompleteSms]] =
    ws.url(s"https://rest.messagebird.com/messages/${id.underlying}")
      .addHttpHeaders("Accept" -> "application/json", "Authorization" -> s"AccessKey $apiKey")
      .withRequestTimeout(requestTimeout)
      .get()
      .map { response =>
        if (response.status == 200) {
          val json = response.body[JsValue]
          Right(CompleteSms(json.as[ApiSms], json))
        } else {
          throw new Exception(s"Unknown response from MB server (status ${response.status})")
        }
      }
      .recover {
        case (e: Throwable) =>
          Left(
            Error.MiscException(
              EventType.SmsReadError,
              s"Impossible de lire le SMS ${id.underlying} chez le provider distant",
              e
            )
          )
      }

  /** API Doc:
    * https://developers.messagebird.com/api/sms-messaging/#available-http-methods
    */
  private def liveDeleteSms(id: ApiSms.Id): Future[Either[Error, Unit]] =
    ws.url(s"https://rest.messagebird.com/messages/${id.underlying}")
      .addHttpHeaders("Accept" -> "application/json", "Authorization" -> s"AccessKey $apiKey")
      .withRequestTimeout(requestTimeout)
      .delete()
      .map { response =>
        if (response.status == 200)
          Right(())
        else
          throw new Exception(s"Unknown response from MB server (status ${response.status})")
      }
      .recover {
        case (e: Throwable) =>
          Left(
            Error.MiscException(
              EventType.SmsDeleteError,
              s"Impossible de supprimer le SMS ${id.underlying} chez le provider distant",
              e
            )
          )
      }

  //
  // Fake API for dev and test
  //

  val fakePhoneNumber = "33600000000"

  def fakeSendSms(body: String, recipients: List[String]): Future[Either[Error, CompleteSms]] = {
    val id = ApiSms.Id(scala.util.Random.nextBytes(16).toList.map("%02x".format(_)).mkString)
    val recipient = recipients.head
    val warn = "(ATTENTION ! CE MESSAGE N'A PAS ETE ENVOYE !)"
    val apiSms = ApiSms(
      id = id,
      originator = fakePhoneNumber,
      body = body + " " + warn,
      createdDatetime = Time.nowParis(),
      recipients = ApiSms.Recipients(
        items = List(
          ApiSms.RecipientItem(
            recipient = ApiSms.localPhoneFranceToInternational(recipient).toLong,
            status = "sent",
            statusDatetime = Time.nowParis(),
            messagePartCount = 1
          )
        )
      )
    )
    val completeSms = CompleteSms(apiSms, Json.toJson(apiSms))
    futures
      .delayed(10.seconds)(
        // Not using the reverse router as this would create a cyclic dependency
        ws.url(s"http://0.0.0.0:$serverPort/mandats/sms/webhook")
          .post(
            Json.toJson(
              ApiSms(
                id = id,
                originator = recipient,
                body = "OUI " + warn,
                createdDatetime = Time.nowParis(),
                recipients = ApiSms.Recipients(
                  items = List(
                    ApiSms.RecipientItem(
                      recipient = fakePhoneNumber.toLong,
                      status = "delivered",
                      statusDatetime = Time.nowParis(),
                      messagePartCount = 1
                    )
                  )
                )
              )
            )
          )
      )
      .onComplete(
        _.fold(
          e =>
            eventService.error(
              User.systemUser,
              "",
              EventType.SmsCallbackError.code,
              s"Impossible d'envoyer un message de test (faux webhook). SMS id: ${id.underlying}",
              None,
              None,
              Some(e)
            ),
          _ =>
            eventService.warn(
              User.systemUser,
              "",
              EventType.SmsCallbackError.code,
              s"Un message de test (faux webhook) a été envoyé. SMS id: ${id.underlying}",
              None,
              None,
              None
            )
        )
      )
    Future(Right(completeSms))
  }

  def fakeSmsReceivedCallback(request: Request[String]): Future[Either[Error, CompleteSms]] = {
    val json = Json.parse(request.body)
    Future(Right(CompleteSms(json.as[ApiSms], json)))
  }

}
