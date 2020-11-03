package services

import java.time.ZonedDateTime
import java.util.UUID

import anorm._
import cats.syntax.all._
import javax.inject.Inject
import models.Authorization.UserRights
import models.sql.ApplicationRow
import models.{Answer, Application, Authorization, Error, EventType}
import play.api.db.Database
import play.api.libs.json.Json
import serializers.DataModel

import scala.concurrent.Future

@javax.inject.Singleton
class ApplicationService @Inject() (
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext
  import serializers.Anorm._
  import serializers.JsonFormats._

  def byId(id: UUID, fromUserId: UUID, rights: UserRights): Future[Either[Error, Application]] =
    Future {
      db.withConnection { implicit connection =>
        val result = SQL(
          "UPDATE application SET seen_by_user_ids = seen_by_user_ids || {seen_by_user_id}::uuid WHERE id = {id}::uuid RETURNING *"
        ).on("id" -> id, "seen_by_user_id" -> fromUserId)
          .as(ApplicationRow.ApplicationRowParser.singleOpt)
          .map(ApplicationRow.toApplication)
        result match {
          case None =>
            Error
              .EntityNotFound(
                EventType.ApplicationNotFound,
                s"Tentative d'accès à une application inexistante: $id"
              )
              .asLeft[Application]
          case Some(application) =>
            if (Authorization.canSeeApplication(application)(rights)) {
              if (Authorization.canSeePrivateDataOfApplication(application)(rights)) {
                application.asRight[Error]
              } else {
                application.anonymousApplication.asRight[Error]
              }
            } else {
              Error
                .Authorization(
                  EventType.ApplicationUnauthorized,
                  s"Tentative d'accès à une application non autorisé: $id"
                )
                .asLeft[Application]
            }
        }
      }
    }

  def openAndOlderThan(day: Int) =
    db.withConnection { implicit connection =>
      SQL(
        s"SELECT * FROM application WHERE closed = false AND age(creation_date) > '$day days' AND expert_invited = false"
      ).as(ApplicationRow.ApplicationRowParser.*).map(ApplicationRow.toApplication)
    }

  def allOpenOrRecentForUserId(
      userId: UUID,
      anonymous: Boolean,
      referenceDate: ZonedDateTime
  ): List[Application] =
    db.withConnection { implicit connection =>
      val result = SQL("""SELECT * FROM application
          |WHERE (creator_user_id = {userId}::uuid OR invited_users ?? {userId}) AND
          |  (closed = FALSE OR DATE_PART('day', {referenceDate} - closed_date) < 30)
          |ORDER BY creation_date DESC""".stripMargin)
        .on("userId" -> userId, "referenceDate" -> referenceDate)
        .as(ApplicationRow.ApplicationRowParser.*)
        .map(ApplicationRow.toApplication)
      if (anonymous) result.map(_.anonymousApplication)
      else result
    }

  def allOpenAndCreatedByUserIdAnonymous(userId: UUID): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        val result = SQL(
          """SELECT * FROM application
             WHERE creator_user_id = {userId}::uuid
             AND closed = false
             ORDER BY creation_date DESC"""
        ).on("userId" -> userId)
          .as(ApplicationRow.ApplicationRowParser.*)
          .map(ApplicationRow.toApplication)
        result.map(_.anonymousApplication)
      }
    }

  def allForUserId(userId: UUID, anonymous: Boolean) =
    db.withConnection { implicit connection =>
      val result = SQL(
        "SELECT * FROM application WHERE creator_user_id = {userId}::uuid OR invited_users ?? {userId} ORDER BY creation_date DESC"
      ).on("userId" -> userId)
        .as(ApplicationRow.ApplicationRowParser.*)
        .map(ApplicationRow.toApplication)
      if (anonymous) {
        result.map(_.anonymousApplication)
      } else {
        result
      }
    }

  def allForUserIds(userIds: List[UUID]): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"SELECT * FROM application WHERE ARRAY[$userIds]::uuid[] @> ARRAY[creator_user_id]::uuid[] OR ARRAY(select jsonb_object_keys(invited_users))::uuid[] && ARRAY[$userIds]::uuid[] ORDER BY creation_date DESC"
          .as(ApplicationRow.ApplicationRowParser.*)
          .map(ApplicationRow.toApplication)
          .map(_.anonymousApplication)
      }
    }

  def allByArea(areaId: UUID, anonymous: Boolean) =
    db.withConnection { implicit connection =>
      val result =
        SQL("SELECT * FROM application WHERE area = {areaId}::uuid ORDER BY creation_date DESC")
          .on("areaId" -> areaId)
          .as(ApplicationRow.ApplicationRowParser.*)
          .map(ApplicationRow.toApplication)
      if (anonymous) {
        result.map(_.anonymousApplication)
      } else {
        result
      }
    }

  def allForAreas(areaIds: List[UUID]): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""SELECT * FROM application WHERE ARRAY[$areaIds]::uuid[] @> ARRAY[area]::uuid[] ORDER BY creation_date DESC"""
          .as(ApplicationRow.ApplicationRowParser.*)
          .map(ApplicationRow.toApplication)
          .map(_.anonymousApplication)
      }
    }

  def all(): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""SELECT * FROM application"""
          .as(ApplicationRow.ApplicationRowParser.*)
          .map(ApplicationRow.toApplication)
          .map(_.anonymousApplication)
      }
    }

  def createApplication(application: Application) =
    db.withConnection { implicit connection =>
      val invitedUserJson = Json.toJson(application.invitedUsers.map { case (key, value) =>
        key.toString -> value
      })
      val mandatType =
        application.mandat
          .map(_.type_)
          .map(DataModel.Application.Mandat.MandatType.dataModelSerialization)

      val mandatDate = application.mandat.map(_.date)

      SQL"""
          INSERT INTO application (
            id,
            creation_date,
            creator_user_name,
            creator_user_id,
            subject,
            description,
            user_infos,
            invited_users,
            area,
            has_selected_subject,
            category,
            files,
            mandat_type,
            mandat_date
            ) VALUES (
            ${application.id}::uuid,
            ${application.creationDate},
            ${application.creatorUserName},
            ${application.creatorUserId}::uuid,
            ${application.subject},
            ${application.description},
            ${Json.toJson(application.userInfos)},
            $invitedUserJson,
            ${application.area}::uuid,
            ${application.hasSelectedSubject},
            ${application.category},
            ${Json.toJson(application.files)}::jsonb,
            $mandatType,
            $mandatDate
          )
      """.executeUpdate() === 1
    }

  def add(applicationId: UUID, answer: Answer, expertInvited: Boolean = false) =
    db.withTransaction { implicit connection =>
      val invitedUserJson = Json.toJson(answer.invitedUsers.map { case (key, value) =>
        key.toString -> value
      })
      val sql = (if (answer.declareApplicationHasIrrelevant) {
                   ", irrelevant = true "
                 } else {
                   ""
                 }) +
        (if (expertInvited) {
           ", expert_invited = true"
         } else {
           ""
         })

      SQL(
        s"""UPDATE application SET answers = answers || {answer}::jsonb,
          invited_users = invited_users || {invited_users}::jsonb $sql
          WHERE id = {id}::uuid
       """
      ).on(
        "id" -> applicationId,
        "answer" -> Json.toJson(answer),
        "invited_users" -> invitedUserJson
      ).executeUpdate()
    }

  def close(applicationId: UUID, usefulness: Option[String], closedDate: ZonedDateTime) =
    db.withTransaction { implicit connection =>
      SQL(
        """
          UPDATE application SET closed = true, usefulness = {usefulness}, closed_date = {closed_date}
          WHERE id = {id}::uuid
       """
      ).on(
        "id" -> applicationId,
        "usefulness" -> usefulness,
        "closed_date" -> closedDate
      ).executeUpdate() === 1
    }

}
