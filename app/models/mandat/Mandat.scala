package models.mandat

import cats.syntax.all._
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

  def withWipedPersonalData: Mandat = {
    val wipedSms: List[Sms] = smsThread.map {
      case sms: Sms.Outgoing =>
        sms.copy(
          recipient = Sms.PhoneNumber("+330" + "0" * 8),
          body = ""
        )
      case sms: Sms.Incoming =>
        sms.copy(
          originator = Sms.PhoneNumber("+330" + "0" * 8),
          body = ""
        )
    }
    Mandat(
      id = id,
      userId = userId,
      creationDate = creationDate,
      applicationId = applicationId,
      usagerPrenom = "".some,
      usagerNom = "".some,
      usagerBirthDate = "".some,
      usagerPhoneLocal = "".some,
      smsThread = wipedSms,
      smsThreadClosed = smsThreadClosed,
      personalDataWiped = personalDataWiped,
    )
  }

}
