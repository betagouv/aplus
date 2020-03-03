package services

import java.util.UUID

import actions.RequestWithUserData
import javax.inject.Inject
import models._
import play.api.db.Database
import anorm._
import anorm.JodaParameterMetaData._
import helper.Time
import org.joda.time.DateTime
import play.api.Logger

@javax.inject.Singleton
class EventService @Inject() (db: Database) {

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
      user: Option[User] = None
  )(implicit request: RequestWithUserData[A]) =
    register[A](event.level)(
      request.currentUser,
      request.currentArea,
      request.remoteAddress,
      event.code,
      s"description. ${request.method} ${request.path}",
      application,
      user
    )

  val info = register("INFO") _
  val warn = register("WARN") _
  val error = register("ERROR") _

  private def register[A](level: String)(
      currentUser: User,
      currentArea: Area,
      remoteAddress: String,
      code: String,
      description: String,
      application: Option[Application],
      user: Option[User]
  ): Unit = {
    val event = Event(
      UUID.randomUUID(),
      level,
      code,
      currentUser.name,
      currentUser.id,
      DateTime.now(Time.dateTimeZone),
      description,
      currentArea.id,
      application.map(_.id),
      user.map(_.id),
      remoteAddress
    )
    addEvent(event)

    val message = s"${currentUser.name}/${currentArea.name}/${description}"
    level match {
      case "INFO" =>
        Logger.info(message)
      case "WARN" =>
        Logger.warn(message)
      case "ERROR" =>
        Logger.error(message)
      case _ =>
    }
  }

  private def addEvent(event: Event): Unit =
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
