package models.mandat

import java.util.UUID
import java.time.ZonedDateTime
import models.Sms
import play.api.libs.json.{JsArray, JsValue, Json}

object Mandat {
  case class Id(underlying: UUID)
}

case class Mandat(
    id: Mandat.Id,
    userId: UUID,
    creationDate: ZonedDateTime,
    applicationId: Option[UUID],
    usagerPrenom: Option[String],
    usagerNom: Option[String],
    usagerBirthDate: Option[String],
    // FR local phone number, example "0612345678"
    usagerPhoneLocal: Option[String],
    smsThread: List[Sms],
    smsThreadClosed: Boolean,
    personalDataWiped: Boolean
) {

  lazy val anonymous: Mandat =
    copy(
      usagerPrenom = usagerPrenom.map(_ => "** Prénom anonyme **"),
      usagerNom = usagerNom.map(_ => "** Nom anonyme **"),
      usagerBirthDate = usagerBirthDate.map(_ => "** Date de naissance anonyme **"),
      usagerPhoneLocal = usagerPhoneLocal.map(phone => phone.map(_ => '*')),
      smsThread = Nil
    )

}
