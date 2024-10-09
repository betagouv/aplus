package models

import cats.Eq
import cats.syntax.all._
import helper.StringHelper

/** Note: `EventType` is used for logging, and logging is an orthogonal concern. ie it shows up
  * everywhere in an application. So we expect `EventType` to show up at any level in the the code
  * (wherever the best information to log is known).
  */
trait EventType {
  val code: String = StringHelper.camelToUnderscoresUpperCase(this.getClass.getSimpleName)
  val level: String
}

object EventType {

  implicit val Eq: Eq[EventType] = (x: EventType, y: EventType) => x.code === y.code

  sealed trait Error extends EventType {
    val level = "ERROR"
  }

  sealed trait Info extends EventType {
    val level = "INFO"
  }

  sealed trait Warn extends EventType {
    val level = "WARN"
  }

  object AddExpertCreated extends Info
  object AddExpertNotCreated extends Error
  object AddExpertNotFound extends Error
  object AddExpertUnauthorized extends Warn
  object AddGroupUnauthorized extends Warn
  object AddUserError extends Error
  object AddUserGroupError extends Error
  object AddUserToGroupUnauthorized extends Warn
  object AdminOutOfRange extends Error
  object AgentsAdded extends Info
  object AgentsNotAdded extends Error
  object AllApplicationsShowed extends Info
  object AllApplicationsUnauthorized extends Warn
  object AllAreaUnauthorized extends Warn
  object AllAsNotFound extends Error
  object AllAsShowed extends Info
  object AllAsUnauthorized extends Warn
  object AllCSVShowed extends Info
  object AllUserIncorrectSetup extends Info
  object AllUserUnauthorized extends Warn
  object ApplicationCreated extends Info
  object ApplicationCreationError extends Error
  object ApplicationCreationUnauthorized extends Warn
  object ApplicationFormShowed extends Info
  object ApplicationLinkedToMandat extends Info
  object ApplicationLinkedToMandatError extends Error
  object ApplicationNotFound extends Error
  object ApplicationShowed extends Info
  object ApplicationUnauthorized extends Warn
  object AreaChanged extends Info
  object CGUShowed extends Info
  object CGUValidated extends Info
  object CGUValidationError extends Error
  object CGUInvalidRedirect extends Warn
  object ChangeAreaUnauthorized extends Warn
  object DeleteUserUnauthorized extends Warn
  object DeploymentDashboardUnauthorized extends Warn
  object EditGroupShowed extends Info
  object EditGroupUnauthorized extends Warn
  object EditUserError extends Error
  object EditUserGroupError extends Error
  object EditUserShowed extends Info
  object EditUserUnauthorized extends Warn
  object EditUserGroupBadRequest extends Warn
  object EventsShowed extends Info
  object EventsUnauthorized extends Warn
  object EventsError extends Error
  object GroupDeletionUnauthorized extends Warn
  object ImportGroupUnauthorized extends Warn
  object ImportUserError extends Error
  object ImportUserUnauthorized extends Warn
  object ImportUsersUnauthorized extends Warn
  object InviteFormValidationError extends Warn
  object MandatInitiationBySmsFormValidationError extends Warn
  object MandatInitiationBySmsWarn extends Warn
  object MandatInitiationBySmsDone extends Info
  object MandatBySmsResponseSaved extends Info
  object MandatGenerationFormValidationError extends Warn
  object MandatGenerated extends Info
  object MandatShowed extends Info
  object MandatError extends Error
  object MandatNotFound extends Error
  object MandatUnauthorized extends Warn
  object MyApplicationsShowed extends Info
  object MyCSVShowed extends Info
  object NewsletterSubscribed extends Info
  object NewsletterSubscriptionError extends Error
  object PostAddUserUnauthorized extends Warn
  object PostEditUserUnauthorized extends Warn
  object ShowAddUserUnauthorized extends Warn
  object SmsReadError extends Error
  object SmsSendError extends Error
  object SmsDeleteError extends Error
  object SmsCallbackError extends Error
  object StatsShowed extends Info
  object StatsUnauthorized extends Warn
  object ReopenCompleted extends Info
  object TerminateCompleted extends Info
  object TerminateError extends Error
  object ReopenError extends Error
  object TerminateIncompleted extends Error
  object TerminateNotFound extends Error
  object ReopenUnauthorized extends Warn
  object TerminateUnauthorized extends Warn
  object ToCGURedirected extends Info
  object UserCreated extends Info
  object UserDeleted extends Info
  object UserEdited extends Info
  object UserGroupCreated extends Info
  object UserGroupDeleted extends Info
  object UserGroupDeletionUnauthorized extends Error
  object UserGroupEdited extends Info
  object UserGroupNotFound extends Error
  object AddUserToGroupBadUserInput extends Warn
  object UserIsUsed extends Error
  object UserNotFound extends Warn
  object UserShowed extends Info
  object UsersCreated extends Info
  object UsersImported extends Info
  object UsersShowed extends Info
  object ViewUserUnauthorized extends Warn
  object WeeklyEmailsSent extends Info

