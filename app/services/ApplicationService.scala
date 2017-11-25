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

  private val simple: RowParser[Application] = Macro.parser[Application](
    "id",
    "status",
    "creation_date",
    "helper_name",
    "helper_user_id",
    "subject", "description", "user_infos", "invited_user_ids", "area"
  )

  private var applications = DemoData.applications

  def byId(id: UUID): Option[Application] = db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE id = CAST({id} AS UUID) ").on('id -> id).as(simple.singleOpt)
  }.orElse(applications.find(_.id == id))

  def allForHelperUserId(helperUserId: UUID) = applications.filter(_.helperUserId == helperUserId) ++ db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE helper_user_id = {helperUserId}::uuid").on('helperUserId -> helperUserId).as(simple.*)
  }

  def allForInvitedUserId(invitedUserId: UUID) = applications.filter(_.invitedUserIDs.contains(invitedUserId)) ++ db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE invited_user_ids @> ARRAY[{invitedUserId}::uuid]::uuid[]").on('invitedUserId -> invitedUserId).as(simple.*)
  }

  def createApplication(newApplication: Application) = db.withTransaction { implicit connection =>
    SQL(
      s"""
          INSERT INTO application VALUES (
            {id}::uuid,
            {status},
            {creation_date},
            {helper_name},
            {helper_user_id}::uuid,
            {subject},
            {description},
            {user_infos},
            ARRAY[{invited_user_ids}]::uuid[],
            {area}::uuid
          )
      """).on(
      'id ->   newApplication.id,
      'status -> newApplication.status,
      'creation_date -> newApplication.creationDate,
      'helper_name -> newApplication.helperName,
      'helper_user_id -> newApplication.helperUserId,
      'subject -> newApplication.subject,
      'description -> newApplication.description,
      'user_infos -> Json.toJson(newApplication.userInfos),
      'invited_user_ids -> newApplication.invitedUserIDs,
      'area -> newApplication.area
    ).executeUpdate()
  }

  def add(answer: Answer): Unit = {
    applications = applications.map { application =>
      if (application.id != answer.applicationId) {
        application
      } else {
        application //.copy(invitedUserIDs = answer.invitedUsers.map(_.id), answers = application.answers ++ List(answer))
      }
    }
  }
}

