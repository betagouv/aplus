package models

import java.time.ZonedDateTime
import java.util.UUID

import constants.Constants
import helper.{Hash, UUIDHelper}

case class User(
    id: UUID,
    key: String,
    name: String,
    qualite: String,
    email: String,
    helper: Boolean,
    instructor: Boolean,
    // TODO: `private[models]` so we cannot check it without going through authorization
    admin: Boolean,
    @deprecated(
      message = "User#areas is deprecated in favor of UserGroup#areaIds.",
      since = "v0.4.3"
    ) areas: List[UUID],
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
    phoneNumber: Option[String] = None,
    // If this field is non empty, then the User
    // is considered to be an observer:
    // * can see stats+deployment of all areas,
    // * can see all users,
    // * can see one user but not edit it
    observableOrganisationIds: List[Organisation.Id] = Nil,
    sharedAccount: Boolean = false
) extends AgeModel {
  def nameWithQualite = s"$name ( $qualite )"

  // TODO: put this in Authorization
  def canSeeUsersInArea(areaId: UUID): Boolean =
    (areaId == Area.allArea.id || areas.contains(areaId)) && (admin || groupAdmin)
}

object User {
  private val date = ZonedDateTime.parse("2017-11-01T00:00+01:00")

  val systemUser = User(
    UUIDHelper.namedFrom("system"),
    Hash.sha256(s"system"),
    "Système A+",
    "System A+",
    Constants.supportEmail,
    false,
    false,
    false,
    List(),
    date,
    "75056",
    false,
    disabled = true
  )

  val admins = List(
    // Enabled
    User(
      UUIDHelper.namedFrom("zohra"),
      Hash.sha256(s"zohra"),
      "LEBEL-SEDKI Zohra",
      "Experte A+",
      "zohra.lebel@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      expert = true,
      cguAcceptationDate = Some(date)
    ),
    User(
      UUIDHelper.namedFrom("julien"),
      Hash.sha256(s"julien"),
      "Julien DAUPHANT",
      "Admin A+",
      "julien.dauphant@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      cguAcceptationDate = Some(date)
    ),
    User(
      UUIDHelper.namedFrom("thibault"),
      Hash.sha256(s"thibault"),
      "Thibault DESJARDINS",
      "Expert A+",
      "thibault.desjardins@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      expert = false,
      cguAcceptationDate = Some(date)
    ),
    User(
      UUIDHelper.namedFrom("laurent"),
      Hash.sha256(s"laurent"),
      "Laurent COURTOIS-COURRET",
      "Expert A+",
      "laurent.courtois-courret@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      expert = false,
      cguAcceptationDate = Some(date)
    ),
    User(
      id = UUIDHelper.namedFrom("dunia"),
      key = Hash.sha256("dunia"),
      name = "Dunia El Achcar",
      qualite = "Experte A+",
      email = "dunia.el_achcar@beta.gouv.fr",
      helper = true,
      instructor = false,
      admin = true,
      areas = Area.all.map(_.id),
      creationDate = date,
      communeCode = "75056",
      groupAdmin = true,
      disabled = false,
      cguAcceptationDate = Some(date)
    )
  )
}
