package services

import akka.actor.ActorSystem
import anorm._
import aplus.macros.Macros
import cats.data.EitherT
import cats.syntax.all._
import helper.{MiscHelpers, PasswordHasher}
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import models.{Error, EventType, User}
import models.dataModels.{PasswordRecoveryTokenRow, PasswordRow}
import modules.AppConfig
import play.api.db.Database
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

@javax.inject.Singleton
class PasswordService @Inject() (
    actorSystem: ActorSystem,
    config: AppConfig,
    val db: Database,
    val dependencies: ServicesDependencies,
    notificationService: NotificationService,
    userService: UserService,
) extends SqlHelpers {
  import dependencies.databaseExecutionContext

  private val (passwordRow, passwordTableFields) = Macros.parserWithFields[PasswordRow](
    "user_id",
    "password_hash",
    "last_update",
  )

  private val (passwordRecoveryTokenRow, passwordRecoveryTokenTableFields) =
    Macros.parserWithFields[PasswordRecoveryTokenRow](
      "token",
      "user_id",
      "creation_date",
      "expiration_date",
      "ip_address",
      "used",
    )

  private val passwordFieldsInSelect: String =
    passwordTableFields.mkString(", ")

  private val passwordRecoveryTokenFieldsInSelect: String =
    passwordRecoveryTokenTableFields.mkString(", ")

  private def generateRandomToken(): String =
    MiscHelpers.secureRandom.alphanumeric.take(30).mkString

  // TODO: rate limit the recovery emails
  def sendRecoverEmail(email: String, ipAddress: String): Future[Either[Error, Unit]] =
    userService
      .byEmailEither(email, includeDisabled = true)
      .flatMap(
        _.fold(
          e => Future.successful(e.asLeft),
          {
            case None =>
              Future.successful(
                Error
                  .EntityNotFound(
                    EventType.PasswordTokenError,
                    s"L'utilisateur n'existe pas",
                    email.some
                  )
                  .asLeft
              )
            case Some(user) if user.disabled =>
              Future.successful(
                Error
                  .EntityNotFound(
                    EventType.PasswordTokenError,
                    s"L'utilisateur ${user.id} est désactivé " +
                      "et ne peut pas se connecter par mot de passe",
                    user.email.some
                  )
                  .asLeft
              )
            case Some(user) =>
              if (user.passwordActivated)
                withDbTransaction(
                  EventType.PasswordTokenError,
                  "Impossible de créer un token de changement de mot de passe " +
                    s"pour l'utilisateur ${user.id}",
                ) { implicit connection =>
                  val tokens = SQL(
                    s"""SELECT $passwordRecoveryTokenFieldsInSelect,
                               host(ip_address)::TEXT AS ip_address
                        FROM password_recovery_token
                        WHERE user_id = {userId}::uuid
                        AND NOT used
                        AND expiration_date > NOW()"""
                  )
                    .on("userId" -> user.id)
                    .as(passwordRecoveryTokenRow.*)
                  tokens match {
                    case Nil =>
                      val now = Instant.now()
                      val token = PasswordRecoveryTokenRow(
                        userId = user.id,
                        token = generateRandomToken(),
                        creationDate = now,
                        expirationDate = now
                          .plusSeconds(config.passwordRecoveryTokenExpirationInMinutes.toLong * 60),
                        ipAddress = ipAddress,
                        used = false,
                      )
                      SQL"""
                        INSERT INTO password_recovery_token (
                          token,
                          user_id,
                          creation_date,
                          expiration_date,
                          ip_address,
                          used
                        ) VALUES (
                          ${token.token},
                          ${token.userId}::uuid,
                          ${token.creationDate},
                          ${token.expirationDate},
                          ${token.ipAddress}::inet,
                          ${token.used}
                        )
                         """.executeUpdate()
                      val _ = notificationService.newPasswordRecoveryLinkEmail(
                        user.name,
                        user.email,
                        user.timeZone,
                        token.token,
                        token.expirationDate
                      )
                    case nonExpiredTokens =>
                      val lastToken = nonExpiredTokens.sortBy(_.creationDate).reverse.head
                      if (lastToken.creationDate.plusSeconds(30).isBefore(Instant.now())) {
                        val _ = notificationService.newPasswordRecoveryLinkEmail(
                          user.name,
                          user.email,
                          user.timeZone,
                          lastToken.token,
                          lastToken.expirationDate
                        )
                      }
                  }
                }
              else
                Future.successful(
                  Error
                    .RequirementFailed(
                      EventType.PasswordTokenError,
                      s"L'utilisateur ${user.id} ne peut pas utiliser de mot de passe",
                      user.email.some
                    )
                    .asLeft
                )
          }
        )
      )

  def verifyPasswordRecoveryToken(
      token: String
  ): Future[Either[Error, Option[PasswordRecoveryTokenRow]]] =
    withDbConnection(
      EventType.PasswordTokenError,
      "Impossible de vérifier le token de changement de mot de passe",
    ) { implicit connection =>
      import anorm.SqlParser._
      SQL(
        s"""SELECT $passwordRecoveryTokenFieldsInSelect,
                   host(ip_address)::TEXT AS ip_address
            FROM password_recovery_token
            WHERE token = {token}"""
      )
        .on("token" -> token.take(100))
        .as(passwordRecoveryTokenRow.singleOpt)
    }

  def changePasswordFromToken(
      token: String,
      newPassword: Array[Char]
  ): Future[Either[Error, (UUID, String)]] =
    withDbTransactionE(
      EventType.PasswordTokenError,
      "Impossible de changer le mot de passe",
    ) { implicit connection =>
      import anorm.SqlParser._
      val userInfos = SQL(
        s"""SELECT "user".id, "user".email
            FROM password_recovery_token, "user"
            WHERE "user".id = password_recovery_token.user_id
            AND token = {token}
            AND NOT used
            AND expiration_date > NOW()
            AND NOT "user".disabled"""
      )
        .on("token" -> token.take(100))
        .as((get[UUID]("id") ~ get[String]("email")).singleOpt)
      userInfos match {
        case Some(userId ~ userEmail) =>
          PasswordHasher
            .hashAndWipe(newPassword)
            .fold(
              e =>
                Error
                  .MiscException(
                    EventType.PasswordTokenError,
                    s"Impossible de hasher le mot de passe de l'utilisateur $userId",
                    e,
                    userEmail.some
                  )
                  .asLeft,
              hash => {
                val now = Instant.now()
                SQL"""INSERT INTO password (
                        user_id,
                        password_hash,
                        last_update
                      ) VALUES (
                        ${userId}::uuid,
                        ${hash},
                        ${now}
                      )
                      ON CONFLICT (user_id)
                      DO UPDATE SET password_hash = ${hash}, last_update = ${now}
                   """.executeUpdate()
                SQL"""UPDATE password_recovery_token
                      SET used = true
                      WHERE token = ${token}
                   """.executeUpdate()
                (userId, userEmail).asRight
              }
            )
        case _ =>
          Error
            .RequirementFailed(
              EventType.PasswordTokenIncorrect,
              "Le token de changement de mot de passe est invalide ou expiré " +
                "ou l'utilisateur est désactivé",
              none
            )
            .asLeft
      }
    }

  /** Uses a timeout in parallel to render time attack more difficult
    */
  def verifyPassword(email: String, password: Array[Char]): Future[Either[Error, User]] = {
    val sleep = akka.pattern.after(5.seconds)(Future.successful(()))(actorSystem)
    val verification: EitherT[Future, Error, User] = EitherT(
      withDbConnection(
        EventType.PasswordVerificationError,
        "Impossible de vérifier le mot de passe",
      ) { implicit connection =>
        SQL(
          s"""SELECT $passwordFieldsInSelect
              FROM "user", password
              WHERE "user".id = password.user_id
              AND email = {email}
              AND password_activated"""
        )
          .on("email" -> email)
          .as(passwordRow.singleOpt)
      }
    ).flatMap {
      case None =>
        EitherT.leftT(
          Error.EntityNotFound(
            EventType.PasswordIncorrect,
            "Utilisateur ou mot de passe non existant, ou non activé",
            email.some
          )
        )
      case Some(row) =>
        PasswordHasher
          .verifyAndWipe(password, row.passwordHash)
          .fold(
            e =>
              EitherT.leftT(
                Error.MiscException(
                  EventType.PasswordVerificationError,
                  "Impossible de vérifier le mot de passe",
                  e,
                  email.some
                )
              ),
            matches =>
              if (matches)
                EitherT(
                  userService
                    .byEmailEither(email, includeDisabled = true)
                    .map(_.flatMap {
                      case None =>
                        Error
                          .RequirementFailed(
                            EventType.PasswordVerificationError,
                            "Impossible de retrouver l'utilisateur en BDD " +
                              "après une connexion par mot de passe réussie",
                            email.some
                          )
                          .asLeft
                      case Some(user) =>
                        if (user.disabled)
                          Error
                            .RequirementFailed(
                              EventType.PasswordIncorrect,
                              "Tentative de connexion par mot de passe pour un compte désactivé",
                              email.some
                            )
                            .asLeft
                        else
                          user.asRight
                    })
                )
              else
                EitherT.leftT(
                  Error.RequirementFailed(
                    EventType.PasswordIncorrect,
                    "Mot de passe incorrect",
                    email.some
                  )
                )
          )
    }
    sleep.zip(verification.value).map { case (_, result) => result }
  }

}
