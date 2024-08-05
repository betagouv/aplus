package services

import anorm._
import cats.syntax.all._
import helper.Time
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, LoginToken}
import play.api.UnexpectedException
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try

object TokenService {

  /** Maps to the actual table row. It can be transformed to and from the actual model `LoginToken`.
    *
    * We use a case class here instead of rarely used anorm combinators to improve readability and
    * maintainability (but with more code).
    *
    * Note that `Instant` is the correct type to map to a timestamp, because Postgres does not store
    * timezones.
    */
  case class LoginTokenRow(
      token: String,
      userId: Option[UUID],
      creationDate: Instant,
      expirationDate: Instant,
      ipAddress: String,
      signupId: Option[UUID]
  ) {

    def toModel: Option[LoginToken] =
      (userId, signupId) match {
        case (Some(userId), _) =>
          LoginToken(
            token = token,
            origin = LoginToken.Origin.User(userId),
            creationDate = creationDate.atZone(Time.timeZoneParis),
            expirationDate = expirationDate.atZone(Time.timeZoneParis),
            ipAddress = ipAddress
          ).some
        case (None, Some(signupId)) =>
          LoginToken(
            token = token,
            origin = LoginToken.Origin.Signup(signupId),
            creationDate = creationDate.atZone(Time.timeZoneParis),
            expirationDate = expirationDate.atZone(Time.timeZoneParis),
            ipAddress = ipAddress
          ).some
        case _ => none
      }

  }

  object LoginTokenRow {

    def fromModel(token: LoginToken): LoginTokenRow = {
      val (userId, signupId) = token.origin match {
        case LoginToken.Origin.User(userId)     => (userId.some, none)
        case LoginToken.Origin.Signup(signupId) => (none, signupId.some)
      }
      LoginTokenRow(
        token = token.token,
        userId = userId,
        creationDate = token.creationDate.toInstant,
        expirationDate = token.expirationDate.toInstant,
        ipAddress = token.ipAddress,
        signupId = signupId
      )
    }

  }

}

@Singleton
class TokenService @Inject() (
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext
  import TokenService.LoginTokenRow

  private val simpleLoginToken: RowParser[LoginTokenRow] = Macro.parser[LoginTokenRow](
    "token",
    "user_id",
    "creation_date",
    "expiration_date",
    "ip_address",
    "signup_id"
  )

  def create(loginToken: LoginToken): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val token = LoginTokenRow.fromModel(loginToken)
          SQL"""
            INSERT INTO login_token (
              token,
              user_id,
              creation_date,
              expiration_date,
              ip_address,
              signup_id
            ) VALUES (
              ${token.token},
              ${token.userId}::uuid,
              ${token.creationDate},
              ${token.expirationDate},
              ${token.ipAddress}::inet,
              ${token.signupId}::uuid
            )
             """.executeUpdate()
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.TokenError,
            s"Impossible de créer un token",
            e,
            none
          )
        )
        .flatMap(nrOfRows =>
          if (nrOfRows === 1) ().asRight
          else
            Error
              .Database(
                EventType.TokenError,
                s"Nombre de lignes ajoutées incorrect ($nrOfRows) lors de la création d'un token",
                none
              )
              .asLeft
        )
    )

  def byTokenThenDelete(rawToken: String): Future[Either[Error, Option[LoginToken]]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          val tokenOpt =
            SQL"""
              SELECT
                token,
                user_id,
                creation_date,
                expiration_date,
                host(ip_address) AS ip_address,
                signup_id
              FROM login_token
              WHERE token = $rawToken"""
              .as(simpleLoginToken.singleOpt)
          // To be sure the token is used only once, we remove it from the database
          tokenOpt match {
            case None => none
            case Some(row) =>
              val deletion =
                SQL"""DELETE FROM login_token WHERE token = $rawToken""".executeUpdate()
              if (deletion === 1) {
                row.toModel
              } else {
                // Kills the transaction, it will be catched by the Try then recovered
                throw UnexpectedException(
                  Some(
                    s"Impossible de supprimer le token (transaction aborted for byTokenThenDelete)"
                  )
                )
              }
          }
        }
      ).toEither.left
        .map {
          case UnexpectedException(Some(message), _) if message.contains("transaction aborted") =>
            Error.Authentication(
              EventType.TokenDoubleUsage,
              message,
              s"Token '$rawToken'".some
            )
          case e =>
            Error.SqlException(
              EventType.TokenError,
              s"Impossible de retrouver un token",
              e,
              none
            )
        }
    )

}
