package services

import java.util.UUID

import actions.RequestWithUserData
import javax.inject.Inject
import models._
import play.api.db.Database
import play.api.mvc.Request
import anorm._
import helper.Time
import play.api.Logger

@javax.inject.Singleton
class EventService @Inject() (db: Database) {

  private val logger = Logger(classOf[EventService])

  private val simpleEvent: RowParser[Event] = Macro.parser[Event](
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

  def log[A](
      event: EventType,
      description: String,
      application: Option[Application] = None,
      /** Not the logged-in `User`, but if the op is about some other `User`. */
      involvesUser: Option[User] = None,
      /** If the warn/error has an exception as cause. */
      underlyingException: Option[Throwable] = None
  )(implicit request: RequestWithUserData[A]) =
    register(event.level)(
      request.currentUser,
      request.remoteAddress,
      event.code,
      s"$description. ${request.method} ${request.path}",
      application,
      involvesUser,
      underlyingException
    )

  def logError[A](
      error: models.Error,
      application: Option[Application] = None,
      involvesUser: Option[User] = None
  )(implicit request: RequestWithUserData[A]) =
    log(
      event = error.eventType,
      description = error.description,
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
      description: String,
      application: Option[Application] = None,
      involvesUser: Option[User] = None,
      underlyingException: Option[Throwable] = None
  )(implicit request: Request[_]): Unit =
    register(event.level)(
      currentUser = User.systemUser,
      request.remoteAddress,
      event.code,
      s"$description. ${request.method} ${request.path}",
      application = application,
      involvesUser = involvesUser,
      underlyingException = underlyingException
    )

  private def register(level: String)(
      currentUser: User,
      remoteAddress: String,
      code: String,
      description: String,
      application: Option[Application],
      involvesUser: Option[User],
      underlyingException: Option[Throwable]
  ): Unit = {
    val event = Event(
      UUID.randomUUID(),
      level,
      code,
      currentUser.name,
      currentUser.id,
      Time.nowParis(),
      description,
      Area.notApplicable.id,
      application.map(_.id),
      involvesUser.map(_.id),
      remoteAddress
    )
    addEvent(event)

    val message = s"${currentUser.name}/${description}"
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
      """.executeUpdate() == 1
    }

  def all(limit: Int = 1000, fromUserId: Option[UUID] = None) = db.withConnection {
    implicit connection =>
      fromUserId match {
        case Some(userId) =>
          SQL"""SELECT *, host(ip_address)::TEXT AS ip_address FROM "event" WHERE from_user_id = $userId::uuid OR to_user_id = $userId::uuid ORDER BY creation_date DESC LIMIT $limit"""
            .as(simpleEvent.*)
        case None =>
          SQL"""SELECT *, host(ip_address)::TEXT AS ip_address FROM "event" ORDER BY creation_date DESC LIMIT $limit"""
            .as(simpleEvent.*)
      }
  }
}
