package models

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID

import cats.syntax.all._
import constants.Constants
import helper.{Hash, Pseudonymizer, Time, UUIDHelper}
import helper.Time.{instantInstance, zonedDateTimeInstance}
import helper.StringHelper.{
  capitalizeName,
  commonStringInputNormalization,
  notLetterNorNumberRegex,
  withQuotes
}

case class User(
    id: UUID,
    key: String,
    firstName: Option[String],
    lastName: Option[String],
    name: String,
    qualite: String,
    email: String,
    private[models] val helper: Boolean,
    instructor: Boolean,
    // TODO: `private[models]` so we cannot check it without going through authorization
    admin: Boolean,
    // TODO: remove usage of areas to more specific AdministratedAreaIds
    // Note: `areas` is used to give "full access" to someone to multiple areas
    areas: List[UUID],
    creationDate: ZonedDateTime,
    communeCode: String,
    // If true, this person is managing the groups it is in
    // can see all users in its groups, and add new users in its groups
    // cannot modify users, only admin can.
    groupAdmin: Boolean,
    disabled: Boolean,
    expert: Boolean = false,
    groupIds: List[UUID] = Nil,
    cguAcceptationDate: Option[ZonedDateTime] = None,
    newsletterAcceptationDate: Option[ZonedDateTime] = None,
    firstLoginDate: Option[Instant],
    phoneNumber: Option[String] = None,
    // If this field is non empty, then the User
    // is considered to be an observer:
    // * can see stats+deployment of all areas,
    // * can see all users,
    // * can see one user but not edit it
    observableOrganisationIds: List[Organisation.Id],
    managingOrganisationIds: List[Organisation.Id],
    managingAreaIds: List[UUID],
    sharedAccount: Boolean = false,
    // This is a comment only visible by the admins
    internalSupportComment: Option[String],
) extends AgeModel {
  def nameWithQualite = s"$name ( $qualite )"

  // TODO: put this in Authorization
  def canSeeUsersInArea(areaId: UUID): Boolean =
    (areaId === Area.allArea.id || areas.contains(areaId)) && (admin || groupAdmin)

  // Note: we want to have in DB the actual time zone
  val timeZone: ZoneId = _root_.helper.Time.timeZoneParis

  def validateWith(
      firstName: Option[String],
      lastName: Option[String],
      qualite: Option[String],
      phoneNumber: Option[String]
  ) =
    copy(
      firstName = firstName,
      lastName = lastName,
      name = if (sharedAccount) name else s"${lastName.orEmpty.toUpperCase} ${firstName.orEmpty}",
      qualite = qualite.orEmpty,
      phoneNumber = phoneNumber
    )

  lazy val helperRoleName: Option[String] = helper.some.filter(identity).map(_ => "Aidant")

  lazy val instructorRoleName: Option[String] =
    instructor.some.filter(identity).map(_ => "Instructeur")

  lazy val groupAdminRoleName: Option[String] =
    groupAdmin.some.filter(identity).map(_ => "Responsable")

  lazy val adminRoleName: Option[String] = admin.some.filter(identity).map(_ => "Admin")
  lazy val disabledRoleName: Option[String] = disabled.some.filter(identity).map(_ => "Désactivé")

  lazy val firstNameLog: String = firstName.map(withQuotes).getOrElse("<vide>")
  lazy val lastNameLog: String = lastName.map(withQuotes).getOrElse("<vide>")
  lazy val nameLog: String = withQuotes(name)
  lazy val qualiteLog: String = withQuotes(qualite)
  lazy val emailLog: String = withQuotes(email)
  lazy val helperLog: String = helper.toString
  lazy val instructorLog: String = instructor.toString
  lazy val adminLog: String = admin.toString
  lazy val areasLog: String = areas.mkString(", ")
  lazy val groupAdminLog: String = groupAdmin.toString
  lazy val disabledLog: String = disabled.toString
  lazy val expertLog: String = expert.toString
  lazy val groupIdsLog: String = groupIds.mkString(", ")

  lazy val cguAcceptationDateLog: String =
    cguAcceptationDate.map(Time.adminsFormatter.format).getOrElse("<vide>")

  lazy val newsletterAcceptationDateLog: String =
    newsletterAcceptationDate.map(Time.adminsFormatter.format).getOrElse("<vide>")

  lazy val firstLoginDateLog: String =
    firstLoginDate.map(Time.formatForAdmins).getOrElse("<vide>")

  lazy val phoneNumberLog: String = phoneNumber.map(withQuotes).getOrElse("<vide>")
  lazy val observableOrganisationIdsLog: String = observableOrganisationIds.map(_.id).mkString(", ")
  lazy val managingOrganisationIdsLog: String = managingOrganisationIds.map(_.id).mkString(", ")
  lazy val managingAreaIdsLog: String = managingAreaIds.mkString(", ")
  lazy val sharedAccountLog: String = sharedAccount.toString

  lazy val internalSupportCommentLog: String =
    internalSupportComment.map(withQuotes).getOrElse("<vide>")

  lazy val toLogString: String =
    "[" + List[(String, String)](
      ("Id", id.toString),
      ("Prénom", firstNameLog),
      ("Nom", lastNameLog),
      ("Nom complet", nameLog),
      ("Qualité", qualiteLog),
      ("Email", emailLog),
      ("Téléphone", phoneNumberLog),
      ("Aidant", helperLog),
      ("Instructeur", instructorLog),
      ("Responsable", groupAdminLog),
      ("Compte partagé", sharedAccountLog),
      ("Admin", adminLog),
      ("Expert", expertLog),
      ("Désactivé", disabledLog),
      ("Groupes", groupIdsLog),
      ("Territoires", areasLog),
      ("Date CGU", cguAcceptationDateLog),
      ("Newsletter", newsletterAcceptationDateLog),
      ("Première connexion", firstLoginDateLog),
      ("Observation des organismes", observableOrganisationIdsLog),
      ("Responsable des organismes", managingOrganisationIdsLog),
      ("Responsable des territoires", managingAreaIdsLog),
      ("Information Support", internalSupportCommentLog),
    ).map { case (fieldName, value) => s"$fieldName : $value" }.mkString(" | ") + "]"

  def toDiffLogString(other: User): String = {
    val diffs: List[String] = List[(String, Boolean, String, String)](
      ("Id", id =!= other.id, id.toString, other.id.toString),
      ("Prénom", firstName =!= other.firstName, firstNameLog, other.firstNameLog),
      ("Nom", lastName =!= other.lastName, lastNameLog, other.lastNameLog),
      ("Nom complet", name =!= other.name, nameLog, other.nameLog),
      ("Qualité", qualite =!= other.qualite, qualiteLog, other.qualiteLog),
      ("Email", email =!= other.email, emailLog, other.emailLog),
      ("Téléphone", phoneNumber =!= other.phoneNumber, phoneNumberLog, other.phoneNumberLog),
      ("Aidant", helper =!= other.helper, helperLog, other.helperLog),
      ("Instructeur", instructor =!= other.instructor, instructorLog, other.instructorLog),
      ("Responsable", groupAdmin =!= other.groupAdmin, groupAdminLog, other.groupAdminLog),
      (
        "Compte partagé",
        sharedAccount =!= other.sharedAccount,
        sharedAccountLog,
        other.sharedAccountLog
      ),
      ("Admin", admin =!= other.admin, adminLog, other.adminLog),
      ("Expert", expert =!= other.expert, expertLog, other.expertLog),
      ("Désactivé", disabled =!= other.disabled, disabledLog, other.disabledLog),
      ("Groupes", groupIds =!= other.groupIds, groupIdsLog, other.groupIdsLog),
      ("Territoires", areas =!= other.areas, areasLog, other.areasLog),
      (
        "Date CGU",
        cguAcceptationDate =!= other.cguAcceptationDate,
        cguAcceptationDateLog,
        other.cguAcceptationDateLog
      ),
      (
        "Newsletter",
        newsletterAcceptationDate =!= other.newsletterAcceptationDate,
        newsletterAcceptationDateLog,
        other.newsletterAcceptationDateLog
      ),
      (
        "Première connexion",
        firstLoginDate =!= other.firstLoginDate,
        firstLoginDateLog,
        other.firstLoginDateLog
      ),
      (
        "Observation des organismes",
        observableOrganisationIds =!= other.observableOrganisationIds,
        observableOrganisationIdsLog,
        other.observableOrganisationIdsLog
      ),
      (
        "Responsable des organismes",
        managingOrganisationIds =!= other.managingOrganisationIds,
        managingOrganisationIdsLog,
        other.managingOrganisationIdsLog
      ),
      (
        "Responsable des territoires",
        managingAreaIds =!= other.managingAreaIds,
        managingAreaIdsLog,
        other.managingAreaIdsLog
      ),
      (
        "Information Support",
        internalSupportCommentLog =!= other.internalSupportCommentLog,
        internalSupportCommentLog,
        other.internalSupportCommentLog
      ),
    ).collect { case (name, true, thisValue, thatValue) => s"$name : $thisValue -> $thatValue" }
    "[" + diffs.mkString(" | ") + "]"
  }

  def pseudonymize: User = {
    val pseudo = new Pseudonymizer(id)
    val pseudoEmail = pseudo.emailKeepingDomain(email)
    val pseudoPhone = phoneNumber.map(_.trim).filter(_.nonEmpty).map(n => n.take(4) + "0000")
    User(
      id = id,
      key = "",
      firstName = pseudo.firstName.some,
      lastName = pseudo.lastName.some,
      name = pseudo.fullName,
      qualite = qualite,
      email = pseudoEmail,
      helper = helper,
      instructor = instructor,
      admin = admin,
      areas = areas,
      creationDate = creationDate,
      communeCode = communeCode,
      groupAdmin = groupAdmin,
      disabled = disabled,
      expert = expert,
      groupIds = groupIds,
      cguAcceptationDate = cguAcceptationDate,
      newsletterAcceptationDate = newsletterAcceptationDate,
      firstLoginDate = firstLoginDate,
      phoneNumber = pseudoPhone,
      observableOrganisationIds = observableOrganisationIds,
      managingOrganisationIds = managingOrganisationIds,
      managingAreaIds = managingAreaIds,
      sharedAccount = sharedAccount,
      internalSupportComment = none,
    )
  }

}

object User {

  val systemUser = User(
    UUIDHelper.namedFrom("system"),
    Hash.sha256(s"system"),
    Option.empty[String],
    Option.empty[String],
    "Système A+",
    "System A+",
    Constants.supportEmail,
    helper = false,
    instructor = false,
    admin = false,
    List(),
    ZonedDateTime.parse("2017-11-01T00:00+01:00"),
    "75056",
    groupAdmin = false,
    disabled = true,
    firstLoginDate = none,
    observableOrganisationIds = Nil,
    managingOrganisationIds = Nil,
    managingAreaIds = Nil,
    internalSupportComment = None
  )

  def standardName(firstName: String, lastName: String): String = {
    val normalizedFirstName = commonStringInputNormalization(firstName)
    val normalizedLastName = commonStringInputNormalization(lastName)
    s"${normalizedLastName.toUpperCase} ${capitalizeName(normalizedFirstName)}"
  }

}
