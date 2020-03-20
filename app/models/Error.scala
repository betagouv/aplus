package models

sealed trait Error

object Error {

  case object Authorization extends Error
  case object EntityNotFound extends Error

}
