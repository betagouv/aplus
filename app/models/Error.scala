package models

sealed trait Error

object Error {

    case class Authorization(message: String) extends Error
    case object EntityNotFound extends Error

}
