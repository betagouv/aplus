package models

import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

import cats.syntax.all._
import constants.Constants
import helper.StringHelper.commonStringInputNormalization
import java.util.UUID
import serializers.Keys

object formModels {

  val normalizedText: Mapping[String] =
    text.transform[String](commonStringInputNormalization, commonStringInputNormalization)

  val normalizedOptionalText: Mapping[Option[String]] =
    optional(text).transform[Option[String]](
      _.map(commonStringInputNormalization).filter(_.nonEmpty),
      _.map(commonStringInputNormalization).filter(_.nonEmpty)
    )

  def inOption[T](constraint: Constraint[T]): Constraint[Option[T]] =
    Constraint[Option[T]](constraint.name, constraint.args) {
      case None    => Valid
      case Some(t) => constraint(t)
    }

  final case class SignupFormData(
      firstName: Option[String],
      lastName: Option[String],
      qualite: Option[String],
      sharedAccount: Boolean,
      sharedAccountName: Option[String],
      phoneNumber: Option[String],
      areaId: UUID,
      organisationId: String,
      groupId: UUID,
      cguChecked: Boolean
  )

  object SignupFormData {

    val form: Form[SignupFormData] =
      Form(
        mapping(
          "firstName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
          "lastName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
          "qualite" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
          "sharedAccount" -> boolean,
          "sharedAccountName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
          "phoneNumber" -> normalizedOptionalText.verifying(inOption(maxLength(30))),
          Keys.Signup.areaId -> uuid,
          Keys.Signup.organisationId -> normalizedText.verifying(nonEmpty),
          Keys.Signup.groupId -> uuid,
          "cguChecked" -> boolean
        )(SignupFormData.apply)(SignupFormData.unapply)
          .verifying(
            "Le prénom est requis pour un compte nominatif",
            form =>
              if (form.sharedAccount) true
              else form.firstName.exists(_.nonEmpty)
          )
          .verifying(
            "Le nom est requis pour un compte nominatif",
            form =>
              if (form.sharedAccount) true
              else form.lastName.exists(_.nonEmpty)
          )
          .verifying(
            "Le nom est requis pour un compte partagé",
            form => if (form.sharedAccount) form.sharedAccountName.exists(_.nonEmpty) else true
          )
          .verifying(
            "Sans acceptation des CGU de votre part, nous ne pouvons pas terminer votre inscription. " +
              "Si vous avez des remarques concernant les CGU, " +
              s"vous pouvez les adresser au support ${Constants.supportEmail}.",
            form => form.cguChecked
          )
      )

  }

  final case class AddSignupsFormData(emails: String, dryRun: Boolean)

  object AddSignupsFormData {

    val form =
      Form(
        mapping(
          "emails" -> text,
          "dryRun" -> boolean,
        )(AddSignupsFormData.apply)(AddSignupsFormData.unapply)
      )

  }

  final case class AddUserToGroupFormData(email: String)

  object AddUserToGroupFormData {

    val form: Form[AddUserToGroupFormData] =
      Form(
        mapping(
          "email" -> normalizedText
        )(AddUserToGroupFormData.apply)(AddUserToGroupFormData.unapply)
      )

  }

  final case class EditProfileFormData(
      firstName: String,
      lastName: String,
      qualite: String,
      phoneNumber: Option[String]
  )

  object EditProfileFormData {

