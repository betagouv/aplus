package serializers

import java.time.ZonedDateTime
import play.api.libs.json._
import scala.util.Try

object ApiModel {

  // API model for the admin deploiement
  case class FranceServiceInstanceLine(
      nomFranceService: String,
      commune: String,
      departementName: String,
      departementCode: String,
      matchedGroup: Option[String],
      groupSize: Int,
      departementIsDone: Boolean,
      contactMail: Option[String],
      phone: Option[String]
  )

  implicit val franceServiceInstanceLineFormat = Json.format[FranceServiceInstanceLine]

  /** https://developers.messagebird.com/api/sms-messaging/#send-outbound-sms */
  case class SmsSendRequest(originator: String, body: String, recipients: List[String])

  object SmsSendRequest {
    implicit val writes = Json.writes[SmsSendRequest]
  }

  /** https://developers.messagebird.com/api/sms-messaging/#the-message-object */
  case class ApiSms(
      id: ApiSms.Id,
      originator: String,
      body: String,
      createdDatetime: ZonedDateTime,
      recipients: ApiSms.Recipients
  )

  object ApiSms {

    case class Id(underlying: String)

    object Id {
      implicit val reads = implicitly[Reads[String]].map(Id.apply)
      implicit val writes = implicitly[Writes[String]].contramap((id: ApiSms.Id) => id.underlying)
    }

    case class RecipientItem(
        recipient: Long,
        status: String,
        statusDatetime: ZonedDateTime,
        messagePartCount: Int
    )
    case class Recipients(items: List[RecipientItem])

    implicit val itemFormats = Json.format[RecipientItem]
    implicit val recipientFormats = Json.format[Recipients]
    implicit val reads = Json.reads[ApiSms]
    implicit val writes = Json.writes[ApiSms]

    /** Example: (31612345678: Long) => ("0612345678": String) */
    def internationalToLocalPhone(international: Long): String =
      "0" + (international % 1000000000L)

    /** Example: ("31612345678": String) => ("0612345678": String) */
    def internationalToLocalPhone(international: String): Option[String] =
      Try(international.toLong).toOption.map(internationalToLocalPhone)

    /** Example: ("0612345678": String) => ("31612345678": String)  */
    def localPhoneFranceToInternational(local: String): String =
      "33" + local.drop(1)

  }

  /** Keep the JsValue for tracability. */
  case class CompleteSms(sms: ApiSms, json: JsValue)
}
