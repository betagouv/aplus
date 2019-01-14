package services

import java.util.UUID

import actions.RequestWithUserData
import javax.inject.Inject
import models._
import play.api.db.Database
import anorm._
import anorm.JodaParameterMetaData._
import org.joda.time.DateTime

@javax.inject.Singleton
class EventService @Inject()(db: Database) {

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
    "to_user_id"
  )

  def info[A](code: String, description: String, application: Option[Application] = None, user: Option[User] = None)(implicit request: RequestWithUserData[A])
     = register[A]("INFO", request)(code, description, application, user)

  def warn[A](code: String, description: String, application: Option[Application] = None, user: Option[User] = None)(implicit request: RequestWithUserData[A])
  = register[A]("WARN", request)(code, description, application, user)

  def error[A](code: String, description: String, application: Option[Application] = None, user: Option[User] = None)(implicit request: RequestWithUserData[A])
  = register[A]("ERROR", request)(code, description, application, user)

  private def register[A](level: String, request: RequestWithUserData[A])(code: String, description: String, application: Option[Application] = None, user: Option[User] = None): Unit = {
    val event = Event(
      UUID.randomUUID(),
      level,
      code,
      request.currentUser.nameWithQualite,
      request.currentUser.id,
      DateTime.now(Time.dateTimeZone),
      description,
      request.currentArea.id,
      application.map(_.id),
      user.map(_.id))
    addEvent(event)
  }

  def addEvent(event: Event): Unit = {
    db.withConnection { implicit connection =>
      SQL(
        """
          INSERT INTO event VALUES (
            {id}::uuid,
            {level},
            {code},
            {from_user_name},
            {from_user_id}::uuid,
            {creation_date},
            {description},
            {area}::uuid,
            {to_application_id}::uuid,
            {to_user_id}::uuid
          )
      """).on(
        'id -> event.id,
        'level -> event.level,
        'code -> event.code,
        'from_user_name -> event.fromUserName,
        'from_user_id -> event.fromUserId,
        'creation_date -> event.creationDate,
        'description -> event.description,
        'area -> event.area,
        'to_application_id -> event.toApplicationId,
        'to_user_id -> event.toUserId
      ).executeUpdate() == 1
    }
  }
}