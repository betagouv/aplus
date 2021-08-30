package services

import anorm._
import cats.syntax.all._
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import models.{Error, EventType, SignupRequest, User}
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try

@javax.inject.Singleton
class SignupService @Inject() (
    configuration: play.api.Configuration,
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext

  private val signupRequestRowParser: RowParser[SignupRequest] = Macro
    .parser[SignupRequest](
      "id",
      "request_date",
      "email",
      "inviting_user_id"
    )

  def byId(signupId: UUID): Future[Either[Error, Option[SignupRequest]]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL"""SELECT * FROM signup_request WHERE id = $signupId::uuid"""
            .as(signupRequestRowParser.singleOpt)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible de chercher la préinscription ayant l'id $signupId",
            e
          )
        )
    )

  def byEmail(email: String): Future[Either[Error, Option[SignupRequest]]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL"""SELECT * FROM signup_request WHERE lower(email) = ${email.toLowerCase}"""
            .as(signupRequestRowParser.singleOpt)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible de chercher la préinscription de $email",
            e
          )
        )
    )

  def byEmails(emails: List[String]): Future[Either[Error, List[SignupRequest]]] =
    Future(
      Try {
        val lowerCaseEmails = emails.map(_.toLowerCase)
        db.withConnection { implicit connection =>
          SQL"""SELECT * FROM signup_request
                WHERE ARRAY[$lowerCaseEmails]::text[] @> ARRAY[lower(email)]::text[]"""
            .as(signupRequestRowParser.*)
        }
      }.toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible de chercher les préinscriptions avec les emails $emails",
            e
          )
        )
    )

  def allAfter(afterDate: Instant): Future[Either[Error, List[(SignupRequest, Option[UUID])]]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL"""SELECT
                  signup_request.*,
                  "user".id AS signedup_user_id
                FROM signup_request
                LEFT JOIN "user" ON LOWER("user".email) = LOWER(signup_request.email)
                WHERE request_date >= $afterDate"""
            .as((signupRequestRowParser ~ SqlParser.get[Option[UUID]]("signedup_user_id")).*)
            .map { case a ~ b => (a, b) }
        }
      ).toEither.left.map { error =>
        Error.SqlException(
          EventType.SignupsError,
          s"Impossible de chercher les préinscriptions après $afterDate",
          error
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
            s"Impossible d'ajouter la préinscription $request",
            error
          )
        }
        .flatMap { nrOfRows =>
          if (nrOfRows === 1) ().asRight
          else
            Error
              .Database(
                EventType.SignupsError,
                s"Nombre incorrect de lignes ($nrOfRows) lors de l'ajout de la préinscription $request"
              )
              .asLeft
        }
    )

}
