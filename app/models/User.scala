package models

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import cats.implicits.{catsKernelStdMonoidForString, catsSyntaxOption}
import cats.syntax.all._
import constants.Constants
import helper.{Hash, UUIDHelper}
import models.User.AccountType
import models.User.AccountType.{Nominative, Shared}

case class User(
    id: UUID,
    key: String,
    accountType: AccountType,
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
    groupIds: List[UUID] = List.empty[UUID],
    cguAcceptationDate: Option[ZonedDateTime] = Option.empty[ZonedDateTime],
    newsletterAcceptationDate: Option[ZonedDateTime] = Option.empty[ZonedDateTime],
    phoneNumber: Option[String] = Option.empty[String],
    // If this field is non empty, then the User
    // is considered to be an observer:
    // * can see stats+deployment of all areas,
    // * can see all users,
    // * can see one user but not edit it
    observableOrganisationIds: List[Organisation.Id] = List.empty[Organisation.Id]
) extends AgeModel {

  val sharedAccount = accountType match {
    case Nominative(_, _, _) => false
    case Shared(_)           => true
  }

  val firstName = accountType match {
    case Nominative(firstName, _, _) => firstName
    case Shared(_)                   => ""
  }

  val lastName = accountType match {
    case Nominative(_, lastName, _) => lastName
    case Shared(_)                  => ""
  }

  val name = accountType match {
    case AccountType.Nominative(firstName, lastName, _) => s"${lastName.toUpperCase()} $firstName"
    case Shared(name)                                   => name
  }

  val qualite = accountType match {
    case Nominative(_, _, qualite) => qualite
    case Shared(_)                 => ""
  }

  def nameWithQualite = accountType match {
    case AccountType.Nominative(_, _, qualite) => s"$name ( $qualite )"
    case Shared(name)                          => s"$name"
  }

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
      accountType = Nominative(firstName.orEmpty, lastName.orEmpty, qualite.orEmpty),
      phoneNumber = phoneNumber
    )

}

object User {

  sealed trait AccountType

  object AccountType {

    final case class Nominative(firstName: String, lastName: String, qualite: String)
        extends AccountType

    object Nominative {
      val newAccount = Nominative("", "", "")
    }

    final case class Shared(name: String) extends AccountType

  }

  val systemUser = User(
    UUIDHelper.namedFrom("system"),
    Hash.sha256(s"system"),
    Nominative("Système A+", "Système A+", "Système A+"),
    Constants.supportEmail,
    helper = false,
    instructor = false,
    admin = false,
    List.empty[UUID],
    ZonedDateTime.parse("2017-11-01T00:00+01:00"),
    "75056",
    groupAdmin = false,
    disabled = true
  )

}
