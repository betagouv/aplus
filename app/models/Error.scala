package models

sealed trait Error {

  /** The `description` as logged by `EventService` ie will be displayed in the admin events on the
    * app. It SHOULD NOT contain user controlled data (because it is logged to stdout).
    */
  def description: String

  /** This is where arbitrary data controlled by users must be logged. This field is only saved in
    * DB and not sent to stdout.
    */
  def unsafeData: Option[String]

  def eventType: EventType

  /** Only sent to Sentry (logged by the `Logger`) */
  def underlyingException: Option[Throwable] = None
}

object Error {

  case class Authentication(eventType: EventType, description: String, unsafeData: Option[String])
      extends Error

  case class Authorization(eventType: EventType, description: String, unsafeData: Option[String])
      extends Error

  case class EntityNotFound(eventType: EventType, description: String, unsafeData: Option[String])
      extends Error

  // Invalid data from user, ...
  case class RequirementFailed(
      eventType: EventType,
      description: String,
      unsafeData: Option[String]
  ) extends Error

  case class Database(eventType: EventType, description: String, unsafeData: Option[String])
      extends Error

  case class SqlException(
      eventType: EventType,
      description: String,
      underlying: Throwable,
      unsafeData: Option[String]
  ) extends Error {
    override val underlyingException: Option[Throwable] = Some(underlying)
  }

  case class UnexpectedServerResponse(
      eventType: EventType,
      description: String,
      status: Int,
      underlying: Throwable,
      unsafeData: Option[String]
  ) extends Error {
    override val underlyingException: Option[Throwable] = Some(underlying)
  }

  case class Timeout(
      eventType: EventType,
      description: String,
      underlying: Throwable,
      unsafeData: Option[String]
  ) extends Error {
    override val underlyingException: Option[Throwable] = Some(underlying)
  }

  case class MiscException(
      eventType: EventType,
      description: String,
      underlying: Throwable,
      unsafeData: Option[String]
  ) extends Error {
    override val underlyingException: Option[Throwable] = Some(underlying)
  }

}
