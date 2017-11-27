package services

import java.util.UUID
import javax.inject.Inject

import anorm.Column.nonNull
import models.{Answer, Application}
import play.api.db.Database
import play.api.libs.json.Json
import utils.DemoData
import anorm._
import anorm.JodaParameterMetaData._

@javax.inject.Singleton
class ApplicationService @Inject()(db: Database) {
  import utils.Anorm._

  private implicit val answerListParser: anorm.Column[List[Answer]] =
    nonNull { (value, meta) =>
      Left(UnexpectedNullableFound(s"This should not append"))
    }

  private val simpleApplication: RowParser[Application] = Macro.parser[Application](
    "id",
    "status",
    "creation_date",
    "creator_user_name",
    "creator_user_id",
    "subject",
    "description",
    "user_infos",
    "invited_users",
    "area"
  )

  private val simpleAnswer: RowParser[Answer] = Macro.parser[Answer](
    "id",
    "application_id",
    "creation_date",
    "message",
    "creator_user_id",
    "creator_user_name",
    "invited_users",
    "visible_by_helpers",
    "area"
  )

  private val applications = DemoData.applications    //TODO : remove

  def byId(id: UUID): Option[Application] = db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE id = CAST({id} AS UUID) ").on('id -> id).as(simpleApplication.singleOpt)
  }.orElse(applications.find(_.id == id))

  def allForCreatorUserId(creatorUserId: UUID) = db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE creator_user_id = {creatorUserId}::uuid")
      .on('creatorUserId -> creatorUserId).as(simpleApplication.*)
  } ++ applications.filter(_.creatorUserId == creatorUserId)

  def allForInvitedUserId(invitedUserId: UUID) = db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE invited_users ?? {invitedUserId}")
      .on('invitedUserId -> invitedUserId).as(simpleApplication.*)
  } ++ applications.filter(_.invitedUsers.contains(invitedUserId))

  def createApplication(newApplication: Application) = db.withConnection { implicit connection =>
    val invitedUserJson = Json.toJson(newApplication.invitedUsers.map {
      case (key, value) =>
         key.toString -> value
    })
    SQL(
      """
          INSERT INTO application VALUES (
            {id}::uuid,
            {status},
            {creation_date},
            {creator_user_name},
            {creator_user_id}::uuid,
            {subject},
            {description},
            {user_infos},
            {invited_users},
            {area}::uuid
          )
      """).on(
      'id ->   newApplication.id,
      'status -> newApplication.status,
      'creation_date -> newApplication.creationDate,
      'creator_user_name -> newApplication.creatorUserName,
      'creator_user_id -> newApplication.creatorUserId,
      'subject -> newApplication.subject,
      'description -> newApplication.description,
      'user_infos -> Json.toJson(newApplication.userInfos),
      'invited_users -> invitedUserJson,
      'area -> newApplication.area
    ).executeUpdate()
  }

  def answersByApplicationId(applicationId: UUID) = db.withConnection { implicit connection =>
    SQL("SELECT * FROM answer WHERE application_id = {applicationId}::uuid")
    .on('applicationId -> applicationId).as(simpleAnswer.*)
  } ++ DemoData.answers.filter(_.applicationId == applicationId)


  def add(answer: Answer) = db.withTransaction { implicit connection =>
    val invitedUserJson = Json.toJson(answer.invitedUsers.map {
      case (key, value) =>
        key.toString -> value
    })
    SQL(
      """
          INSERT INTO answer VALUES (
            {id}::uuid,
            {application_id}::uuid,
            {creation_date},
            {message},
            {creator_user_id}::uuid,
            {creator_user_name},
            {invited_users},
            {visible_by_helpers},
            {area}::uuid
          )
      """).on(
      'id ->   answer.id,
      'application_id -> answer.applicationId,
      'creation_date -> answer.creationDate,
      'message -> answer.message,
      'creator_user_id -> answer.creatorUserID,
      'creator_user_name -> answer.creatorUserName,
      'invited_users -> invitedUserJson,
      'visible_by_helpers -> answer.visibleByHelpers,
      'area -> answer.area
    ).executeUpdate()
    SQL(
       """
          UPDATE application SET invited_users = invited_users || {invited_users}::jsonb
          WHERE id = {id}::uuid
       """
    ).on(
      'id -> answer.applicationId,
      'invited_users -> invitedUserJson
    ).executeUpdate()
  }
}

