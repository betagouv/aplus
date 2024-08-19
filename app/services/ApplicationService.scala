package services

import anorm._
import aplus.macros.Macros
import cats.effect.IO
import cats.syntax.all._
import helper.StringHelper.StringListOps
import java.sql.Connection
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import models.{dataModels, Answer, Application, Authorization, Error, EventType}
import models.Application.SeenByUser
import models.Authorization.UserRights
import models.dataModels.{AnswerRow, ApplicationRow, InvitedUsers, UserInfos}
import play.api.db.Database
import scala.Option.empty
import scala.concurrent.Future

@javax.inject.Singleton
class ApplicationService @Inject() (
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext

  private val (simpleApplication, applicationTableFields) =
    Macros.parserWithFields[ApplicationRow](
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
      "personal_data_wiped",
    )

  private val fieldsInApplicationSelect: String = applicationTableFields.mkString(", ")

  private val (simpleAnswer, answerTableFields) =
    Macros.parserWithFields[AnswerRow](
      "id",
      "application_id",
      "answer_order",
      "creation_date",
      "answer_type",
      "message",
      "user_infos",
      "creator_user_id",
      "creator_user_name",
      "invited_users",
      "invited_group_ids",
      "visible_by_helpers",
      "declare_application_is_irrelevant",
    )

  private val fieldsInAnswerSelect: String = answerTableFields.mkString(", ")

  private def setSeenByUsers(id: UUID, seenByUsers: List[SeenByUser])(implicit
      connection: Connection
  ): Option[Application] = {
    val row = SQL(s"""
      UPDATE application
      SET seen_by_user_ids = {seenByUsers}::jsonb
      WHERE id = {id}::uuid
      RETURNING $fieldsInApplicationSelect
    """)
      .on(
        "id" -> id,
        "seenByUsers" -> dataModels.SeenByUsers(seenByUsers)
      )
      .as(simpleApplication.singleOpt)
    val answersRows = answersForApplications(row.toList)
    row.map(_.toApplication(answersRows))
  }

  private def byId(id: UUID)(implicit connection: Connection): Option[Application] = {
    val row = SQL(s"""
      SELECT $fieldsInApplicationSelect
      FROM application
      WHERE id = {id}::uuid
    """)
      .on("id" -> id)
      .as(simpleApplication.singleOpt)
    val answersRows = answersForApplications(row.toList)
    row.map(_.toApplication(answersRows))
  }

  private def byIdBlocking(id: UUID, userId: UUID, rights: UserRights): Either[Error, Application] =
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

  def byId(id: UUID, userId: UUID, rights: UserRights): Future[Either[Error, Application]] =
    Future(byIdBlocking(id, userId, rights))

  def byIdIO(id: UUID, userId: UUID, rights: UserRights): IO[Either[Error, Application]] =
    IO.blocking(byIdBlocking(id, userId, rights))

  def openAndOlderThan(numberOfDays: Int): List[Application] =
    db.withConnection { implicit connection =>
      val rows = SQL(s"""
        SELECT $fieldsInApplicationSelect
        FROM application
        WHERE closed = false
        AND age(creation_date) > {days}::interval
        AND expert_invited = false
      """)
        .on("days" -> s"$numberOfDays days")
        .as(simpleApplication.*)
      val answersRows = answersForApplications(rows)
      rows.map(_.toApplication(answersRows))
    }

  def allOpenOrRecentForUserId(
      userId: UUID,
      anonymous: Boolean,
      referenceDate: ZonedDateTime
  ): List[Application] =
    db.withConnection { implicit connection =>
      val rows = SQL(s"""
        SELECT $fieldsInApplicationSelect
        FROM application
        WHERE
          (creator_user_id = {userId}::uuid OR invited_users ?? {userId})
        AND
          (closed = FALSE OR DATE_PART('day', {referenceDate} - closed_date) < 30)
        ORDER BY creation_date DESC
      """)
        .on("userId" -> userId, "referenceDate" -> referenceDate)
        .as(simpleApplication.*)
      val answersRows = answersForApplications(rows)
      val applications = rows.map(_.toApplication(answersRows))
      if (anonymous) {
        applications.map(_.anonymousApplication)
      } else {
        applications
      }
    }

  def allOpenAndCreatedByUserIdAnonymous(userId: UUID): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        val rows = SQL(s"""
          SELECT $fieldsInApplicationSelect
          FROM application
          WHERE creator_user_id = {userId}::uuid
          AND closed = false
          ORDER BY creation_date DESC
        """)
          .on("userId" -> userId)
          .as(simpleApplication.*)
        val answersRows = answersForApplications(rows)
        val applications = rows.map(_.toApplication(answersRows))
        applications.map(_.anonymousApplication)
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
        val rows = SQL(s"""
          SELECT $fieldsInApplicationSelect
          FROM application
          WHERE
            (
              ARRAY[{userIds}]::uuid[] @> ARRAY[creator_user_id]::uuid[]
            OR
              ARRAY(select jsonb_object_keys(invited_users))::uuid[] && ARRAY[{userIds}]::uuid[]
            )
          $additionalFilter
          ORDER BY creation_date DESC
        """)
          .on("userIds" -> userIds)
          .as(simpleApplication.*)
        val answersRows = answersForApplications(rows)
        val applications = rows.map(_.toApplication(answersRows))
        if (anonymous)
          applications.map(_.anonymousApplication)
        else
          applications
      }
    }

  def allByArea(areaId: UUID, anonymous: Boolean): List[Application] =
    db.withConnection { implicit connection =>
      val rows = SQL(s"""
        SELECT $fieldsInApplicationSelect
        FROM application
        WHERE area = {areaId}::uuid
        ORDER BY creation_date DESC
      """)
        .on("areaId" -> areaId)
        .as(simpleApplication.*)
      val answersRows = answersForApplications(rows)
      val applications = rows.map(_.toApplication(answersRows))
      if (anonymous) {
        applications.map(_.anonymousApplication)
      } else {
        applications
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
        val rows = SQL(s"""
          SELECT $fieldsInApplicationSelect
          FROM application
          WHERE ARRAY[{areaIds}]::uuid[] @> ARRAY[area]::uuid[]
          $additionalFilter
          ORDER BY creation_date DESC
        """)
          .on("areaIds" -> areaIds)
          .as(simpleApplication.*)
        val answersRows = answersForApplications(rows)
        val applications = rows.map(_.toApplication(answersRows))
        if (anonymous) {
          applications.map(_.anonymousApplication)
        } else {
          applications
        }
      }
    }

  def byInvitedGroupIdAndOpen(groupId: UUID): Future[List[Application]] =
    Future {
      db.withConnection { implicit connection =>
        val rows = SQL(s"""
          SELECT $fieldsInApplicationSelect
          FROM application
          WHERE
            application.id IN (
              SELECT
                application.id
              FROM
                application
              WHERE
                array[{groupId}::uuid] && application.invited_group_ids
              UNION
              SELECT
                answer.application_id
              FROM
                answer
              WHERE
                array[{groupId}::uuid] && answer.invited_group_ids
            )
          AND
            closed_date IS NULL
          ORDER BY creation_date DESC
        """)
          .on("groupId" -> groupId)
          .as(simpleApplication.*)
        val answersRows = answersForApplications(rows)
        val applications = rows.map(_.toApplication(answersRows))
        applications
      }
    }

  def lastInternalIdOrThrow: Option[Int] =
    db.withConnection { implicit connection =>
      SQL"""SELECT max(internal_id) FROM application"""
        .as(SqlParser.scalar[Int].singleOpt)
    }

  def byInternalIdBetweenOrThrow(firstId: Int, lastIdExclusive: Int): List[Application] =
    db.withConnection { implicit connection =>
      val rows = SQL(s"""
        SELECT $fieldsInApplicationSelect
        FROM application
        WHERE
          internal_id >= {firstId}
        AND
          internal_id < {lastId}
      """)
        .on("firstId" -> firstId, "lastId" -> lastIdExclusive)
        .as(simpleApplication.*)
      val answersRows = answersForApplications(rows)
      val applications = rows.map(_.toApplication(answersRows))
      applications
    }

  def createApplication(newApplication: Application): Boolean =
    db.withConnection { implicit connection =>
      val row = ApplicationRow.fromApplication(newApplication)
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
            ${row.id}::uuid,
            ${row.creationDate},
            ${row.creatorUserName},
            ${row.creatorUserId}::uuid,
            ${row.creatorGroupId}::uuid,
            ${row.creatorGroupName},
            ${row.subject},
            ${row.description},
            ${row.userInfos},
            ${row.invitedUsers},
            ${row.area}::uuid,
            ${row.hasSelectedSubject},
            ${row.category},
            ${row.mandatType},
            ${row.mandatDate},
            array[${row.invitedGroupIds}]::uuid[]
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
      val lastOrder: Option[Int] = SQL"""
        SELECT MAX(answer_order)
        FROM answer
        WHERE application_id = $applicationId::uuid
      """.as(SqlParser.scalar[Int].singleOpt)
      val answerOrder: Int = lastOrder.map(_ + 1).getOrElse(1)
      val answerRow = AnswerRow.fromAnswer(answer, answerOrder)
      SQL"""
        INSERT INTO answer (
          id,
          application_id,
          answer_order,
          creation_date,
          answer_type,
          message,
          user_infos,
          creator_user_id,
          creator_user_name,
          invited_users,
          invited_group_ids,
          visible_by_helpers,
          declare_application_is_irrelevant
        ) VALUES (
          ${answerRow.id}::uuid,
          ${answerRow.applicationId}::uuid,
          ${answerRow.answerOrder},
          ${answerRow.creationDate},
          ${answerRow.answerType},
          ${answerRow.message},
          ${answerRow.userInfos},
          ${answerRow.creatorUserId}::uuid,
          ${answerRow.creatorUserName},
          ${answerRow.invitedUsers},
          array[${answerRow.invitedGroupIds}]::uuid[],
          ${answerRow.visibleByHelpers},
          ${answerRow.declareApplicationIsIrrelevant}
        )
      """.executeUpdate()

      val irrelevant =
        if (answer.declareApplicationHasIrrelevant) "irrelevant = true".some else empty[String]
      val expert = if (expertInvited) "expert_invited = true".some else empty[String]
      val reopen = if (shouldBeOpened) "closed = false, closed_date = null".some else empty[String]

      val sql = List(irrelevant, expert, reopen).flatten.mkStringIfNonEmpty(", ", ", ", "")

      SQL(s"""
        UPDATE application
        SET
          invited_users = invited_users || {invited_users}::jsonb
          $sql
        WHERE id = {id}::uuid
      """)
        .on(
          "id" -> applicationId,
          "invited_users" -> InvitedUsers(answer.invitedUsers)
        )
        .executeUpdate()
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

  def wipePersonalData(retentionInMonths: Long): Future[List[ApplicationRow]] =
    Future {
      val before = ZonedDateTime.now().minusMonths(retentionInMonths)
      val applications = db.withConnection { implicit connection =>
        val rows = SQL(s"""
          SELECT $fieldsInApplicationSelect
          FROM application
          WHERE personal_data_wiped = false
          AND closed_date < {before}
        """)
          .on("before" -> before)
          .as(simpleApplication.*)
        val answersRows = answersForApplications(rows)
        rows.map(_.toApplication(answersRows))
      }
      applications.flatMap { application =>
        db.withConnection { implicit connection =>
          val wiped = application.withWipedPersonalData
          wiped.answers.zipWithIndex.foreach { case (answer, index) =>
            val answerOrder = index + 1
            SQL(s"""
              UPDATE answer
              SET
                message = '',
                user_infos = {usagerInfos}::jsonb
              WHERE
                application_id = {applicationId}::uuid
                AND answer_order = {answerOrder}
            """)
              .on(
                "applicationId" -> application.id,
                "answerOrder" -> answerOrder,
                "usagerInfos" -> UserInfos(answer.userInfos.getOrElse(Map.empty)),
              )
          }
          SQL(s"""
            UPDATE application
            SET
              subject = '',
              description = '',
              user_infos = {usagerInfos}::jsonb,
              personal_data_wiped = true
            WHERE id = {id}::uuid
            RETURNING $fieldsInApplicationSelect
          """)
            .on(
              "id" -> application.id,
              "usagerInfos" -> UserInfos(wiped.userInfos),
            )
            .as(simpleApplication.singleOpt)
        }
      }
    }

  private def answersForApplications(
      rows: List[ApplicationRow]
  )(implicit connection: Connection): List[AnswerRow] = {
    val ids = rows.map(_.id)
    SQL(s"""
      SELECT $fieldsInAnswerSelect
      FROM answer
      WHERE application_id = ANY(array[{ids}]::uuid[])
    """)
      .on("ids" -> ids)
      .as(simpleAnswer.*)
  }

}
