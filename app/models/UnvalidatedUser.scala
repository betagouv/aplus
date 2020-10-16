package models

import java.time.ZonedDateTime
import java.util.UUID

import cats.implicits.{
  catsKernelStdMonoidForString,
  catsStdInstancesForOption,
  catsSyntaxOption,
  catsSyntaxTuple2Semigroupal
}

final case class UnvalidatedUser(
    id: UUID,
    email: String,
    firstName: Option[String],
    lastName: Option[String],
    phoneNumber: Option[String],
    sharedAccountName: Option[String],
    instructor: Boolean,
    // If true, this person is managing the groups it is in
    // can see all users in its groups, and add new users in its groups
    // cannot modify users, only admin can.
    groupManager: Boolean,
    // TODO: remove usage of areas to more specific AdministratedAreaIds
    // Note: `areas` is used to give "full access" to someone to multiple areas
    areaIds: List[UUID],
    creationDate: ZonedDateTime,
    groupIds: List[UUID]
) {

  val name = sharedAccountName.getOrElse((firstName, lastName).mapN(_ ++ " " ++ _).orEmpty)

}

object UnvalidatedUser {

  def toUser(user: UnvalidatedUser) = User(
    user.id,
    "",
    user.name,
    "",
    user.email,
    helper = true,
    instructor = user.instructor,
    admin = false,
    user.areaIds,
    user.creationDate,
    "",
    groupAdmin = user.groupManager,
    disabled = false,
    expert = false,
    user.groupIds,
    cguAcceptationDate = Option.empty[ZonedDateTime],
    newsletterAcceptationDate = Option.empty[ZonedDateTime],
    phoneNumber = user.phoneNumber,
    List.empty[Organisation.Id],
    user.sharedAccountName.isDefined
  )

}
