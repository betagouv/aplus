package services

import java.time.{Instant, LocalDate}
import java.util.UUID

import actions.RequestWithUserData
import anorm._
import aplus.macros.Macros
import cats.syntax.all._
import helper.Time
import javax.inject.Inject
import models._
import play.api.Logger
import play.api.db.Database
import play.api.mvc.Request

import scala.concurrent.Future

@javax.inject.Singleton
class EventService @Inject() (db: Database, dependencies: ServicesDependencies) {
  import dependencies.databaseExecutionContext

  private val logger = Logger(classOf[EventService])

  private val (simpleEvent, tableFields) = Macros.parserWithFields[Event](
    "id",
    "level",
    "code",
    "from_user_name",
    "from_user_id",
    "creation_date",
    "description",
    "area",
    "to_application_id",
    "to_user_id",
    "ip_address"
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  def log(
      event: EventType,
      descriptionSanitized: String,
      additionalUnsafeData: Option[String] = None,
      application: Option[Application] = None,
      /** Not the logged-in `User`, but if the op is about some other `User`. */
      involvesUser: Option[UUID] = None,
      /** If the warn/error has an exception as cause. */
      underlyingException: Option[Throwable] = None
  )(implicit request: RequestWithUserData[_]) =
    register(event.level)(
      request.currentUser,
      request.remoteAddress,
      event.code,
      // Note: here we should have gone through the router, so
      //       request.path is supposed to be valid
      s"$descriptionSanitized. ${request.method} ${request.path}",
      additionalUnsafeData,
      application,
      involvesUser,
      underlyingException
    )

  def logError(
      error: models.Error,
      application: Option[Application] = None,
      involvesUser: Option[UUID] = None
  )(implicit request: RequestWithUserData[_]) =
    log(
      event = error.eventType,
      descriptionSanitized = error.description,
      additionalUnsafeData = error.unsafeData,
      application = application,
      involvesUser = involvesUser,
      underlyingException = error.underlyingException
    )

  /** Logs an `Error` when no user is authenticated. */
  def logErrorNoUser(
      error: models.Error,
      application: Option[Application] = None,
      involvesUser: Option[UUID] = None
  )(implicit request: Request[_]) =
    logSystem(
      event = error.eventType,
      descriptionSanitized = error.description,
      additionalUnsafeData = error.unsafeData,
      application = application,
      involvesUser = involvesUser,
      underlyingException = error.underlyingException
    )

  val info = register("INFO") _
  val warn = register("WARN") _
  val error = register("ERROR") _

  /** When there are no logged in user */
  def logSystem(
      event: EventType,
      descriptionSanitized: String,
      additionalUnsafeData: Option[String] = None,
      application: Option[Application] = None,
      involvesUser: Option[UUID] = None,
      underlyingException: Option[Throwable] = None
  )(implicit request: Request[_]): Unit =
    register(event.level)(
      currentUser = User.systemUser,
      request.remoteAddress,
      event.code,
      s"$descriptionSanitized. ${request.method} ${request.path}",
      additionalUnsafeData,
      application = application,
      involvesUser = involvesUser,
      underlyingException = underlyingException
    )

  private def register(level: String)(
      currentUser: User,
      remoteAddress: String,
      code: String,
      descriptionSanitized: String,
      additionalUnsafeData: Option[String],
      application: Option[Application],
      involvesUser: Option[UUID],
      underlyingException: Option[Throwable]
  ): Unit = {
    // DB type is 'text', but we don't want arbitrarily long strings here
    val dbDescription = additionalUnsafeData.fold(descriptionSanitized)(unsafe =>
      s"$descriptionSanitized [${unsafe.take(100000)}]"
    )
    val eventId = UUID.randomUUID()
    val event = Event(
      eventId,
      level,
      code,
      currentUser.name,
      currentUser.id,
      Instant.now(),
      dbDescription,
      Area.notApplicable.id,
      application.map(_.id),
      involvesUser,
      remoteAddress
    )
    addEvent(event)

    val message = s"${currentUser.name}/$descriptionSanitized [$eventId]"
    level match {
      case "INFO" =>
        underlyingException.fold(logger.info(message))(e => logger.info(message, e))
      case "WARN" =>
        underlyingException.fold(logger.warn(message))(e => logger.warn(message, e))
      case "ERROR" =>
        underlyingException.fold(logger.error(message))(e => logger.error(message, e))
      case _ =>
    }
  }

  private def addEvent(event: Event): Boolean =
    db.withConnection { implicit connection =>
      SQL"""
          INSERT INTO event VALUES (
            ${event.id}::uuid,
            ${event.level},
            ${event.code},
            ${event.fromUserName},
            ${event.fromUserId}::uuid,
            ${event.creationDate},
            ${event.description},
            ${event.area}::uuid,
            ${event.toApplicationId}::uuid,
            ${event.toUserId}::uuid,
            ${event.ipAddress}::inet
          )
      """.executeUpdate() === 1
    }

  def all(limit: Int, fromUserId: Option[UUID], date: Option[LocalDate]): Future[List[Event]] =
    Future {
      db.withConnection { implicit connection =>
        (fromUserId, date) match {
          case (Some(userId), Some(date)) =>
            SQL(s"""SELECT $fieldsInSelect, host(ip_address)::TEXT AS ip_address
                    FROM "event"
                    WHERE (from_user_id = {userId}::uuid OR to_user_id = {userId}::uuid)
                    AND date_trunc('day',creation_date) = {date}
                    ORDER BY creation_date DESC
                    LIMIT {limit}""")
              .on("userId" -> userId, "date" -> date, "limit" -> limit)
              .as(simpleEvent.*)
          case (None, Some(date)) =>
            SQL(s"""SELECT $fieldsInSelect, host(ip_address)::TEXT AS ip_address
                    FROM "event"
                    WHERE date_trunc('day',creation_date) = {date}
                    ORDER BY creation_date DESC
                    LIMIT {limit}""")
              .on("date" -> date, "limit" -> limit)
              .as(simpleEvent.*)
          case (Some(userId), None) =>
            SQL(s"""SELECT $fieldsInSelect, host(ip_address)::TEXT AS ip_address
                    FROM "event"
                    WHERE from_user_id = {userId}::uuid OR to_user_id = {userId}::uuid
                    ORDER BY creation_date DESC
                    LIMIT {limit}""")
              .on("userId" -> userId, "limit" -> limit)
              .as(simpleEvent.*)
          case (None, None) =>
            SQL(s"""SELECT $fieldsInSelect, host(ip_address)::TEXT AS ip_address
                    FROM "event"
                    ORDER BY creation_date DESC
                    LIMIT {limit}""")
              .on("limit" -> limit)
              .as(simpleEvent.*)
        }
      }
    }

}
