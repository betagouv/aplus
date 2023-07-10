package helper

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.{Instant, ZonedDateTime}
import java.util.Base64

import cats.syntax.all._
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import models.{Error, EventType}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.Request

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object MessageBirdApi {

  /** https://developers.messagebird.com/api/sms-messaging/#send-outbound-sms */
  case class SendSmsRequest(originator: String, body: String, recipients: List[String])

  object SendSmsRequest {
    implicit val writes: Writes[SendSmsRequest] = Json.writes[SendSmsRequest]
  }

  /** https://developers.messagebird.com/api/sms-messaging/#the-message-object */
  case class Sms(
      id: Sms.Id,
      originator: String,
      body: String,
      createdDatetime: ZonedDateTime,
      recipients: Sms.Recipients
  )

  object Sms {

    case class Id(underlying: String)

    object Id {
      implicit val reads: Reads[Id] = implicitly[Reads[String]].map(Id.apply)

      implicit val writes: Writes[Id] =
        implicitly[Writes[String]].contramap((id: Sms.Id) => id.underlying)

    }

    case class RecipientItem(
        recipient: Long,
        status: String,
        statusDatetime: ZonedDateTime,
        messagePartCount: Int
    )

    case class Recipients(items: List[RecipientItem])

    implicit val itemFormats: Format[RecipientItem] = Json.format[RecipientItem]
    implicit val recipientFormats: Format[Recipients] = Json.format[Recipients]
    implicit val reads: Reads[Sms] = Json.reads[Sms]
    implicit val writes: Writes[Sms] = Json.writes[Sms]

  }

}

/** Various infos:
  *   - https://support.messagebird.com/hc/en-us/articles/208015009-France
  *   - "Each Virtual Number has a daily limit of approximately 500 SMS per number, per day."
  */
final class MessageBirdApi(
    bodyParsers: play.api.mvc.PlayBodyParsers,
    ws: WSClient,
    apiKey: String,
    signingKey: String,
    aplusPhoneNumber: String,
    requestTimeout: FiniteDuration,
    implicit val executionContext: ExecutionContext
) {
  import MessageBirdApi._

  /** API Doc: https://developers.messagebird.com/api/sms-messaging/#send-outbound-sms
    */
  def sendSms(
      body: String,
      recipients: List[String]
  ): Future[Either[Error, Sms]] = {
    val request = SendSmsRequest(
      originator = aplusPhoneNumber,
      body = body,
      recipients = recipients
    )
    ws.url("https://rest.messagebird.com/messages")
      .addHttpHeaders("Accept" -> "application/json", "Authorization" -> s"AccessKey $apiKey")
      .withRequestTimeout(requestTimeout)
      .post(Json.toJson(request))
      .map { response =>
        if (response.status === 201) {
          val json = response.body[JsValue]
          Right(json.as[Sms])
        } else {
          throw new Exception(
            s"Unknown response from MB server (status ${response.status})" +
              s": $response - ${response.body}"
          )
        }
      }
      .recover { case e: Throwable =>
        Left(
          Error.MiscException(
            EventType.SmsSendError,
            s"Impossible d'envoyer un SMS à partir du numéro $aplusPhoneNumber",
            e,
            none
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
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secretKey)
    val requestSignature: List[Byte] = mac.doFinal(blobToHmac.toArray).toList
    requestSignature === expectedSignature
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

  def smsReceivedCallback(request: Request[String]): Future[Either[Error, Sms]] =
    Try {
      val signatureChecks = checkSignature(request)
      if (!signatureChecks) throw new Exception(s"Callback signature invalid")
      val json = Json.parse(request.body)
      val id = (JsPath \ "id")
        .read[String]
        .reads(json)
        .getOrElse(throw new Exception(s"Cannot read 'id' field of webhook request"))
      Sms.Id(id)
    }.fold(
      e =>
        Future(
          Left(
            Error.MiscException(
              EventType.SmsCallbackError,
              s"Impossible de lire le callback",
              e,
              none
            )
          )
        ),
      liveFetchSmsById
    )

  /** API Doc: https://developers.messagebird.com/api/sms-messaging/#view-an-sms
    */
  private def liveFetchSmsById(id: Sms.Id): Future[Either[Error, Sms]] =
    ws.url(s"https://rest.messagebird.com/messages/${id.underlying}")
      .addHttpHeaders("Accept" -> "application/json", "Authorization" -> s"AccessKey $apiKey")
      .withRequestTimeout(requestTimeout)
      .get()
      .map { response =>
        if (response.status === 200) {
          val json = response.body[JsValue]
          Right(json.as[Sms])
        } else {
          throw new Exception(s"Unknown response from MB server (status ${response.status})")
        }
      }
      .recover { case (e: Throwable) =>
        Left(
          Error.MiscException(
            EventType.SmsReadError,
            s"Impossible de lire le SMS ${id.underlying} chez le provider distant",
            e,
            none
          )
        )
      }

  /** API Doc: https://developers.messagebird.com/api/sms-messaging/#available-http-methods
    */
  def deleteSms(id: Sms.Id): Future[Either[Error, Unit]] =
    ws.url(s"https://rest.messagebird.com/messages/${id.underlying}")
      .addHttpHeaders("Accept" -> "application/json", "Authorization" -> s"AccessKey $apiKey")
      .withRequestTimeout(requestTimeout)
      .delete()
      .map { response =>
        if (response.status === 200)
          Right(())
        else
          throw new Exception(s"Unknown response from MB server (status ${response.status})")
      }
      .recover { case (e: Throwable) =>
        Left(
          Error.MiscException(
            EventType.SmsDeleteError,
            s"Impossible de supprimer le SMS ${id.underlying} chez le provider distant",
            e,
            none
          )
        )
      }

}
