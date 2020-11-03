package models.form

import java.time.ZonedDateTime
import java.util.UUID

import models.User.AccountType.{Nominative, Shared}
import models.{Organisation, User}
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import serializers.Keys

final case class ImportUserForm(
    id: UUID,
    name: String,
    email: String,
    areas: List[UUID],
    creationDate: ZonedDateTime,
    groupAdmin: Boolean,
    groupIds: List[UUID] = List.empty[UUID],
    observableOrganisationIds: List[Organisation.Id] = List.empty[Organisation.Id],
    sharedAccount: Boolean = false
)

object ImportUserForm {

  private def toUser(importUserForm: ImportUserForm): User = {
    import importUserForm._
    val accountType = if (sharedAccount) Shared(name) else Nominative.newAccount
    User(
      id = id,
      key = "key",
      accountType = accountType,
      email = email,
      helper = true,
      instructor = false,
      admin = false,
      areas = areas,
      creationDate = creationDate,
      communeCode = "",
      groupAdmin = groupAdmin,
      disabled = false,
      groupIds = groupIds,
      cguAcceptationDate = Option.empty[ZonedDateTime],
      newsletterAcceptationDate = Option.empty[ZonedDateTime],
      phoneNumber = Option.empty[String],
      observableOrganisationIds = observableOrganisationIds
    )
  }

  private def toImportUserForm(user: User): ImportUserForm = {
    import user._
    ImportUserForm(
      id = id,
      name = name,
      email = email,
      areas = areas,
      creationDate = creationDate,
      groupAdmin = groupAdmin,
      groupIds = groupIds,
      observableOrganisationIds = observableOrganisationIds,
      sharedAccount = sharedAccount
    )
  }

  def userImportMapping(date: ZonedDateTime): Mapping[User] =
    mapping(
      "id" -> optional(uuid).transform[UUID](
        {
          case None     => UUID.randomUUID()
          case Some(id) => id
        },
        Option.apply
      ),
      "name" -> text.verifying(maxLength(500)),
      "email" -> email.verifying(maxLength(200), nonEmpty),
      "areas" -> list(uuid),
      "creationDate" -> ignored(date),
      "adminGroup" -> boolean,
      "groupIds" -> default(list(uuid), List.empty[UUID]),
      "observableOrganisationIds" -> list(of[Organisation.Id]),
      Keys.User.sharedAccount -> boolean
    )(ImportUserForm.apply)(ImportUserForm.unapply).transform[User](toUser, toImportUserForm)

}
