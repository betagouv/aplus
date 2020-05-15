package models

sealed trait Error {

  /** The `description` as logged by `EventService`
    * ie will be displayed in the admin events on the app
    */
  def description: String

  def eventType: EventType

  /** Only sent to Sentry (logged by the `Logger`) */
  def underlyingException: Option[Throwable] = None
}

object Error {

  case class Authorization(eventType: EventType, description: String) extends Error
  case class EntityNotFound(eventType: EventType, description: String) extends Error
  case class Database(eventType: EventType, description: String) extends Error

  case class SqlException(eventType: EventType, description: String, underlying: Throwable)
      extends Error {
    override val underlyingException = Some(underlying)
  }

}
