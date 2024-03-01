package models

import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

import cats.syntax.all._
import constants.Constants
import forms.FormsPlusMap
import helper.StringHelper.commonStringInputNormalization
import java.util.UUID
import serializers.Keys
import scala.util.Try

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
      creatorGroupId: Option[UUID],
      users: List[UUID],
      groups: List[UUID],
      category: Option[String],
      selectedSubject: Option[String],
      signature: Option[String],
      mandatGenerationType: String,
      mandatDate: String,
      linkedMandat: Option[UUID]
  )

  object ApplicationFormData {

    private val applicationIdKey = "application-id"

    val mandatGenerationTypeIsNew = "generateNew"

    def extractApplicationId(form: Form[ApplicationFormData]): Option[UUID] =
      form.data.get(applicationIdKey).flatMap(id => Try(UUID.fromString(id)).toOption)

    def form(currentUser: User) =
      Form(
        mapping(
          "subject" -> nonEmptyText.verifying(maxLength(150)),
          "description" -> nonEmptyText,
          "usagerPrenom" -> nonEmptyText.verifying(maxLength(30)),
          "usagerNom" -> nonEmptyText.verifying(maxLength(30)),
          "usagerBirthDate" -> nonEmptyText.verifying(maxLength(30)),
          "usagerOptionalInfos" -> FormsPlusMap.map(text.verifying(maxLength(200))),
          "creatorGroupId" -> optional(uuid),
          "users" -> list(uuid),
          "groups" -> list(uuid)
            .verifying("Vous devez sélectionner au moins une structure", _.nonEmpty),
          "category" -> optional(text),
          "selected-subject" -> optional(text),
          "signature" -> (
            if (currentUser.sharedAccount)
              nonEmptyText.transform[Option[String]](Some.apply, _.getOrElse(""))
            else ignored(Option.empty[String])
          ),
          "mandatGenerationType" -> text,
          "mandatDate" -> nonEmptyText,
          "linkedMandat" -> optional(uuid)
        )(ApplicationFormData.apply)(ApplicationFormData.unapply)
      )

  }

  case class AnswerFormData(
      answerType: String,
      message: Option[String],
      applicationIsDeclaredIrrelevant: Boolean,
      usagerOptionalInfos: Map[String, String],
      privateToHelpers: Boolean,
      applicationHasBeenProcessed: Boolean,
      signature: Option[String]
  )

  object AnswerFormData {

    private val answerIdKey = "answer-id"

    def extractAnswerId(form: Form[AnswerFormData]): Option[UUID] =
      form.data.get(answerIdKey).flatMap(id => Try(UUID.fromString(id)).toOption)

    def form(currentUser: User, containsFiles: Boolean) =
      answerForm(currentUser, containsFiles, normalizedText.verifying(maxLength(20)), boolean)

    def applicationHasBeenProcessedForm(currentUser: User, containsFiles: Boolean) =
      answerForm(
        currentUser,
        containsFiles,
        ignored(Answer.AnswerType.ApplicationProcessed.name),
        ignored(true)
      )

    private def answerForm(
        currentUser: User,
        containsFiles: Boolean,
        answerTypeMapping: Mapping[String],
        applicationHasBeenProcessedMapping: Mapping[Boolean]
    ) =
      Form(
        mapping(
          "answer_type" -> answerTypeMapping,
          "message" -> normalizedOptionalText,
          "irrelevant" -> boolean,
          "usagerOptionalInfos" -> FormsPlusMap.map(normalizedText.verifying(maxLength(200))),
          "privateToHelpers" -> boolean,
          "applicationHasBeenProcessed" -> applicationHasBeenProcessedMapping,
          "signature" -> (
            if (currentUser.sharedAccount)
              nonEmptyText.transform[Option[String]](Some.apply, _.orEmpty)
            else ignored(Option.empty[String])
          )
        )(AnswerFormData.apply)(AnswerFormData.unapply)
          .verifying(
            "La formulaire doit comporter une réponse.",
            form =>
              containsFiles ||
                form.usagerOptionalInfos.filter { case (_, value) => value.nonEmpty }.nonEmpty ||
                (form.answerType === Answer.AnswerType.WorkInProgress.name) ||
                (form.answerType === Answer.AnswerType.WrongInstructor.name) ||
                form.applicationHasBeenProcessed ||
                form.message.nonEmpty
          )
      )

  }

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
      Keys.User.sharedAccount -> boolean,
    )(AddUserFormData.apply)(AddUserFormData.unapply)

    val addUsersForm: Form[AddUsersFormData] =
      Form(
        mapping(
          "users" -> list(formMapping),
          "confirmInstructors" -> boolean,
        )(AddUsersFormData.apply)(AddUsersFormData.unapply)
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
      sharedAccount: Boolean,
  )

  case class AddUsersFormData(users: List[AddUserFormData], confirmInstructors: Boolean)

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
        managingOrganisationIds = user.managingOrganisationIds,
        managingAreaIds = user.managingAreaIds,
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
          "managingOrganisationIds" -> list(of[Organisation.Id]),
          "managingAreaIds" -> list(uuid),
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
      managingOrganisationIds: List[Organisation.Id],
      managingAreaIds: List[UUID],
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

  object ApplicationsPageInfos {
    val groupFilterKey = "filtre-groupe"
    val statusFilterKey = "filtre-status"
    val statusMine = "mes-demandes"
    val statusNew = "nouvelles"
    val statusProcessing = "en-cours"
    val statusLate = "souffrante"
    val statusArchived = "archive"

    def emptyFilters(baseUrl: String): Filters = Filters(none, none, baseUrl)

    case class Filters(selectedGroups: Option[Set[UUID]], status: Option[String], urlBase: String) {

      def groupIsFiltered(id: UUID): Boolean =
        selectedGroups.map(groups => groups.contains(id)).getOrElse(false)

      def isMine: Boolean = status === Some(statusMine)
      def isNew: Boolean = status === Some(statusNew)
      def isProcessing: Boolean = status === Some(statusProcessing)
      def isLate: Boolean = status === Some(statusLate)
      def isArchived: Boolean = status === Some(statusArchived)
      def hasNoStatus: Boolean = !isMine && !isNew && !isProcessing && !isLate && !isArchived

      def toUrl: String = {
        val groups: List[String] =
          selectedGroups.map(_.map(id => s"$groupFilterKey=$id").toList).getOrElse(Nil)
        val statusFilter: List[String] = status.toList.map(status => s"$statusFilterKey=$status")
        val filters = statusFilter ::: groups
        if (filters.isEmpty)
          urlBase
        else
          urlBase + "?" + filters.mkString("&")
      }

      def withGroup(id: UUID) = {
        val newGroups = selectedGroups match {
          case None                                => Some(Set(id))
          case Some(groups) if groups.contains(id) => Some(groups)
          case Some(groups)                        => Some(groups.incl(id))
        }
        copy(selectedGroups = newGroups)
      }

      def withoutGroup(id: UUID) = {
        val newGroups = selectedGroups match {
          case None         => None
          case Some(groups) => Some(groups.excl(id))
        }
        copy(selectedGroups = newGroups)
      }

      def withoutStatus: Filters = copy(status = None)
      def withStatusMine: Filters = copy(status = Some(statusMine))
      def withStatusNew: Filters = copy(status = Some(statusNew))
      def withStatusProcessing: Filters = copy(status = Some(statusProcessing))
      def withStatusLate: Filters = copy(status = Some(statusLate))
      def withStatusArchived: Filters = copy(status = Some(statusArchived))
    }

  }

  case class ApplicationsPageInfos(
      filters: ApplicationsPageInfos.Filters,
      groupsCounts: Map[UUID, Int],
      allGroupsOpenCount: Int,
      allGroupsClosedCount: Int,
      filteredByGroupsOpenCount: Int,
      filteredByGroupsClosedCount: Int,
      interactedCount: Int,
      newCount: Int,
      processingCount: Int,
      lateCount: Int,
  ) {

    def countsLog: String = {
      val groups: String = filters.selectedGroups.map(_.mkString).getOrElse("")
      s"ouvertes=$allGroupsOpenCount/archivées=$allGroupsClosedCount " +
        s"[groupes=$groups/mes demandes=$interactedCount/nouvelles=$newCount/" +
        s"en cours=$processingCount/retard=$lateCount]"
    }

  }

}
