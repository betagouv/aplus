package services

import anorm._
import aplus.macros.Macros
import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import helper.{Hash, StringHelper, Time}
import helper.StringHelper.StringOps
import java.security.SecureRandom
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, Organisation, User, UserSession}
import models.dataModels.UserRow
import modules.AppConfig
import org.postgresql.util.PSQLException
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try
import views.editMyGroups.UserInfos

object UserService {

  def toStarTsquery(searchQuery: String): String =
    StringHelper
      .commonStringInputNormalization(searchQuery)
      .replace(' ', '+')
      .replace('(', '+')
      .replace(')', '+')
      .replace(':', '+')
      .replace('@', '+') // emails are considered single tokens
      .replace('.', '+') + ":*"

}

@Singleton
class UserService @Inject() (
    config: AppConfig,
    val db: Database,
    val dependencies: ServicesDependencies
) extends SqlHelpers {
  import dependencies.databaseExecutionContext

  private val (simpleUser, tableFields) = Macros.parserWithFields[UserRow](
    "id",
    "key",
    "first_name",
    "last_name",
    "name",
    "qualite",
    "email",
    "helper",
    "instructor",
    "admin",
    "areas",
    "creation_date",
    "commune_code",
    "group_admin",
    "disabled",
    "expert",
    "group_ids",
    "cgu_acceptation_date",
    "newsletter_acceptation_date",
    "first_login_date",
    "phone_number",
    "observable_organisation_ids",
    "managing_organisation_ids",
    "managing_area_ids",
    "shared_account",
    "internal_support_comment",
    "password_activated"
  )

  private val qualifiedUserParser = anorm.Macro.parser[UserRow](
    "user.id",
    "user.key",
    "user.first_name",
    "user.last_name",
    "user.name",
    "user.qualite",
    "user.email",
    "user.helper",
    "user.instructor",
    "user.admin",
    "user.areas",
    "user.creation_date",
    "user.commune_code",
    "user.group_admin",
    "user.disabled",
    "user.expert",
    "user.group_ids",
    "user.cgu_acceptation_date",
    "user.newsletter_acceptation_date",
    "user.first_login_date",
    "user.phone_number",
    "user.observable_organisation_ids",
    "user.managing_organisation_ids",
    "user.managing_area_ids",
    "user.shared_account",
    "user.internal_support_comment",
    "user.password_activated"
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  def allNoNameNoEmail: IO[List[User]] =
    IO.blocking {
      db.withConnection { implicit connection =>
        SQL(s"""SELECT $fieldsInSelect, '' as name, '' as email, '' as qualite FROM "user"""")
          .as(simpleUser.*)
      }.map(_.toUser)
    }

  def allOrThrow: List[User] =
    db.withConnection(implicit connection =>
      SQL(s"""SELECT $fieldsInSelect FROM "user"""").as(simpleUser.*)
    ).map(_.toUser)

  def all: Future[List[User]] =
    Future(allOrThrow)

  def allNotDisabled: Future[List[User]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""SELECT $fieldsInSelect FROM "user" WHERE NOT disabled""").as(simpleUser.*)
      }.map(_.toUser)
    }

  def allExperts: Future[List[User]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""SELECT $fieldsInSelect FROM "user" WHERE expert = true AND disabled = false""")
          .as(simpleUser.*)
      }.map(_.toUser)
    }

  // Note: this is deprecated, should check via the UserGroup
  def byAreaIds(areaIds: List[UUID]): List[User] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect FROM "user" WHERE ARRAY[{areaIds}]::uuid[] && areas""")
        .on("areaIds" -> areaIds.distinct)
        .as(simpleUser.*)
    }.map(_.toUser)

  def allDBOnlybyArea(areaId: UUID): List[User] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect FROM "user" WHERE areas @> ARRAY[{areaId}]::uuid[]""")
        .on("areaId" -> areaId)
        .as(simpleUser.*)
    }.map(_.toUser)

  def byGroupIdsFuture(ids: List[UUID], includeDisabled: Boolean = false): Future[List[User]] =
    Future(byGroupIds(ids, includeDisabled))

  def byGroupIds(ids: List[UUID], includeDisabled: Boolean = false): List[User] =
    db.withConnection { implicit connection =>
      val disabledSQL: String = if (includeDisabled) {
        ""
      } else {
        "AND disabled = false"
      }
      SQL(s"""SELECT $fieldsInSelect
              FROM "user"
              WHERE ARRAY[{ids}]::uuid[] && group_ids $disabledSQL""")
        .on("ids" -> ids.distinct)
        .as(simpleUser.*)
    }.map(_.toUser)

  def byId(id: UUID, includeDisabled: Boolean = false): Option[User] = {
    val results = byIds(List(id), includeDisabled)
    assert(results.length <= 1)
    results.headOption
  }

  def byIds(userIds: List[UUID], includeDisabled: Boolean = false): List[User] =
    db.withConnection { implicit connection =>
      val ids = userIds.distinct
      val disabledSQL: String = if (includeDisabled) {
        ""
      } else {
        "AND disabled = false"
      }
      SQL(s"""SELECT $fieldsInSelect
              FROM "user"
              WHERE ARRAY[{ids}]::uuid[] @> ARRAY[id]::uuid[] $disabledSQL""")
        .on("ids" -> ids)
        .as(simpleUser.*)
    }.map(_.toUser)

  def byIdsFuture(ids: List[UUID], includeDisabled: Boolean = false): Future[List[User]] =
    Future(byIds(ids, includeDisabled))

  def byKey(key: String): Option[User] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect FROM "user" WHERE key = {key} AND disabled = false""")
        .on("key" -> key)
        .as(simpleUser.singleOpt)
    }.map(_.toUser)

  private def userByEmail(email: String, includeDisabled: Boolean): Connection => Option[User] = {
    implicit connection =>
      val disabledSQL: String = if (includeDisabled) {
        ""
      } else {
        " AND disabled = false"
      }
      SQL(s"""SELECT $fieldsInSelect
              FROM "user"
              WHERE lower(email) = {email}
              $disabledSQL""")
        .on("email" -> email.toLowerCase)
        .as(simpleUser.singleOpt)
        .map(_.toUser)
  }

  def byEmail(email: String, includeDisabled: Boolean = false): Option[User] =
    db.withConnection(userByEmail(email, includeDisabled))

  def byEmailFuture(email: String, includeDisabled: Boolean = false): Future[Option[User]] = Future(
    byEmail(email, includeDisabled)
  )

  def byEmailEither(
      email: String,
      includeDisabled: Boolean = false
  ): Future[Either[Error, Option[User]]] =
    withDbConnection(
      EventType.UserNotFound,
      "Impossible de trouver l'utilisateur par email " +
        s"(recherche dans les utilisateurs désactivés : $includeDisabled)",
      email.some
    )(userByEmail(email, includeDisabled))

  def byEmails(emails: List[String]): List[User] = {
    val lowerCaseEmails = emails.map(_.toLowerCase).distinct
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect
              FROM "user"
              WHERE ARRAY[{emails}]::text[] @> ARRAY[lower(email)]::text[]""")
        .on("emails" -> lowerCaseEmails)
        .as(simpleUser.*)
    }.map(_.toUser)
  }

  def byEmailsFuture(emails: List[String]): Future[Either[Error, List[User]]] =
    Future(byEmails(emails).asRight)

  def usersOrganisations(
      userIds: List[UUID]
  ): Future[Either[Error, Map[UUID, List[Organisation.Id]]]] =
    Future(
      Try {
        val ids = userIds.distinct
        db.withConnection { implicit connection =>
          SQL(s"""SELECT u.id as user_id, g.organisation
                    FROM "user" u, UNNEST(u.group_ids) as gid
                    JOIN user_group g ON g.id = gid
                    WHERE g.organisation IS NOT NULL
                    AND ARRAY[{ids}]::uuid[] @> ARRAY[u.id]::uuid[]
                 """)
            .on("ids" -> ids)
            .as((SqlParser.get[UUID]("user_id") ~ SqlParser.get[String]("organisation")).*)
            .map(SqlParser.flatten)
            .groupBy { case (id, _) => id }
            .view
            .mapValues(_.map { case (_, id) => Organisation.Id(id) })
            .toMap
        }
      }.toEither.left
        .map(e =>
          Error.SqlException(
            EventType.UsersQueryError,
            s"Impossible de lister les organismes d'un utilisateur",
            e,
            none
          )
        )
    )

  def isAccountUsed(userId: UUID): Future[Boolean] =
    Future {
      db.withConnection { implicit connection =>
        SQL(
          """
            SELECT
              CASE
                WHEN first_login_date IS NOT NULL
                AND EXISTS (
                  SELECT 1
                  FROM application
                  WHERE creator_user_id = {userId}::uuid
                  OR invited_users ?? {userId}
                ) THEN TRUE
                ELSE FALSE
              END AS result
            FROM
              "user"
            WHERE
              id = {userId}::uuid
           """
        ).on("userId" -> userId)
          .as(SqlParser.scalar[Boolean].single)
      }
    }

  // Note: empty string will return an `Error`
  //
  // The configuration is
  // CREATE TEXT SEARCH CONFIGURATION french_unaccent ( COPY = french );
  // ALTER TEXT SEARCH CONFIGURATION french_unaccent alter mapping for hword, hword_part, word with unaccent, french_stem;
  def search(searchQuery: String, limit: Int): Future[Either[Error, List[User]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val query = UserService.toStarTsquery(searchQuery)
          SQL(s"""SELECT $fieldsInSelect
                  FROM "user"
                  WHERE (
                    to_tsvector('french_unaccent', coalesce(first_name, '')) ||
                    to_tsvector('french_unaccent', coalesce(last_name, '')) ||
                    to_tsvector('french_unaccent', name) ||
                    to_tsvector('french_unaccent', qualite) ||
                    to_tsvector('french_unaccent', translate(email, '@.', '  '))
                  ) @@ to_tsquery('french_unaccent', {query})
                  LIMIT {limit}""")
            .on("query" -> query, "limit" -> limit)
            .as(simpleUser.*)
        }.map(_.toUser)
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SearchUsersError,
            s"Impossible de faire une recherche",
            e,
            s"Recherche '$searchQuery'".some
          )
        )
    )

  def deleteById(userId: UUID): Boolean =
    db.withTransaction { implicit connection =>
      SQL"""DELETE FROM "user" WHERE id = $userId::uuid""".execute()
    }

  def add(users: List[User]): Either[String, Unit] =
    try {
      val result = db.withTransaction { implicit connection =>
        users.foldRight(true) { (user, success) =>
          val row = UserRow.fromUser(user, config.groupsWhichCannotHaveInstructors)
          assert(row.areas.nonEmpty)
          success && SQL"""
        INSERT INTO "user" (id, key, first_name, last_name, name, qualite, email, helper, instructor, admin, areas, creation_date,
                            commune_code, group_admin, group_ids, cgu_acceptation_date, first_login_date, expert, phone_number, shared_account) VALUES (
           ${row.id}::uuid,
           ${Hash.sha256(s"${row.id}${config.appSecret}")},
           ${row.firstName},
           ${row.lastName},
           ${row.name},
           ${row.qualite},
           ${row.email},
           ${row.helper},
           ${row.instructor},
           ${row.admin},
           array[${row.areas}]::uuid[],
           ${row.creationDate},
           ${row.communeCode},
           ${row.groupAdmin},
           array[${row.groupIds}]::uuid[],
           ${row.cguAcceptationDate},
           ${row.firstLoginDate},
           ${row.expert},
           ${row.phoneNumber},
           ${row.sharedAccount})
        """.executeUpdate() === 1
        }
      }
      if (result)
        Right(())
      else
        Left("Aucun utilisateur n'a été ajouté")
    } catch {
      case ex: PSQLException =>
        val EmailErrorPattern =
          """[^()@]+@[^()@.]+\.[^()@]+""".r // This didn't work in that case : """ Detail: Key \(email\)=\(([^()]*)\) already exists."""".r  (don't know why, the regex is correct)
        val errorMessage = EmailErrorPattern.findFirstIn(ex.getServerErrorMessage.toString) match {
          case Some(email) => s"Un utilisateur avec l'adresse $email existe déjà."
          case _           => s"SQL Erreur : ${ex.getServerErrorMessage.toString}"
        }
        Left(errorMessage)
    }

  def addFuture(users: List[User]): Future[Either[String, Unit]] =
    Future(add(users))

  def update(user: User): Future[Boolean] =
    Future(db.withConnection { implicit connection =>
      val row = UserRow.fromUser(user, config.groupsWhichCannotHaveInstructors)
      SQL"""
          UPDATE "user" SET
          first_name = ${row.firstName},
          last_name = ${row.lastName},
          name = ${row.name},
          qualite = ${row.qualite},
          email = ${row.email},
          helper = ${row.helper},
          instructor = ${row.instructor},
          areas = array[${row.areas}]::uuid[],
          commune_code = ${row.communeCode},
          group_admin = ${row.groupAdmin},
          group_ids = array[${row.groupIds}]::uuid[],
          cgu_acceptation_date = ${row.cguAcceptationDate},
          phone_number = ${row.phoneNumber},
          disabled = ${row.disabled},
          observable_organisation_ids = array[${row.observableOrganisationIds}]::varchar[],
          managing_organisation_ids = array[${row.managingOrganisationIds}]::varchar[],
          managing_area_ids = array[${row.managingAreaIds}]::uuid[],
          shared_account = ${row.sharedAccount},
          internal_support_comment = ${row.internalSupportComment}
          WHERE id = ${row.id}::uuid
       """.executeUpdate() === 1
    })

  def validateCGU(userId: UUID): Int =
    db.withConnection { implicit connection =>
      val now = Time.nowParis()
      SQL"""
        UPDATE "user" SET
        cgu_acceptation_date = $now
        WHERE id = $userId::uuid
     """.executeUpdate()
    }

  def acceptNewsletter(userId: UUID): Int =
    db.withConnection { implicit connection =>
      val now = Time.nowParis()
      SQL"""
        UPDATE "user" SET
        newsletter_acceptation_date = $now
        WHERE id = $userId::uuid
     """.executeUpdate()
    }

  def recordLogin(userId: UUID): Int =
    db.withConnection { implicit connection =>
      SQL"""
        UPDATE "user"
        SET
          first_login_date = NOW()
        WHERE
              id = $userId::uuid
          AND first_login_date IS NULL
      """.executeUpdate()
    }

  def editProfile(userId: UUID)(
      firstName: String,
      lastName: String,
      qualite: String,
      phoneNumber: String
  ): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        val normalizedFirstName = firstName.normalized
        val normalizedLastName = lastName.normalized
        val normalizedQualite = qualite.normalized
        val name = User.standardName(firstName, lastName)
        SQL"""
        UPDATE "user" SET
        name = $name,
        first_name = ${normalizedFirstName.capitalizeWords},
        last_name = ${normalizedLastName.capitalizeWords},
        qualite = $normalizedQualite,
        phone_number = $phoneNumber
        WHERE id = $userId::uuid
     """.executeUpdate()
      }
    }

  def addToGroup(userId: UUID, groupId: UUID): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""
         UPDATE "user" SET
          group_ids = group_ids || $groupId::uuid
         WHERE id = $userId::uuid
       """.executeUpdate()
      }
    }

  def removeFromGroup(userId: UUID, groupId: UUID): Future[Either[Error, Unit]] =
    withDbConnection(
      EventType.EditUserError,
      s"Impossible d'ajouter l'utilisateur $userId au groupe $groupId"
    ) { implicit connection =>
      val _ = SQL"""
        UPDATE "user" SET
          group_ids = array_remove(group_ids, $groupId::uuid)
        WHERE id = $userId::uuid
      """.executeUpdate()
    }

  def enable(userId: UUID, groupId: UUID): Future[Either[Error, Unit]] =
    withDbTransaction(
      EventType.EditUserError,
      s"Impossible de réactiver l'utilisateur $userId"
    ) { implicit connection =>
      val user =
        SQL(s"""SELECT $fieldsInSelect
                FROM "user"
                WHERE id = {userId}::uuid""")
          .on("userId" -> userId)
          .as(simpleUser.single)
      if (user.groupIds.contains[UUID](groupId) && user.groupIds.size === 2) {
        // When there are 2 groups, most people actually want to reactivate in one group
        val newGroupIds = groupId :: Nil
        val _ = SQL"""
          UPDATE "user" SET
            disabled = false,
            group_ids = array[${newGroupIds}]::uuid[]
          WHERE id = $userId::uuid
        """.executeUpdate()
      } else {
        val _ = SQL"""
          UPDATE "user" SET
            disabled = false
          WHERE id = $userId::uuid
        """.executeUpdate()
      }
    }

  def disable(userId: UUID): Future[Either[Error, Unit]] =
    withDbConnection(
      EventType.EditUserError,
      s"Impossible de désactiver l'utilisateur $userId"
    ) { implicit connection =>
      val _ = SQL"""
        UPDATE "user" SET
          disabled = true
        WHERE id = $userId::uuid
      """.executeUpdate()
    }

  def activatePassword(userId: UUID): Future[Either[Error, Unit]] =
    withDbConnection(
      EventType.EditUserError,
      s"Impossible d'activer le mot de passe de l'utilisateur $userId"
    ) { implicit connection =>
      val _ = SQL"""
        UPDATE "user" SET
          password_activated = true
        WHERE id = $userId::uuid
      """.executeUpdate()
    }

  def deactivatePassword(userId: UUID): Future[Either[Error, Unit]] =
    withDbConnection(
      EventType.EditUserError,
      s"Impossible de désactiver le mot de passe de l'utilisateur $userId",
    ) { implicit connection =>
      val _ = SQL"""
        UPDATE "user" SET
          password_activated = false
        WHERE id = $userId::uuid
      """.executeUpdate()
    }

  /** Uses `= ANY` instead of `@>` in order to take advantage of indexes. */
  def usersInfos(usersIds: List[UUID]): IO[Either[Error, Map[UUID, UserInfos]]] = {
    val ids = usersIds.distinct

    val creations = IO.blocking {
      db.withConnection { implicit connection =>
        SQL"""
          SELECT creator_user_id, count(creator_user_id) AS count
          FROM application
          WHERE creator_user_id = ANY(array[$ids]::uuid[])
          GROUP BY creator_user_id"""
          .as((SqlParser.get[UUID]("creator_user_id") ~ SqlParser.get[Int]("count")).*)
          .map(SqlParser.flatten)
      }
    }

    val invitations = IO.blocking {
      db.withConnection { implicit connection =>
        SQL"""
          SELECT user_id, count(user_id) AS count
          FROM user_is_invited_on_application
          WHERE user_id = ANY(array[$ids]::uuid[])
          GROUP BY user_id"""
          .as((SqlParser.get[UUID]("user_id") ~ SqlParser.get[Int]("count")).*)
          .map(SqlParser.flatten)
      }
    }

    val participations = IO.blocking {
      db.withConnection { implicit connection =>
        SQL"""
          SELECT
            answer.creator_user_id,
            count(distinct application.id) AS count
          FROM application, answer
          WHERE
            application.id = answer.application_id
          AND
            application.creator_user_id != answer.creator_user_id
          AND
            answer.creator_user_id = ANY(array[$ids]::uuid[])
          AND
            answer_type != 'inviteAsExpert'
          AND
            answer_type != 'inviteThroughGroupPermission'
          GROUP BY answer.creator_user_id"""
          .as((SqlParser.get[UUID]("creator_user_id") ~ SqlParser.get[Int]("count")).*)
          .map(SqlParser.flatten)
      }
    }

    creations
      .both(invitations)
      .both(participations)
      .map { case ((creations, invitations), participations) =>
        val withCreations = creations.foldLeft(Map.empty[UUID, UserInfos]) {
          case (result, (id, count)) =>
            result.updatedWith(id)(
              _.fold(UserInfos(creations = count, 0, 0).some)(_.copy(creations = count).some)
            )
        }
        val withInvitations = invitations.foldLeft(withCreations) { case (result, (id, count)) =>
          result.updatedWith(id)(
            _.fold(UserInfos(0, invitations = count, 0).some)(_.copy(invitations = count).some)
          )
        }
        val withParticipations =
          participations.foldLeft(withInvitations) { case (result, (id, count)) =>
            result.updatedWith(id)(
              _.fold(UserInfos(0, 0, participations = count).some)(
                _.copy(participations = count).some
              )
            )
          }
        withParticipations
      }
      .attempt
      .map(
        _.left.map(error =>
          Error.SqlException(
            EventType.UsersQueryError,
            s"Impossible de calculer l'activité des utilisateurs ${usersIds.mkString(",")}",
            error,
            none
          )
        )
      )
  }

  //
  // User Sessions
  //

  implicit val invitedUsersParser: Column[UserSession.LoginType] =
    Column
      .of[String]
      .mapResult {
        case "magic_link"        => UserSession.LoginType.MagicLink.asRight
        case "insecure_demo_key" => UserSession.LoginType.InsecureDemoKey.asRight
        case "password"          => UserSession.LoginType.Password.asRight
        case unknownType         =>
          SqlMappingError(s"Cannot parse login_type $unknownType").asLeft
      }

  private val userSessionTableFields = List(
    "id",
    "user_id",
    "creation_date",
    "creation_ip_address",
    "last_activity",
    "login_type",
    "expires_at",
    "revoked_at",
    "user_agent",
  )

  private val qualifiedUserSessionParser = anorm.Macro.parser[UserSession](
    "user_session.id",
    "user_session.user_id",
    "user_session.creation_date",
    "creation_ip_address_text",
    "user_session.last_activity",
    "user_session.login_type",
    "user_session.expires_at",
    "user_session.revoked_at",
    "user_session.user_agent",
  )

  // Double the recommended minimum 64 bits of entropy
  private val SESSION_SIZE_BYTES = 16

  private def generateNewSessionId: IO[String] = IO {
    val bytes = Array.ofDim[Byte](SESSION_SIZE_BYTES)
    new SecureRandom().nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }

  private def generateNewUserSession(
      userId: UUID,
      loginType: UserSession.LoginType,
      expiresAt: Instant,
      ipAddress: String,
      userAgent: Option[String],
  ): IO[Either[Error, UserSession]] =
    generateNewSessionId
      .flatMap(sessionId =>
        IO.realTimeInstant.map(now =>
          UserSession(
            id = sessionId,
            userId = userId,
            creationDate = now,
            creationIpAddress = ipAddress,
            lastActivity = now,
            loginType = loginType,
            expiresAt = expiresAt,
            revokedAt = none,
            userAgent = userAgent,
          )
        )
      )
      .attempt
      .map(
        _.left.map(error =>
          Error.SqlException(
            EventType.UserSessionError,
            s"Impossible de générer une nouvelle session pour l'utilisateur ${userId} ($loginType)",
            error,
            none
          )
        )
      )

  private def stringifyLoginType(loginType: UserSession.LoginType): String = loginType match {
    case UserSession.LoginType.MagicLink       => "magic_link"
    case UserSession.LoginType.InsecureDemoKey => "insecure_demo_key"
    case UserSession.LoginType.Password        => "password"
  }

  private def saveUserSession(session: UserSession): IO[Either[Error, UserSession]] =
    IO.blocking {
      val _ = db.withConnection { implicit connection =>
        val userAgent = session.userAgent.map(_.take(2048))
        SQL"""
          INSERT INTO user_session (
            id,
            user_id,
            creation_date,
            creation_ip_address,
            last_activity,
            login_type,
            expires_at,
            user_agent
          ) VALUES (
            ${session.id},
            ${session.userId}::uuid,
            ${session.creationDate},
            ${session.creationIpAddress}::inet,
            ${session.lastActivity},
            ${stringifyLoginType(session.loginType)},
            ${session.expiresAt},
            $userAgent
          )
        """.executeUpdate()
      }
      session
    }.attempt
      .map(
        _.left.map[Error](error =>
          Error.SqlException(
            EventType.UserSessionError,
            s"Impossible de sauvegarder une session utilisateur $session",
            error,
            none
          )
        )
      )

  /** We store the session id as hexadecimals in the db because there is a unique mapping between
    * bytes and hexadecimals. This is not true of base64 strings.
    *
    * OWASP Recommendations on session id length:
    * https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#session-id-length
    */
  def createNewUserSession(
      userId: UUID,
      loginType: UserSession.LoginType,
      expiresAt: Instant,
      ipAddress: String,
      userAgent: Option[String],
  ): EitherT[IO, Error, UserSession] =
    for {
      session <- EitherT(generateNewUserSession(userId, loginType, expiresAt, ipAddress, userAgent))
      _ <- EitherT(saveUserSession(session))
    } yield session

  /** Intended to be used at each logged in call */
  def userWithSessionLoggingActivity(
      userId: UUID,
      sessionId: Option[String]
  ): IO[Either[Error, (Option[User], Option[UserSession])]] =
    (sessionId match {
      case None            => IO.blocking(byId(userId)).map(user => (user, None))
      case Some(sessionId) =>
        IO.realTimeInstant.flatMap(now =>
          IO.blocking {
            db.withTransaction { implicit connection =>
              val _ = SQL"""
                UPDATE user_session
                SET last_activity = ${now}
                WHERE id = ${sessionId}
              """.executeUpdate()

              // This method is called during each user action, therefore,
              // as an optimization, we get all the data in a single query
              val fields =
                tableFields.map(f => s"\"user\".$f").mkString(", ") + ", " +
                  userSessionTableFields.map(f => s"user_session.$f").mkString(", ")
              val result: Option[(Option[UserRow], Option[UserSession])] = SQL(s"""
                SELECT
                  $fields,
                  host(user_session.creation_ip_address)::text AS creation_ip_address_text
                FROM
                  (SELECT * FROM "user" WHERE id = {userId}::uuid) AS "user"
                LEFT JOIN
                  (SELECT * FROM user_session WHERE id = {sessionId}) AS user_session
                ON true
              """)
                .on("userId" -> userId.toString, "sessionId" -> sessionId)
                .as(
                  (qualifiedUserParser.? ~ qualifiedUserSessionParser.?)
                    .map(SqlParser.flatten)
                    .singleOpt
                )
              result match {
                case None                 => (None, None)
                case Some((row, session)) => (row.map(_.toUser), session)
              }
            }
          }
        )
    }).attempt.map(
      _.left.map(error =>
        Error.SqlException(
          EventType.UserSessionError,
          s"Impossible de trouver l'utilisateur ${userId} avec la session ${sessionId}",
          error,
          none
        )
      )
    )

  def revokeUserSession(sessionId: String): IO[Either[Error, Unit]] =
    IO.realTimeInstant.flatMap(now =>
      IO.blocking {
        val _ = db.withConnection { implicit connection =>
          SQL"""
            UPDATE user_session
            SET revoked_at = ${now}
            WHERE id = ${sessionId}
              AND revoked_at is NULL
          """.executeUpdate()
        }
      }.attempt
        .map(
          _.left.map[Error](error =>
            Error.SqlException(
              EventType.UserSessionError,
              s"Impossible de revoquer la session utilisateur $sessionId",
              error,
              none
            )
          )
        )
    )

  def revokeActiveUserSessions(userId: UUID): IO[Either[Error, Int]] =
    IO.blocking {
      db.withConnection { implicit connection =>
        SQL"""
          UPDATE user_session
          SET revoked_at = now()
          WHERE user_id = ${userId}::uuid
          AND expires_at > now()
          AND revoked_at is NULL
        """.executeUpdate()
      }
    }.attempt
      .map(
        _.left.map[Error](error =>
          Error.SqlException(
            EventType.UserSessionError,
            s"Impossible de revoquer les sessions actives de l'utilisateur $userId",
            error,
            none
          )
        )
      )

}
