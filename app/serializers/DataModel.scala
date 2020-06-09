package serializers

import models.Application.MandatType
import play.api.libs.json._

object DataModel {

  object Application {

    object MandatType {
      import models.Application.MandatType._

      def dataModelSerialization(entity: MandatType): String =
        entity match {
          case Sms   => "sms"
          case Phone => "phone"
          case Paper => "paper"
        }

      def dataModelDeserialization(raw: String): Option[MandatType] =
        raw match {
          case "sms"   => Some(Sms)
          case "phone" => Some(Phone)
          case "paper" => Some(Paper)
          case _       => None
        }
    }
  }

  object SmsFormats {
    import models.Sms
    implicit val smsIdReads = implicitly[Reads[String]].map(Sms.ApiId.apply)

    implicit val smsIdWrites =
      implicitly[Writes[String]].contramap((id: Sms.ApiId) => id.underlying)
    implicit val smsPhoneNumberReads = implicitly[Reads[String]].map(Sms.PhoneNumber.apply)

    implicit val smsPhoneNumberWrites =
      implicitly[Writes[String]].contramap((id: Sms.PhoneNumber) => id.internationalPhoneNumber)

    // Not implicits, so they are not picked as serializers/deserializers of `Sms`
    private val smsOutgoingFormat = Json.format[Sms.Outgoing]
    private val smsIncomingFormat = Json.format[Sms.Incoming]

    implicit val smsApiReads: Reads[Sms] =
      (JsPath \ "tag").read[String].flatMap {
        case "outgoing" => smsOutgoingFormat.map(sms => (sms: Sms))
        case "incoming" => smsIncomingFormat.map(sms => (sms: Sms))
        case tag        => Reads.failed(s"Type de SMS inconnu: $tag")
      }

    implicit val smsApiWrites: Writes[Sms] =
      Writes(
        _ match {
          case sms: Sms.Outgoing =>
            smsOutgoingFormat.writes(sms) match {
              case obj: JsObject => obj + ("tag" -> JsString("outgoing"))
              case other         => other
            }
          case sms: Sms.Incoming =>
            smsIncomingFormat.writes(sms) match {
              case obj: JsObject => obj + ("tag" -> JsString("incoming"))
              case other         => other
            }
        }
      )

  }

}
