package models.mandat

import java.util.UUID
import java.time.ZonedDateTime
import play.api.libs.json.{JsArray, JsValue, Json}
import serializers.ApiModel.ApiSms

object Mandat {
  case class Id(underlying: UUID)
}

case class Mandat(
    id: Mandat.Id,
    userId: UUID,
    initiationDate: ZonedDateTime,
    applicationId: Option[UUID],
    usagerPrenom: Option[String],
    usagerNom: Option[String],
    usagerBirthDate: Option[String],
    // FR local phone number, example "0612345678"
    usagerPhoneLocal: Option[String],
    // Messages as given by the remote API, should be a JSON array
    // Note: phone numbers are integers in international form (without `+`)
    smsThread: JsValue,
    smsThreadClosed: Boolean
) {

  // Note: maybe return a Either?
  lazy val typedSmsThread: List[ApiSms] =
    Json.fromJson[List[ApiSms]](smsThread).asOpt.toList.flatten

  lazy val anonymous: Mandat =
    copy(
      usagerPrenom = usagerPrenom.map(_ => "** Prénom anonyme **"),
      usagerNom = usagerNom.map(_ => "** Nom anonyme **"),
      usagerBirthDate = usagerBirthDate.map(_ => "** Date de naissance anonyme **"),
      usagerPhoneLocal = usagerPhoneLocal.map(phone => phone.map(_ => '*')),
      smsThread = JsArray.empty
    )

}
