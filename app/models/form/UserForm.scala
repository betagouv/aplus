package models.form

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import cats.implicits.{catsSyntaxEq, catsSyntaxOption, catsSyntaxOptionId}
import models.User.AccountType.{Nominative, Shared}
import models.sql.UserRow
import models.{Organisation, User}
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.{Form, Mapping}
import serializers.Keys

final case class UserForm(
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

object UserForm {

  private def toUser(userForm: UserForm): User = {
    import userForm._
    val accountType =
      if (sharedAccount) Shared(name)
      else Nominative(firstName = firstName.orEmpty, lastName = lastName.orEmpty, qualite = qualite)

    User(
      id = id,
      key = key,
      accountType = accountType,
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

  private def toUserForm(user: User): UserForm = {
    import user._

    UserForm(
      id = id,
      key = key,
      firstName = firstName.some,
      lastName = lastName.some,
      name = name,
      qualite = qualite,
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
      observableOrganisationIds = observableOrganisationIds,
      sharedAccount = sharedAccount
    )
  }

  private def privateUserMapping(timeZone: ZoneId, areas: Mapping[List[UUID]]): Mapping[User] =
    mapping(
      "id" -> optional(uuid).transform[UUID](
        {
          case None     => UUID.randomUUID()
          case Some(id) => id
        },
        Option.apply
      ),
      "key" -> ignored[String]("key"),
      "firstName" -> optional(text.verifying(maxLength(100))),
      "lastName" -> optional(text.verifying(maxLength(100))),
      "name" -> optional(nonEmptyText.verifying(maxLength(100))).transform[String](
        {
          case Some(value) => value
          case None        => ""
        },
        value => value.some.filter(_.nonEmpty)
      ),
      "qualite" -> text.verifying(maxLength(100)),
      "email" -> email.verifying(maxLength(200), nonEmpty),
      "helper" -> boolean,
      "instructor" -> boolean,
      "admin" -> boolean,
      "areas" -> areas,
      "creationDate" -> ignored(ZonedDateTime.now(timeZone)),
      "communeCode" -> default[String](nonEmptyText.verifying(maxLength(5)), "0"),
      "adminGroup" -> boolean,
      "disabled" -> boolean,
      "expert" -> ignored(false),
      "groupIds" -> default[List[UUID]](list(uuid), List.empty[UUID]),
      "cguAcceptationDate" -> ignored(Option.empty[ZonedDateTime]),
      "newsletterAcceptationDate" -> ignored(Option.empty[ZonedDateTime]),
      "phone-number" -> optional(text),
      "observableOrganisationIds" -> list(
        of[Organisation.Id].verifying(
          "Organisation non reconnue",
          organisationId =>
            Organisation.all
              .exists(organisation => organisation.id === organisationId)
        )
      ),
      Keys.User.sharedAccount -> boolean
    )(UserForm.apply)(UserForm.unapply).transform[User](toUser, toUserForm)

  def userMapping(timeZone: ZoneId): Mapping[User] =
    privateUserMapping(
      timeZone,
      list(uuid).verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty)
    )

  def usersForm(timeZone: ZoneId, areaIds: List[UUID]): Form[List[User]] =
    Form(single("users" -> list(privateUserMapping(timeZone, ignored(areaIds)))))

}
