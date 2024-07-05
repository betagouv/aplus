package services

import actions.RequestWithUserData
import anorm._
import aplus.macros.Macros
import cats.syntax.all._
import java.time.{Instant, LocalDate}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Area, Error, Event, EventType, User}
import play.api.Logger
import play.api.db.Database
import play.api.mvc.Request
import scala.concurrent.Future
import scala.util.Try

@Singleton
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
      applicationId: Option[UUID] = None,
      /** Not the logged-in `User`, but if the op is about some other `User`. */
      involvesUser: Option[UUID] = None,
      /** If the warn/error has an exception as cause. */
      underlyingException: Option[Throwable] = None
  )(implicit request: RequestWithUserData[_]): Unit =
    register(event.level)(
      request.currentUser,
      request.remoteAddress,
      event.code, {
        val session = request.userSession.map(session => " " + session.id).getOrElse("")
        // Note: here we should have gone through the router, so
        //       request.path is supposed to be valid
        s"$descriptionSanitized.$session ${request.method} ${request.path}"
      },
      additionalUnsafeData,
      applicationId,
      involvesUser,
      underlyingException
    )

  def logError(
      error: models.Error,
      applicationId: Option[UUID] = None,
      involvesUser: Option[UUID] = None
  )(implicit request: RequestWithUserData[_]): Unit =
    log(
      event = error.eventType,
      descriptionSanitized = error.description,
      additionalUnsafeData = error.unsafeData,
      applicationId = applicationId,
      involvesUser = involvesUser,
      underlyingException = error.underlyingException
    )

  /** Logs an `Error` when no user is authenticated. */
  def logErrorNoUser(
      error: models.Error,
      applicationId: Option[UUID] = None,
      involvesUser: Option[UUID] = None
  )(implicit request: Request[_]): Unit =
    logSystem(
      event = error.eventType,
      descriptionSanitized = error.description,
      additionalUnsafeData = error.unsafeData,
      applicationId = applicationId,
      involvesUser = involvesUser,
      underlyingException = error.underlyingException
    )

  type Log = (
      User,
      String,
      String,
      String,
      Option[String],
      Option[UUID],
      Option[UUID],
      Option[Throwable]
  ) => Unit

  val info: Log = register("INFO") _
  val warn: Log = register("WARN") _
  val error: Log = register("ERROR") _

  /** When there are no logged in user */
  def logSystem(
      event: EventType,
      descriptionSanitized: String,
      additionalUnsafeData: Option[String] = None,
      applicationId: Option[UUID] = None,
      involvesUser: Option[UUID] = None,
      underlyingException: Option[Throwable] = None
  )(implicit request: Request[_]): Unit =
    register(event.level)(
      currentUser = User.systemUser,
      request.remoteAddress,
      event.code,
      s"$descriptionSanitized. ${request.method} ${request.path}",
      additionalUnsafeData,
      applicationId = applicationId,
      involvesUser = involvesUser,
      underlyingException = underlyingException
    )

  /** Internal errors without requests: jobs, etc. */
  def logErrorNoRequest(
      error: models.Error,
      applicationId: Option[UUID] = None,
      involvesUser: Option[UUID] = None
  ): Unit = logNoRequest(
    error.eventType,
    error.description,
    error.unsafeData,
    applicationId = applicationId,
    involvesUser = involvesUser,
    underlyingException = error.underlyingException
  )

  /** Internal errors without requests: jobs, etc. */
  def logNoRequest(
      event: EventType,
      descriptionSanitized: String,
      additionalUnsafeData: Option[String] = None,
      applicationId: Option[UUID] = None,
      involvesUser: Option[UUID] = None,
      underlyingException: Option[Throwable] = None
  ): Unit =
    register(event.level)(
      currentUser = User.systemUser,
      "0.0.0.0",
      event.code,
      descriptionSanitized,
      additionalUnsafeData,
      applicationId,
      involvesUser,
      underlyingException
    )

  private def register(level: String)(
      currentUser: User,
      remoteAddress: String,
      code: String,
      descriptionSanitized: String,
      additionalUnsafeData: Option[String],
      applicationId: Option[UUID],
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
      applicationId,
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

  def beforeOrThrow(beforeExcluded: Instant, limit: Int): List[Event] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect, host(ip_address)::TEXT AS ip_address
              FROM "event"
              WHERE creation_date < {beforeExcluded}
              ORDER BY creation_date DESC
              LIMIT {limit}
              """)
        .on("beforeExcluded" -> beforeExcluded, "limit" -> limit)
        .as(simpleEvent.*)
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

  // Supported by index event (from_user_id, creation_date DESC)
  def lastActivity(userIds: List[UUID]): Future[Either[Error, List[(UUID, Instant)]]] =
    Future(
      Try {
        val ids = userIds.distinct
        db.withConnection { implicit connection =>
          SQL(s"""SELECT DISTINCT ON (from_user_id) from_user_id, creation_date
                  FROM event
                  WHERE from_user_id = ANY(array[{ids}]::uuid[])
                  AND code <> ALL(array[{unauthenticated_events}])
                  ORDER BY from_user_id, creation_date DESC
               """)
            .on(
              "ids" -> ids,
              "unauthenticated_events" -> EventType.unauthenticatedEvents.map(_.code)
            )
            .as((SqlParser.get[UUID]("from_user_id") ~ SqlParser.get[Instant]("creation_date")).*)
            .map(SqlParser.flatten)
        }
      }.toEither.left
        .map(error =>
          Error.SqlException(
            EventType.EventsError,
            "Impossible de lire les dernières activités",
            error,
            none
          )
        )
    )

}
