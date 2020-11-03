package models.sql

import java.time.ZonedDateTime
import java.util.UUID

import anorm.{Macro, RowParser}
import cats.implicits.catsSyntaxOption
import helper.Time
import models.User.AccountType.{Nominative, Shared}
import models.{Organisation, User}

final case class UserRow(
    id: UUID,
    key: String,
    firstName: Option[String],
    lastName: Option[String],
    name: String,
    qualite: String,
    email: String,
    helper: Boolean,
    instructor: Boolean,
    admin: Boolean,
    areas: List[UUID],
    creationDate: ZonedDateTime,
    communeCode: String,
    groupAdmin: Boolean,
    disabled: Boolean,
    expert: Boolean = false,
    groupIds: List[UUID] = List.empty[UUID],
    cguAcceptationDate: Option[ZonedDateTime] = Option.empty[ZonedDateTime],
    newsletterAcceptationDate: Option[ZonedDateTime] = Option.empty[ZonedDateTime],
    phoneNumber: Option[String] = Option.empty[String],
    observableOrganisationIds: List[Organisation.Id] = List.empty[Organisation.Id],
    sharedAccount: Boolean = false
)

object UserRow {

  def toUser(row: UserRow): User = {
    import row._
    val account =
      if (row.sharedAccount) Nominative(firstName.orEmpty, lastName.orEmpty, qualite)
      else Shared(name)

    User(
      id = id,
      key = key,
      account,
      email = email,
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
      phoneNumber = phoneNumber,
      observableOrganisationIds = observableOrganisationIds
    )
  }

  implicit val UserParser: RowParser[User] = Macro
    .parser[UserRow](
      "id",
      "key",
      "first_name",
      "last_name",
      "name",
      "qualite",
      "email",
      "helper",
      "instructor",
      "admin",
      "areas",
      "creation_date",
      "commune_code",
      "group_admin",
      "disabled",
      "expert",
      "group_ids",
      "cgu_acceptation_date",
      "newsletter_acceptation_date",
      "phone_number",
      "observable_organisation_ids",
      "shared_account"
    )
    .map(a => a.copy(creationDate = a.creationDate.withZoneSameInstant(Time.timeZoneParis)))
    .map(toUser)

}
