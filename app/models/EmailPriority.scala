package models

sealed trait EmailPriority

object EmailPriority {
  case object Urgent extends EmailPriority
  case object Normal extends EmailPriority
}