    val form: Form[EditProfileFormData] =
      Form(
        mapping(
          "firstName" -> normalizedText.verifying(maxLength(100), nonEmpty),
          "lastName" -> normalizedText.verifying(maxLength(100), nonEmpty),
          "qualite" -> normalizedText.verifying(maxLength(100), nonEmpty),
          "phone-number" -> normalizedOptionalText
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

  object AddUserFormData {

    val formMapping: Mapping[AddUserFormData] = mapping(
      "firstName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
      "lastName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
      "name" -> normalizedOptionalText
        .verifying(inOption(maxLength(100)))
        .transform[String](
          {
            case Some(value) => value
            case None        => ""
          },
          {
            case ""   => Option.empty[String]
            case name => name.some
          }
        ),
      "qualite" -> default(normalizedText.verifying(maxLength(100)), ""),
      "email" -> email.verifying(maxLength(200), nonEmpty),
      "instructor" -> boolean,
      "groupAdmin" -> boolean,
      "phoneNumber" -> normalizedOptionalText,
      Keys.User.sharedAccount -> boolean
    )(AddUserFormData.apply)(AddUserFormData.unapply)

    val addUsersForm: Form[List[AddUserFormData]] =
      Form(
        single(
          "users" -> list(formMapping)
        )
      )

  }

  case class AddUserFormData(
      firstName: Option[String],
      lastName: Option[String],
      name: String,
      qualite: String,
      email: String,
      instructor: Boolean,
      groupAdmin: Boolean,
      phoneNumber: Option[String],
      sharedAccount: Boolean
  )

  object EditUserFormData {

    def fromUser(user: User): EditUserFormData =
      EditUserFormData(
        id = user.id,
        firstName = user.firstName,
        lastName = user.lastName,
        name = user.name,
        qualite = user.qualite,
        email = user.email,
        helper = user.helper,
        instructor = user.instructor,
        areas = user.areas,
        groupAdmin = user.groupAdmin,
        disabled = user.disabled,
        groupIds = user.groupIds,
        phoneNumber = user.phoneNumber,
        observableOrganisationIds = user.observableOrganisationIds,
        sharedAccount = user.sharedAccount,
        internalSupportComment = user.internalSupportComment
      )

    val form: Form[EditUserFormData] =
      Form(
        mapping(
          "id" -> uuid,
          "firstName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
          "lastName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
          "name" -> normalizedOptionalText
            .verifying(inOption(maxLength(100)))
            .transform[String](
              {
                case Some(value) => value
                case None        => ""
              },
              {
                case ""   => Option.empty[String]
                case name => name.some
              }
            ),
          "qualite" -> normalizedText.verifying(maxLength(100)),
          "email" -> email.verifying(maxLength(200), nonEmpty),
          "helper" -> boolean,
          "instructor" -> boolean,
          "areas" -> list(uuid)
            .verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
          "groupAdmin" -> boolean,
          "disabled" -> boolean,
          "groupIds" -> default(list(uuid), Nil),
          "phoneNumber" -> normalizedOptionalText,
          "observableOrganisationIds" -> list(of[Organisation.Id]),
          Keys.User.sharedAccount -> boolean,
          "internalSupportComment" -> normalizedOptionalText
        )(EditUserFormData.apply)(EditUserFormData.unapply)
      )

  }

  case class EditUserFormData(
      id: UUID,
      firstName: Option[String],
      lastName: Option[String],
      name: String,
      qualite: String,
      email: String,
      helper: Boolean,
      instructor: Boolean,
      areas: List[UUID],
      groupAdmin: Boolean,
      disabled: Boolean,
      groupIds: List[UUID],
      phoneNumber: Option[String],
      observableOrganisationIds: List[Organisation.Id],
      sharedAccount: Boolean,
      internalSupportComment: Option[String]
  )

  case class CSVReviewUserFormData(
      id: UUID,
      firstName: Option[String],
      lastName: Option[String],
      name: String,
      email: String,
      instructor: Boolean,
      groupAdmin: Boolean,
      phoneNumber: Option[String]
  )

  case class CSVUserFormData(
      user: CSVReviewUserFormData,
      line: Int,
      alreadyExists: Boolean,
      alreadyExistingUser: Option[User] = None,
      isInMoreThanOneGroup: Option[Boolean] = None
  )

  case class CSVUserGroupFormData(
      group: UserGroup,
      users: List[CSVUserFormData],
      alreadyExistsOrAllUsersAlreadyExist: Boolean,
      doNotInsert: Boolean,
      alreadyExistingGroup: Option[UserGroup] = None
  )

  case class CSVRawLinesFormData(csvLines: String, areaIds: List[UUID], separator: Char)

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
        "firstName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
        "lastName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
        "qualite" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
        "phoneNumber" -> normalizedOptionalText.verifying(inOption(phoneNumberConstraint))
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
