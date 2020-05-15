package models.mandat

import java.util.UUID
import java.time.ZonedDateTime
import play.api.libs.json.JsValue
import services.ApiSms

object Mandat {
  case class Id(underlying: UUID)
}

case class Mandat(
    id: Mandat.Id,
    userId: UUID,
    initiationDate: ZonedDateTime,
    applicationId: Option[UUID],
    enduserPrenom: Option[String],
    enduserNom: Option[String],
    enduserBirthDate: Option[String],
    // FR local phone number, example "0612345678"
    enduserPhoneLocal: Option[String],
    // Messages as given by the remote API, should be a JSON array
    // Note: phone numbers are integers in international form (without `+`)
    smsThread: JsValue,
    smsThreadClosed: Boolean
) {

  import play.api.libs.json.{JsArray, Json}
  import scala.util.Try

  // Note: maybe return a Either?
  lazy val typedSmsThread: List[ApiSms] =
    Json.fromJson[List[ApiSms]](smsThread).asOpt.toList.flatten

  lazy val anonymous: Mandat =
    copy(
      enduserPrenom = enduserPrenom.map(_ => "** Prénom anonyme **"),
      enduserNom = enduserNom.map(_ => "** Nom anonyme **"),
      enduserBirthDate = enduserBirthDate.map(_ => "** Date de naissance anonyme **"),
      enduserPhoneLocal = enduserPhoneLocal.map(phone => phone.map(_ => '*')),
      smsThread = JsArray.empty
    )

}

case class SmsMandatInitiation(
    enduserPrenom: String,
    enduserNom: String,
    enduserBirthDate: String,
    enduserPhoneLocal: String
)
