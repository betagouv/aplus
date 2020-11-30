package models

import java.util.UUID

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty, pattern}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

object formModels {

  final case class AddUserToGroupFormData(email: String)

  object AddUserToGroupFormData {

    val form: Form[AddUserToGroupFormData] =
      Form(
        mapping("email" -> nonEmptyText)(AddUserToGroupFormData.apply)(
          AddUserToGroupFormData.unapply
        )
      )

  }

  final case class EditProfileFormData(
      firstName: String,
      lastName: String,
      qualite: String,
      phoneNumber: String
  )

  object EditProfileFormData {

    val form: Form[EditProfileFormData] =
      Form(
        mapping(
          "firstName" -> text.verifying(maxLength(100), nonEmpty),
          "lastName" -> text.verifying(maxLength(100), nonEmpty),
          "qualite" -> text.verifying(maxLength(100), nonEmpty),
          "phone-number" -> text.verifying(
            pattern(
              """0\d \d{2} \d{2} \d{2} \d{2}""".r,
              error = "Le format doit être XX XX XX XX XX"
            )
          )
        )(EditProfileFormData.apply)(EditProfileFormData.unapply)
      )

  }

  case class ApplicationFormData(
      subject: String,
      description: String,
      usagerPrenom: String,
      usagerNom: String,
      usagerBirthDate: String,
      usagerOptionalInfos: Map[String, String],
      users: List[UUID],
      groups: List[UUID],
      category: Option[String],
      selectedSubject: Option[String],
      signature: Option[String],
      mandatType: String,
      mandatDate: String,
      linkedMandat: Option[UUID]
  )

  case class AnswerFormData(
      answerType: String,
      message: Option[String],
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
      redirect: Option[String],
      cguChecked: Boolean,
      firstName: Option[String],
      lastName: Option[String],
      qualite: Option[String],
      phoneNumber: Option[String]
  )

  object ValidateSubscriptionForm {

    val PhoneNumber = """(\d{2}) (\d{2}) (\d{2}) (\d{2}) (\d{2})""".r

    private val validPhoneNumberPrefixes =
      List("01", "02", "03", "04", "05", "06", "07", "08", "09")

    private def isValidPhoneNumberPrefix(prefix: String) =
      validPhoneNumberPrefixes.contains(prefix)

    val phoneNumberConstraint: Constraint[String] =
      Constraint[String]("constraint.invalidFormat") {
        case PhoneNumber(prefix, _, _, _, _) if isValidPhoneNumberPrefix(prefix) => Valid
        case PhoneNumber(_, _, _, _, _) =>
          Invalid(ValidationError("Préfixe de numéro de téléphone invalide"))
        case _ => Invalid(ValidationError("Le format doit être XX XX XX XX XX"))
      }

    def validate(user: User): Form[ValidateSubscriptionForm] = Form(
      mapping(
        "redirect" -> optional(text),
        "cguChecked" -> boolean,
        "firstName" -> optional(nonEmptyText.verifying(maxLength(100))),
        "lastName" -> optional(nonEmptyText.verifying(maxLength(100))),
        "qualite" -> optional(nonEmptyText.verifying(maxLength(100))),
        "phoneNumber" -> optional(nonEmptyText.verifying(phoneNumberConstraint))
      )(ValidateSubscriptionForm.apply)(ValidateSubscriptionForm.unapply)
        .verifying(
          "Le prénom est requis",
          form => if (!user.sharedAccount) form.firstName.map(_.trim).exists(_.nonEmpty) else true
        )
        .verifying(
          "Le nom est requis",
          form => if (!user.sharedAccount) form.lastName.map(_.trim).exists(_.nonEmpty) else true
        )
        .verifying(
          "La qualité est requise",
          form => if (!user.sharedAccount) form.qualite.map(_.trim).exists(_.nonEmpty) else true
        )
    )

  }

}
