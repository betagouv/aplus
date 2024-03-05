package services

import anorm._
import aplus.macros.Macros
import cats.syntax.all._
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import models.{Error, EventType, SignupRequest}
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try

@javax.inject.Singleton
class SignupService @Inject() (
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext

  private val (signupRequestRowParser, tableFields) = Macros.parserWithFields[SignupRequest](
    "id",
    "request_date",
    "email",
    "inviting_user_id"
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  def byId(signupId: UUID): Future[Either[Error, Option[SignupRequest]]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL(s"""SELECT $fieldsInSelect FROM signup_request WHERE id = {signupId}::uuid""")
            .on("signupId" -> signupId)
            .as(signupRequestRowParser.singleOpt)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible de chercher la préinscription ayant l'id $signupId",
            e,
            none
          )
        )
    )

  def byEmail(email: String): Future[Either[Error, Option[SignupRequest]]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL(s"""SELECT $fieldsInSelect
                  FROM signup_request
                  WHERE lower(email) = {email}""")
            .on("email" -> email.toLowerCase)
            .as(signupRequestRowParser.singleOpt)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible de chercher une préinscription par email",
            e,
            s"Email '$email'".some
          )
        )
    )

  def byEmails(emails: List[String]): Future[Either[Error, List[SignupRequest]]] =
    Future(
      Try {
        val lowerCaseEmails = emails.map(_.toLowerCase)
        db.withConnection { implicit connection =>
          SQL(s"""SELECT $fieldsInSelect FROM signup_request
                  WHERE ARRAY[{emails}]::text[] @> ARRAY[lower(email)]::text[]""")
            .on("emails" -> lowerCaseEmails)
            .as(signupRequestRowParser.*)
        }
      }.toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible de chercher les préinscriptions avec certains emails",
            e,
            s"Emails '$emails'".some
          )
        )
    )

  def allOrThrow: List[(SignupRequest, Option[UUID])] =
    db.withTransaction { implicit connection =>
      val fields = tableFields.map(field => s"signup_request.$field").mkString(", ")
      SQL(s"""SELECT
              $fields,
              "user".id AS signedup_user_id
              FROM signup_request
              LEFT JOIN "user" ON LOWER("user".email) = LOWER(signup_request.email)
           """)
        .as((signupRequestRowParser ~ SqlParser.get[Option[UUID]]("signedup_user_id")).*)
        .map { case a ~ b => (a, b) }
    }

  def allAfter(afterDate: Instant): Future[Either[Error, List[(SignupRequest, Option[UUID])]]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          val fields = tableFields.map(field => s"signup_request.$field").mkString(", ")
          SQL(s"""SELECT
                    $fields,
                    "user".id AS signedup_user_id
                  FROM signup_request
                  LEFT JOIN "user" ON LOWER("user".email) = LOWER(signup_request.email)
                  WHERE request_date >= {afterDate}""")
            .on("afterDate" -> afterDate)
            .as((signupRequestRowParser ~ SqlParser.get[Option[UUID]]("signedup_user_id")).*)
            .map { case a ~ b => (a, b) }
        }
      ).toEither.left.map { error =>
        Error.SqlException(
          EventType.SignupsError,
          s"Impossible de chercher les préinscriptions après $afterDate",
          error,
          none
        )
      }
    )

  def addSignupRequest(request: SignupRequest): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL"""
          INSERT INTO signup_request (
            id,
            request_date,
            email,
            inviting_user_id
          ) VALUES (
            ${request.id}::uuid,
            ${request.requestDate},
            ${request.email},
            ${request.invitingUserId}::uuid
          )
        """.executeUpdate()
        }
      ).toEither.left
        .map { error =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible d'ajouter la préinscription",
            error,
            s"Préinscription '$request'".some
          )
        }
        .flatMap { nrOfRows =>
          if (nrOfRows === 1) ().asRight
          else
            Error
              .Database(
                EventType.SignupsError,
                s"Nombre incorrect de lignes ($nrOfRows) lors de l'ajout de la préinscription",
                s"Préinscription '$request'".some
              )
              .asLeft
        }
    )

}
