package monitoring

import helper.StringHelper

object EventType {
  trait Event {
    val code = StringHelper.camelToUnderscoresUpperCase(this.getClass.getSimpleName)
    val level: String
  }

  sealed trait Error extends Event {
    val level = "ERROR"
  }

  sealed trait Info extends Event {
    val level = "INFO"
  }

  sealed trait Warn extends Event {
    val level = "WARN"
  }
}