package models

import helper.StringHelper

/** Note: `EventType` is used for logging, and logging is an orthogonal concern.
  *       ie it shows up everywhere in an application. So we expect `EventType` to show up
  *       at any level in the the code (wherever the best information to log is known).
  */
trait EventType {
  val code = StringHelper.camelToUnderscoresUpperCase(this.getClass.getSimpleName)
  val level: String
}

object EventType {

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
  object AllUserCSVUnauthorized extends Warn
  object AllUserCsvShowed extends Info
  object AllUserIncorrectSetup extends Info
  object AllUserUnauthorized extends Warn
  object AnswerCreated extends Info
  object AnswerNotCreated extends Error
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
  object ChangeAreaUnauthorized extends Warn
  object DeleteUserUnauthorized extends Warn
  object DeploymentDashboardUnauthorized extends Warn
  object EditGroupShowed extends Info
  object EditGroupUnauthorized extends Warn
  object EditUserError extends Error
  object EditUserGroupError extends Error
  object EditUserShowed extends Info
  object EventsShowed extends Info
  object EventsUnauthorized extends Warn
  object FileNotFound extends Error
  object FileOpened extends Info
  object FileUnauthorized extends Warn
  object GroupDeletionUnauthorized extends Warn
  object ImportGroupUnauthorized extends Warn
  object ImportUserError extends Error
  object ImportUserUnauthorized extends Warn
  object ImportUsersUnauthorized extends Warn
  object InviteNotCreated extends Error
  object MandatInitiationBySmsInvalid extends Error
  object MandatInitiationBySmsWarn extends Warn
  object MandatInitiationBySmsDone extends Info
  object MandatBySmsResponseSaved extends Info
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
  object SmsCallbackError extends Error
  object StatsShowed extends Info
  object StatsUnauthorized extends Warn
  object TerminateCompleted extends Info
  object TerminateError extends Error
  object TerminateIncompleted extends Error
  object TerminateNotFound extends Error
  object TerminateUnauthorized extends Warn
  object ToCGURedirected extends Info
  object UserAccessDisabled extends Info
  object UserCreated extends Info
  object UserDeleted extends Info
  object UserEdited extends Info
  object UserGroupCreated extends Info
  object UserGroupDeleted extends Info
  object UserGroupDeletionUnauthorized extends Error
  object UserGroupEdited extends Info
  object UserGroupNotFound extends Error
  object UserIsUsed extends Error
  object UserNotFound extends Error
  object UserShowed extends Info
  object UsersCreated extends Info
  object UsersImported extends Info
  object UsersShowed extends Info
  object ViewUserUnauthorized extends Warn

  object ApplicationCreationInvalid extends Info
  object AuthByKey extends Info
  object AuthWithDifferentIp extends Warn
  object CSVImportFormError extends Warn
  object CsvImportInputEmpty extends Warn
  object ExpiredToken extends Warn
  object GenerateToken extends Info
  object ImportUserFormError extends Warn
  object LoginByKey extends Info
  object StatsIncorrectSetup extends Warn
  object TryLoginByKey extends Info
  object UnknownEmail extends Warn
}
