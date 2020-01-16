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
}
