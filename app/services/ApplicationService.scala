package services

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.Future

import javax.inject.Inject
import anorm.Column.nonNull
import models.{Answer, Application, Authorization, Error, EventType}
import models.Authorization.UserRights
import play.api.db.Database
import play.api.libs.json.Json
import anorm._
import helper.Time
import serializers.DataModel

@javax.inject.Singleton
class ApplicationService @Inject() (
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext

  import serializers.Anorm._
  import serializers.JsonFormats._

  // Note:
  // anorm.Column[String] => anorm.Column[Option[Application.MandatType]] does not work
  // throws exception "AnormException: 'mandat_type' not found, available columns: ..."
  implicit val mandatTypeAnormParser: anorm.Column[Option[Application.MandatType]] =
    implicitly[anorm.Column[Option[String]]]
      .map(_.flatMap(DataModel.Application.MandatType.dataModelDeserialization))

  private implicit val answerReads = Json.reads[Answer]
  private implicit val answerWrite = Json.writes[Answer]

  implicit val answerListParser: anorm.Column[List[Answer]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(Json.parse(json.getValue).as[List[Answer]])
        case json: String =>
          Right(Json.parse(json).as[List[Answer]])
        case _ =>
          Left(
            TypeDoesNotMatch(
              s"Cannot convert $value: ${className(value)} to List[Answer] for column $qualified"
            )
          )
      }
    }

  private val simpleApplication: RowParser[Application] = Macro
    .parser[Application](
      "id",
      "creation_date",
      "creator_user_name",
      "creator_user_id",
      "subject",
      "description",
      "user_infos",
      "invited_users",
      "area",
      "irrelevant",
      "answers", // Data have been left bad migrated from answser_unsed
      "internal_id",
      "closed",
      "seen_by_user_ids",
      "usefulness",
      "closed_date",
      "expert_invited",
      "has_selected_subject",
      "category",
      "files",
      "mandat_type",
      "mandat_date"
    )
    .map(application =>
      application.copy(
        creationDate = application.creationDate.withZoneSameInstant(Time.timeZoneParis),
        answers = application.answers.map(answer =>
          answer.copy(creationDate = answer.creationDate.withZoneSameInstant(Time.timeZoneParis))
        )
      )
    )

  def byId(id: UUID, fromUserId: UUID, rights: UserRights): Future[Either[Error, Application]] =
    Future {
      db.withConnection { implicit connection =>
        val result = SQL(
          "UPDATE application SET seen_by_user_ids = seen_by_user_ids || {seen_by_user_id}::uuid WHERE id = {id}::uuid RETURNING *"
        ).on("id" -> id, "seen_by_user_id" -> fromUserId)
          .as(simpleApplication.singleOpt)
        result match {
          case None =>
            Left(
              Error.EntityNotFound(
                EventType.ApplicationNotFound,
                s"Tentative d'accès à une application inexistante: $id"
              )
            )
          case Some(application) =>
            if (Authorization.canSeeApplication(application)(rights)) {
              if (Authorization.canSeePrivateDataOfApplication(application)(rights)) {
                Right(application)
              } else {
                Right(application.anonymousApplication)
              }
            } else {
              Left(
                Error.Authorization(
                  EventType.ApplicationUnauthorized,
                  s"Tentative d'accès à une application non autorisé: $id"
                )
              )
            }
        }
      }
    }

  def openAndOlderThan(day: Int) = db.withConnection { implicit connection =>
    SQL(
      s"SELECT * FROM application WHERE closed = false AND age(creation_date) > '$day days' AND expert_invited = false"
    ).as(simpleApplication.*)
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
        .as(simpleApplication.*)
      if (anonymous) {
        result.map(_.anonymousApplication)
      } else {
        result
      }
    }

  def allForUserId(userId: UUID, anonymous: Boolean) = db.withConnection { implicit connection =>
    val result = SQL(
      "SELECT * FROM application WHERE creator_user_id = {userId}::uuid OR invited_users ?? {userId} ORDER BY creation_date DESC"
    ).on("userId" -> userId)
      .as(simpleApplication.*)
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
          .as(simpleApplication.*)
          .map(_.anonymousApplication)
      }
    }

  def allByArea(areaId: UUID, anonymous: Boolean) = db.withConnection { implicit connection =>
    val result =
      SQL("SELECT * FROM application WHERE area = {areaId}::uuid ORDER BY creation_date DESC")
        .on("areaId" -> areaId)
        .as(simpleApplication.*)
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
          .as(simpleApplication.*)
          .map(_.anonymousApplication)
      }
    }

  def all(): Future[List[Application]] = Future {
    db.withConnection { implicit connection =>
      SQL"""SELECT * FROM application""".as(simpleApplication.*).map(_.anonymousApplication)
    }
  }

  def createApplication(newApplication: Application) = db.withConnection { implicit connection =>
    val invitedUserJson = Json.toJson(newApplication.invitedUsers.map {
      case (key, value) =>
        key.toString -> value
    })
    val mandatType =
      newApplication.mandatType.map(DataModel.Application.MandatType.dataModelSerialization)
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
            ${newApplication.id}::uuid,
            ${newApplication.creationDate},
            ${newApplication.creatorUserName},
            ${newApplication.creatorUserId}::uuid,
            ${newApplication.subject},
            ${newApplication.description},
            ${Json.toJson(newApplication.userInfos)},
            ${invitedUserJson},
            ${newApplication.area}::uuid,
            ${newApplication.hasSelectedSubject},
            ${newApplication.category},
            ${Json.toJson(newApplication.files)}::jsonb,
            $mandatType,
            ${newApplication.mandatDate}
          )
      """.executeUpdate() == 1
  }

  def add(applicationId: UUID, answer: Answer, expertInvited: Boolean = false) =
    db.withTransaction { implicit connection =>
      val invitedUserJson = Json.toJson(answer.invitedUsers.map {
        case (key, value) =>
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
        )
        .executeUpdate()
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
        )
        .executeUpdate() == 1
    }
}
