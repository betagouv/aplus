package models

sealed trait Error {

  /** The `description` as logged by `EventService` ie will be displayed in the admin events on the
    * app
    */
  def description: String

  def eventType: EventType

  /** Only sent to Sentry (logged by the `Logger`) */
  def underlyingException: Option[Throwable] = None
}

object Error {

  case class Authentication(eventType: EventType, description: String) extends Error
  case class Authorization(eventType: EventType, description: String) extends Error
  case class EntityNotFound(eventType: EventType, description: String) extends Error
  case class Database(eventType: EventType, description: String) extends Error

  case class SqlException(eventType: EventType, description: String, underlying: Throwable)
      extends Error {
    override val underlyingException = Some(underlying)
  }

  case class UnexpectedServerResponse(
      eventType: EventType,
      description: String,
      status: Int,
      underlying: Throwable
  ) extends Error {
    override val underlyingException = Some(underlying)
  }

  case class Timeout(eventType: EventType, description: String, underlying: Throwable)
      extends Error {
    override val underlyingException = Some(underlying)
  }

  case class MiscException(eventType: EventType, description: String, underlying: Throwable)
      extends Error {
    override val underlyingException = Some(underlying)
  }

}
