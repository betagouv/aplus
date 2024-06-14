package services

import anorm._
import anorm.Column.nonNull
import aplus.macros.Macros
import cats.syntax.all._
import helper.StringHelper.StringListOps
import helper.Time
import java.sql.Connection
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import models.{dataModels, Answer, Application, Authorization, Error, EventType}
import models.Application.SeenByUser
import models.Authorization.UserRights
import org.postgresql.util.PGobject
import play.api.db.Database
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import scala.Option.empty
import scala.concurrent.Future

@javax.inject.Singleton
class ApplicationService @Inject() (
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext
  import serializers.Anorm._

  // Note:
  // anorm.Column[String] => anorm.Column[Option[Application.MandatType]] does not work
  // throws exception "AnormException: 'mandat_type' not found, available columns: ..."
  implicit val mandatTypeAnormParser: anorm.Column[Option[Application.MandatType]] =
    implicitly[anorm.Column[Option[String]]]
      .map(_.flatMap(dataModels.Application.MandatType.dataModelDeserialization))

  import dataModels.Answer._

  implicit val answerListParser: anorm.Column[List[Answer]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Json.parse(json.getValue).as[List[Answer]].asRight[SqlRequestError]
        case json: String =>
          Json.parse(json).as[List[Answer]].asRight[SqlRequestError]
        case _ =>
          TypeDoesNotMatch(
            s"Cannot convert $value: ${className(value)} to List[Answer] for column $qualified"
          ).asLeft[List[Answer]]
      }
    }

  import dataModels.Application.SeenByUser._

  private val (parser, tableFields) = Macros.parserWithFields[Application](
    "id",
    "creation_date",
    "creator_user_name",
    "creator_user_id",
    "creator_group_id",
    "creator_group_name",
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
    "mandat_type",
    "mandat_date",
    "invited_group_ids",
    "personal_data_wiped"
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  private val simpleApplication: RowParser[Application] = parser
    .map(application =>
      application.copy(
        creationDate = application.creationDate.withZoneSameInstant(Time.timeZoneParis),
        answers = application.answers.map(answer =>
          answer.copy(creationDate = answer.creationDate.withZoneSameInstant(Time.timeZoneParis))
        )
      )
    )

  private def setSeenByUsers(id: UUID, seenByUsers: List[SeenByUser])(implicit
      cnx: Connection
  ): Option[Application] = {
    val pgObject = new PGobject
    pgObject.setType("json")
    pgObject.setValue(toJson(seenByUsers).toString)

    SQL(s"""UPDATE application
           |SET seen_by_user_ids = {seen_by_users}::jsonb
           |WHERE id = {id}::uuid
           |RETURNING $fieldsInSelect;""".stripMargin)
      .on(
        "id" -> id,
        "seen_by_users" -> anorm.Object(pgObject)
      )
      .as(simpleApplication.singleOpt)
  }

  private def byId(id: UUID)(implicit cnx: Connection) =
    SQL(s"""SELECT $fieldsInSelect
           |FROM application
           |WHERE id = {id}::uuid;""".stripMargin)
      .on(
        "id" -> id
      )
      .as(simpleApplication.singleOpt)

  def byId(id: UUID, userId: UUID, rights: UserRights): Future[Either[Error, Application]] =
    Future {
      db.withTransaction { implicit connection =>
        val result = byId(id) match {
          case Some(application) =>
            val newSeen = SeenByUser.now(userId)
            val seenByUsers = newSeen :: application.seenByUsers.filter(_.userId =!= userId)
            setSeenByUsers(id, seenByUsers)
          case None => empty[Application]
        }
        result match {
          case None =>
            val message = s"Tentative d'accès à une application inexistante: $id"
            Error.EntityNotFound(EventType.ApplicationNotFound, message, none).asLeft[Application]
          case Some(application) =>
            if (Authorization.canSeeApplication(application)(rights)) {
              if (Authorization.canSeePrivateDataOfApplication(application)(rights))
                application.asRight[Error]
              else application.anonymousApplication.asRight[Error]
            } else {
              val message = s"Tentative d'accès à une application non autorisé: $id"
              Error
                .Authorization(EventType.ApplicationUnauthorized, message, none)
                .asLeft[Application]
            }
        }
      }
    }

  def openAndOlderThan(day: Int): List[Application] =
    db.withConnection { implicit connection =>
      SQL(
        s"""SELECT $fieldsInSelect
            FROM application
            WHERE closed = false
            AND age(creation_date) > '$day days'
            AND expert_invited = false"""
      ).as(simpleApplication.*)
    }

  def allOpenOrRecentForUserId(
      userId: UUID,
      anonymous: Boolean,
      referenceDate: ZonedDateTime
  ): List[Application] =
    db.withConnection { implicit connection =>
      val result = SQL(s"""SELECT $fieldsInSelect FROM application
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

  def allOpenAndCreatedByUserIdAnonymous(userId: UUID): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        val result = SQL(
          s"""SELECT $fieldsInSelect FROM application
              WHERE creator_user_id = {userId}::uuid
              AND closed = false
              ORDER BY creation_date DESC"""
        ).on("userId" -> userId)
          .as(simpleApplication.*)
        result.map(_.anonymousApplication)
      }
    }

  def allForUserId(userId: UUID, anonymous: Boolean): List[Application] =
    db.withConnection { implicit connection =>
      val result = SQL(
        s"""SELECT $fieldsInSelect
            FROM application
            WHERE creator_user_id = {userId}::uuid
            OR invited_users ?? {userId}
            ORDER BY creation_date DESC"""
      ).on("userId" -> userId)
        .as(simpleApplication.*)
      if (anonymous) {
        result.map(_.anonymousApplication)
      } else {
        result
      }
    }

  private def monthsFilter(numOfMonths: Option[Int]): String =
    numOfMonths
      .filter(_ >= 1)
      .map(months =>
        "AND creation_date >= date_trunc('month', now()) - " +
          s"interval '$months month'"
      )
      .orEmpty

  def allForUserIds(
      userIds: List[UUID],
      numOfMonths: Option[Int],
      anonymous: Boolean = true
  ): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        val additionalFilter = monthsFilter(numOfMonths)
        val result = SQL(
          s"""SELECT $fieldsInSelect
              FROM application
              WHERE
                (
                ARRAY[{userIds}]::uuid[] @> ARRAY[creator_user_id]::uuid[]
                OR
                ARRAY(select jsonb_object_keys(invited_users))::uuid[] && ARRAY[{userIds}]::uuid[]
                )
              $additionalFilter
              ORDER BY creation_date DESC"""
        ).on("userIds" -> userIds)
          .as(simpleApplication.*)
        if (anonymous)
          result.map(_.anonymousApplication)
        else
          result
      }
    }

  def allByArea(areaId: UUID, anonymous: Boolean): List[Application] =
    db.withConnection { implicit connection =>
      val result =
        SQL(
          s"""SELECT $fieldsInSelect
              FROM application
              WHERE area = {areaId}::uuid
              ORDER BY creation_date DESC"""
        ).on("areaId" -> areaId)
          .as(simpleApplication.*)
      if (anonymous) {
        result.map(_.anonymousApplication)
      } else {
        result
      }
    }

  def allForAreas(
      areaIds: List[UUID],
      numOfMonths: Option[Int],
      anonymous: Boolean = true
  ): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        val additionalFilter = monthsFilter(numOfMonths)
        val result = SQL(s"""SELECT $fieldsInSelect
                FROM application
                WHERE ARRAY[{areaIds}]::uuid[] @> ARRAY[area]::uuid[]
                $additionalFilter
                ORDER BY creation_date DESC""")
          .on("areaIds" -> areaIds)
          .as(simpleApplication.*)
        if (anonymous) {
          result.map(_.anonymousApplication)
        } else {
          result
        }
      }
    }

  def byInvitedGroupIdAndOpen(groupId: UUID): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        val _ = SQL("""REFRESH MATERIALIZED VIEW answer_metadata""").execute()
        val _ = SQL("""REFRESH MATERIALIZED VIEW user_group_is_invited_on_application""").execute()

        val result = SQL(
          s"""SELECT $fieldsInSelect
              FROM application
              WHERE
                application.id IN (
                  SELECT application_id
                  FROM user_group_is_invited_on_application
                  WHERE user_group_is_invited_on_application.group_id = {groupId}::uuid
                )
              AND
                closed_date IS NULL
              ORDER BY creation_date DESC"""
        )
          .on("groupId" -> groupId)
          .as(simpleApplication.*)

        result
      }
    }

  def allOrThrow: List[Application] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect FROM application""")
        .as(simpleApplication.*)
        .map(_.anonymousApplication)
    }

  def all(): Future[List[Application]] =
    Future(allOrThrow)

  def createApplication(newApplication: Application): Boolean =
    db.withConnection { implicit connection =>
      val invitedUserJson = toJson(newApplication.invitedUsers.map { case (key, value) =>
        key.toString -> value
      })
      val mandatType =
        newApplication.mandatType.map(dataModels.Application.MandatType.dataModelSerialization)
      SQL"""
          INSERT INTO application (
            id,
            creation_date,
            creator_user_name,
            creator_user_id,
            creator_group_id,
            creator_group_name,
            subject,
            description,
            user_infos,
            invited_users,
            area,
            has_selected_subject,
            category,
            mandat_type,
            mandat_date,
            invited_group_ids
            ) VALUES (
            ${newApplication.id}::uuid,
            ${newApplication.creationDate},
            ${newApplication.creatorUserName},
            ${newApplication.creatorUserId}::uuid,
            ${newApplication.creatorGroupId}::uuid,
            ${newApplication.creatorGroupName},
            ${newApplication.subject},
            ${newApplication.description},
            ${toJson(newApplication.userInfos)},
            $invitedUserJson,
            ${newApplication.area}::uuid,
            ${newApplication.hasSelectedSubject},
            ${newApplication.category},
            $mandatType,
            ${newApplication.mandatDate},
            array[${newApplication.invitedGroupIdsAtCreation}]::uuid[]
          )
      """.executeUpdate() === 1
    }

  def addAnswer(
      applicationId: UUID,
      answer: Answer,
      expertInvited: Boolean = false,
      shouldBeOpened: Boolean = false
  ): Int =
    db.withTransaction { implicit connection =>
      val invitedUserJson = toJson(answer.invitedUsers.map { case (key, value) =>
        key.toString -> value
      })

      val irrelevant =
        if (answer.declareApplicationHasIrrelevant) "irrelevant = true".some else empty[String]
      val expert = if (expertInvited) "expert_invited = true".some else empty[String]
      val reopen = if (shouldBeOpened) "closed = false, closed_date = null".some else empty[String]

      val sql = List(irrelevant, expert, reopen).flatten.mkStringIfNonEmpty(", ", ", ", "")

      SQL(
        s"""UPDATE application SET answers = answers || {answer}::jsonb,
          invited_users = invited_users || {invited_users}::jsonb $sql
          WHERE id = {id}::uuid
       """
      ).on(
        "id" -> applicationId,
        "answer" -> toJson(answer),
        "invited_users" -> invitedUserJson
      ).executeUpdate()
    }

  def close(applicationId: UUID, usefulness: Option[String], closedDate: ZonedDateTime): Boolean =
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

  def reopen(applicationId: UUID): Future[Boolean] =
    Future {
      db.withTransaction { implicit connection =>
        SQL(
          """
          UPDATE application SET closed = false, usefulness = null, closed_date = null
          WHERE id = {id}::uuid
       """
        ).on(
          "id" -> applicationId,
        ).executeUpdate() === 1
      }
    }

  def wipePersonalData(retentionInMonths: Long): Future[List[Application]] =
    Future {
      val before = ZonedDateTime.now().minusMonths(retentionInMonths)
      val applications = db.withConnection { implicit connection =>
        SQL(s"""SELECT $fieldsInSelect
                FROM application
                WHERE personal_data_wiped = false
                AND closed_date < {before};""")
          .on("before" -> before)
          .as(simpleApplication.*)
      }
      applications.flatMap { application =>
        db.withConnection { implicit connection =>
          val wiped = application.withWipedPersonalData
          SQL(s"""UPDATE application
                  SET
                    subject = '',
                    description = '',
                    user_infos = {usagerInfos}::jsonb,
                    answers = {answers}::jsonb,
                    personal_data_wiped = true
                  WHERE id = {id}::uuid
                  RETURNING $fieldsInSelect;""")
            .on(
              "id" -> application.id,
              "usagerInfos" -> toJson(wiped.userInfos),
              "answers" -> toJson(wiped.answers),
            )
            .as(simpleApplication.singleOpt)
        }
      }
    }

}
