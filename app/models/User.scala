package models

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import cats.implicits.{catsKernelStdMonoidForString, catsSyntaxOption}
import cats.syntax.all._
import constants.Constants
import helper.{Hash, UUIDHelper}

case class User(
    id: UUID,
    key: String,
    firstName: Option[String],
    lastName: Option[String],
    name: String,
    qualite: String,
    email: String,
    helper: Boolean,
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
    (areaId === Area.allArea.id || areas.contains(areaId)) && (admin || groupAdmin)

  // Note: we want to have in DB the actual time zone
  val timeZone: ZoneId = _root_.helper.Time.timeZoneParis

  def validateWith(
      firstName: Option[String],
      lastName: Option[String],
      qualite: Option[String],
      phoneNumber: Option[String]
  ) =
    this.copy(
      firstName = firstName,
      lastName = lastName,
      name = if (sharedAccount) name else s"${lastName.orEmpty.toUpperCase} ${firstName.orEmpty}",
      qualite = qualite.orEmpty,
      phoneNumber = phoneNumber
    )

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
    disabled = true
  )

}
