package models

import helper.StringHelper.withQuotes
import helper.Time
import java.time.Instant
import java.util.UUID

case class AccountCreationRequest(
    id: UUID,
    requestDate: Instant,
    email: String,
    isNamedAccount: Boolean,
    firstName: String,
    lastName: String,
    phoneNumber: Option[String],
    areaIds: List[UUID],
    qualite: Option[String],
    organisationId: Option[Organisation.Id],
    miscOrganisation: Option[String],
    // TODO: rename
    isManager: Boolean,
    isInstructor: Boolean,
    message: Option[String],
    fillingIpAddress: String,
    rejectionUserId: Option[UUID],
    rejectionDate: Option[Instant],
    rejectionReason: Option[String]
) {

  lazy val requestDateLog: String = Time.formatForAdmins(requestDate)
  lazy val emailLog: String = withQuotes(email)
  lazy val phoneNumberLog: String = phoneNumber.getOrElse("<vide>")
  lazy val rejectionDateLog: String = rejectionDate.map(Time.formatForAdmins).getOrElse("<vide>")
  lazy val rejectionReasonLog: String = rejectionReason.getOrElse("<vide>")

  lazy val toLogString: String =
    "[" + List[(String, String)](
      ("Id", id.toString),
      ("Date", requestDateLog),
      ("Email", emailLog),
      ("Compte nommé", isNamedAccount.toString),
      ("Prénom", firstName),
      ("Nom", lastName),
      ("Téléphone", phoneNumberLog),
      ("Territoires", areaIds.mkString(", ")),
      ("Qualité", qualite.getOrElse("")),
      ("Organisme", organisationId.map(_.id).getOrElse("<vide>")),
      ("Autre organisme", miscOrganisation.getOrElse("")),
      ("Responsable", isManager.toString),
      ("Instructeur", isInstructor.toString),
      ("Message", message.getOrElse("")),
      ("Utilisateur ayant rejeté", id.toString),
      ("Date de rejet", rejectionDateLog),
      ("Raison du rejet", rejectionReasonLog)
    ).map { case (fieldName, value) => s"$fieldName : $value" }.mkString(" | ") + "]"

}

case class AccountCreationSignature(
    id: UUID,
    formId: UUID,
    firstName: String,
    lastName: String,
    phoneNumber: Option[String]
) {

  lazy val phoneNumberLog: String = phoneNumber.getOrElse("<vide>")

  lazy val toLogString: String =
    "[" + List[(String, String)](
      ("Id", id.toString),
      ("Id du formulaire", formId.toString),
      ("Prénom", firstName),
      ("Nom", lastName),
      ("Téléphone", phoneNumberLog)
    ).map { case (fieldName, value) => s"$fieldName : $value" }.mkString(" | ") + "]"

}

case class AccountCreation(
    form: AccountCreationRequest,
    signatures: List[AccountCreationSignature]
) {

  lazy val toLogString: String =
    "[" + List[(String, String)](
      ("Form", form.toLogString),
      ("Signatures", signatures.map(_.toLogString).mkString(", "))
    ).map { case (fieldName, value) => s"$fieldName : $value" }.mkString(" | ") + "]"

}

object AccountCreationStats {

  case class PeriodStats(
      minCount: Int,
      maxCount: Int,
      median: Double,
      quartile1: Double,
      quartile3: Double,
      percentile99: Double,
      mean: Double,
      stddev: Double,
  )

}

case class AccountCreationStats(
    todayCount: Int,
    yearStats: AccountCreationStats.PeriodStats,
    allStats: AccountCreationStats.PeriodStats,
)
