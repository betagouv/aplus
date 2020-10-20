package models

import java.util.UUID

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.maxLength

object formModels {

  case class ApplicationFormData(
      subject: String,
      description: String,
      usagerPrenom: String,
      usagerNom: String,
      usagerBirthDate: String,
      usagerOptionalInfos: Map[String, String],
      users: List[UUID],
      organismes: List[String],
      category: Option[String],
      selectedSubject: Option[String],
      signature: Option[String],
      mandatType: String,
      mandatDate: String,
      linkedMandat: Option[UUID]
  )

  case class AnswerFormData(
      message: String,
      applicationIsDeclaredIrrelevant: Boolean,
      usagerOptionalInfos: Map[String, String],
      privateToHelpers: Boolean,
      signature: Option[String]
  )

  case class InvitationFormData(
      message: String,
      invitedUsers: List[UUID],
      invitedGroups: List[UUID],
      privateToHelpers: Boolean
  )

  case class UserFormData(
      user: User,
      line: Int,
      alreadyExists: Boolean,
      alreadyExistingUser: Option[User] = None,
      isInMoreThanOneGroup: Option[Boolean] = None
  )

  case class UserGroupFormData(
      group: UserGroup,
      users: List[UserFormData],
      alreadyExistsOrAllUsersAlreadyExist: Boolean,
      doNotInsert: Boolean,
      alreadyExistingGroup: Option[UserGroup] = None
  )

  // TOOD : rename Data -> FormData
  case class CSVImportData(csvLines: String, areaIds: List[UUID], separator: Char)

  final case class ValidateSubscriptionForm(
      sharedAccount: Boolean,
      redirect: Option[String],
      validate: Boolean,
      firstName: Option[String],
      lastName: Option[String],
      qualite: Option[String],
      phoneNumber: Option[String]
  )

  object ValidateSubscriptionForm {

    def validate: Form[ValidateSubscriptionForm] = Form(
      mapping(
        "sharedAccount" -> boolean,
        "redirect" -> optional(text),
        "validate" -> boolean,
        "firstName" -> optional(nonEmptyText.verifying(maxLength(100))),
        "lastName" -> optional(nonEmptyText.verifying(maxLength(100))),
        "qualite" -> optional(nonEmptyText.verifying(maxLength(100))),
        "phoneNumber" -> optional(nonEmptyText.verifying(maxLength(40)))
      )(ValidateSubscriptionForm.apply)(ValidateSubscriptionForm.unapply)
        .verifying(
          "Le prénom est requis",
          form => if (!form.sharedAccount) form.firstName.map(_.trim).exists(_.nonEmpty) else true
        )
        .verifying(
          "Le nom est requis",
          form => if (!form.sharedAccount) form.lastName.map(_.trim).exists(_.nonEmpty) else true
        )
        .verifying(
          "La qualité est requise",
          form => if (!form.sharedAccount) form.lastName.map(_.trim).exists(_.nonEmpty) else true
        )
    )

  }

}
