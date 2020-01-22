package models

import helper.StringHelper

trait EventType {
  val code = StringHelper.camelToUnderscoresUpperCase(this.getClass.getSimpleName)
  val level: String
}

sealed trait Error extends EventType {
  val level = "ERROR"
}

sealed trait Info extends EventType {
  val level = "INFO"
}

sealed trait Warn extends EventType {
  val level = "WARN"
}

object EventType {
  object AddExpertCreated extends Info
  object AgentsAdded extends Info
  object AllApplicationsShowed extends Info
  object AllAsShowed extends Info
  object AllCSVShowed extends Info
  object AllUserCsvShowed extends Info
  object AllUserIncorrectSetup extends Info
  object AnswerCreated extends Info
  object ApplicationCreated extends Info
  object ApplicationFormShowed extends Info
  object ApplicationShowed extends Info
  object AreaChanged extends Info
  object CGUShowed extends Info
  object CGUValidated extends Info
  object EditGroupShowed extends Info
  object EditUserShowed extends Info
  object EventsShowed extends Info
  object FileOpened extends Info
  object MyApplicationsShowed extends Info
  object MyCSVShowed extends Info
  object ToCGURedirected extends Info
  object StatsShowed extends Info
  object TerminateCompleted extends Info
  object UserAccessDisabled extends Info
  object UserCreated extends Info
  object UserDeleted extends Info
  object UserEdited extends Info
  object UserGroupCreated extends Info
  object UserGroupDeleted extends Info
  object UserGroupEdited extends Info
  object UserShowed extends Info
  object UsersCreated extends Info
  object UsersImported extends Info
  object UsersShowed extends Info

  object ApplicationCreationInvalid extends Info
  object AuthByKey extends Info
  object GenerateToken extends Info
  object LoginByKey extends Info
  object TryLoginByKey extends Info
}
