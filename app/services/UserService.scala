package services

import anorm._
import aplus.macros.Macros
import cats.syntax.all._
import helper.{Hash, StringHelper, Time}
import helper.StringHelper.StringOps
import java.sql.Connection
import java.util.UUID
import javax.inject.Inject
import models.{Error, EventType, Organisation, User}
import models.dataModels.UserRow
import modules.AppConfig
import org.postgresql.util.PSQLException
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try

@javax.inject.Singleton
class UserService @Inject() (
    config: AppConfig,
    db: Database,
    dependencies: ServicesDependencies
) {
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
    "internal_support_comment"
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  def allNoNameNoEmail: Future[List[User]] =
    Future {
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

  def allDBOnlybyArea(areaId: UUID) =
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

  def byEmail(email: String, includeDisabled: Boolean = false): Option[User] =
    db.withConnection { implicit connection =>
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
    }.map(_.toUser)

  def byEmailFuture(email: String, includeDisabled: Boolean = false): Future[Option[User]] = Future(
    byEmail(email, includeDisabled)
  )

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
          val query =
            StringHelper
              .commonStringInputNormalization(searchQuery)
              .replace(' ', '+')
              .replace('@', '+') // emails are considered single tokens
              .replace('.', '+') + ":*"
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

  def validateCGU(userId: UUID) =
    db.withConnection { implicit connection =>
      val now = Time.nowParis()
      SQL"""
        UPDATE "user" SET
        cgu_acceptation_date = $now
        WHERE id = $userId::uuid
     """.executeUpdate()
    }

  def acceptNewsletter(userId: UUID) =
    db.withConnection { implicit connection =>
      val now = Time.nowParis()
      SQL"""
        UPDATE "user" SET
        newsletter_acceptation_date = $now
        WHERE id = $userId::uuid
     """.executeUpdate()
    }

  def recordLogin(userId: UUID) =
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

  def addToGroup(userId: UUID, groupId: UUID) =
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
    executeUserUpdate(
      s"Impossible d'ajouter l'utilisateur $userId au groupe $groupId"
    ) { implicit connection =>
      SQL"""
        UPDATE "user" SET
          group_ids = array_remove(group_ids, $groupId::uuid)
        WHERE id = $userId::uuid
      """.executeUpdate()
    }

  def enable(userId: UUID, groupId: UUID): Future[Either[Error, Unit]] =
    executeUserUpdateTransaction(
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
        SQL"""
          UPDATE "user" SET
            disabled = false,
            group_ids = array[${newGroupIds}]::uuid[]
          WHERE id = $userId::uuid
        """.executeUpdate()
      } else {
        SQL"""
          UPDATE "user" SET
            disabled = false
          WHERE id = $userId::uuid
        """.executeUpdate()
      }
    }

  def disable(userId: UUID): Future[Either[Error, Unit]] =
    executeUserUpdate(
      s"Impossible de désactiver l'utilisateur $userId"
    ) { implicit connection =>
      SQL"""
        UPDATE "user" SET
          disabled = true
        WHERE id = $userId::uuid
      """.executeUpdate()
    }

  private def executeUserUpdate(
      errorMessage: String
  )(inner: Connection => _): Future[Either[Error, Unit]] =
    Future(
      Try(db.withConnection(inner)).toEither
        .map(_ => ())
        .left
        .map(error =>
          Error.SqlException(
            EventType.EditUserError,
            errorMessage,
            error,
            none
          )
        )
    )

  private def executeUserUpdateTransaction(
      errorMessage: String
  )(inner: Connection => _): Future[Either[Error, Unit]] =
    Future(
      Try(db.withTransaction(inner)).toEither
        .map(_ => ())
        .left
        .map(error =>
          Error.SqlException(
            EventType.EditUserError,
            errorMessage,
            error,
            none
          )
        )
    )

}
