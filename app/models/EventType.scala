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
  object AddUserDone extends Info
  object AddUserGroupDone extends Info
  object AddUsersDone extends Info
  object DeleteGroupDone extends Info
  object EditUserDone extends Info
  object EditUserGroupDone extends Info
  object ImportUsersDone extends Info

  object AllUserShowed extends Info
  object AllUserCsvShowed extends Info
  object UserShowed extends Info
  object CGUShowed extends Info
  object EditUserShowed extends Info
  object EditGroupShowed extends Info
  object EventsShowed extends Info
  object ApplicationFormShowed extends Info
  object AllApplicationsShowed extends Info
  object MyApplicationsShowed extends Info
  object StatsShowed extends Info
  object AllAsShowed extends Info
  object MyCSVShowed extends Info
  object AllCSVShowed extends Info
  object ApplicationShowed extends Info

  object ApplicationCreated extends Info
  object AnswerCreated extends Info
  object AddExpertCreated extends Info

  object TerminateCompleted extends Info
  object AgentsAdded extends Info
  object CGUValidated extends Info
  object AllUserIncorrectSetup extends Info
  object UserDeleted extends Info
  object GenerateToken extends Info
  object AreaChange extends Info
  object ApplicationCreationInvalid extends Info
  object FileOpen extends Info
  object LoginByKey extends Info
  object TryLoginByKey extends Info
  object UserAccessDisabled extends Info
  object RedirectedToCGU extends Info
  object AuthByKey extends Info
}