  // Applications
  object AnswerCreated extends Info
  object AnswerFormError extends Warn
  object AnswerCreationError extends Error

  object ApplicationCreationInvalid extends Info
  object CSVImportFormError extends Warn
  object CsvImportInputEmpty extends Warn
  object ImportUserFormError extends Warn
  object StatsIncorrectSetup extends Warn
  object TryLoginByKey extends Info
  object UnknownEmail extends Warn
  object LoginInvalidPath extends Warn

  object UserProfileShowed extends Info
  object UserProfileShowedError extends Error
  object UserProfileUpdated extends Info
  object UserProfileUpdatedError extends Error

  object EditMyGroupShowed extends Info

  object WipeDataComplete extends Info
  object WipeDataError extends Error

  object AnonymizedDataExportMessage extends Info
  object AnonymizedDataExportError extends Error

  object ViewsRefreshMessage extends Info
  object ViewsRefreshError extends Error

  object MasqueradeUnauthorized extends Warn

  object SearchUsersDone extends Info
  object SearchUsersNotAuthorized extends Warn
  object SearchUsersError extends Error

  object UsersQueryError extends Error

  // Signups
  object SignupFormShowed extends Info
  object SignupFormValidationError extends Warn
  object SignupFormError extends Error
  object SignupFormSuccessful extends Info
  object SignupsValidationError extends Warn
  object SignupsUnauthorized extends Error
  object SignupsError extends Error
  object SignupEmailError extends Error
  object SignupCreated extends Info

  // Login & Token
  object GenerateToken extends Info
  object ExpiredToken extends Warn
  object TokenDoubleUsage extends Warn
  object InvalidToken extends Warn
  object TokenError extends Error
  object MissingSignup extends Warn
  object SignupLoginExpired extends Info
  object AuthByKey extends Info // Incorrectly named (this is an auth by token)
  object AuthWithDifferentIp extends Warn
  object LoginByKey extends Info
  object AuthBySignupToken extends Info
  object UserAccessDisabled extends Info
  object LoggedInUserAccountDeleted extends Warn
  object UserSessionError extends Error

  // Files
  object FileNotFound extends Error
  object FileOpened extends Info
  object FileUnauthorized extends Warn
  object FileMetadataError extends Error
  object FileAvailable extends Info
  object FileQuarantined extends Warn
  object FileScanError extends Error
  object FileError extends Error
  object FilesDeletion extends Info
  object FileDeletionError extends Error

  // Matricules
  object FSApiAccessUnauthorized extends Warn
  object FSMatriculeInvalidData extends Warn
  object FSMatriculeError extends Error
  object FSMatriculeChanged extends Info

  // AgentConnect
  object AgentConnectSecurityWarning extends Warn
  object AgentConnectError extends Error
  object AgentConnectUpdateProviderConfiguration extends Info
  object AgentConnectUserLoginSuccessful extends Info
  object AgentConnectSignupLoginSuccessful extends Info
  object AgentConnectLoginDeactivatedUser extends Error
  object AgentConnectUnknownEmail extends Warn
  object AgentConnectClaimsSaveError extends Error

  val unauthenticatedEvents: List[EventType] = List(
    GenerateToken,
    ExpiredToken,
    TokenDoubleUsage,
    TokenError,
    TryLoginByKey,
  )

}
