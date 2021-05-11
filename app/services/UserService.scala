package services

import anorm._
import cats.syntax.all._
import helper.StringHelper.StringOps
import helper.{Hash, Time, UUIDHelper}
import java.sql.Connection
import java.util.UUID
import javax.inject.Inject
import models.{Error, EventType, User}
import org.postgresql.util.PSQLException
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try

@javax.inject.Singleton
class UserService @Inject() (
    configuration: play.api.Configuration,
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext

  private lazy val cryptoSecret = configuration.underlying.getString("play.http.secret.key ")

  private lazy val groupsWhichCannotHaveInstructors: Set[UUID] =
    configuration
      .get[String]("app.groupsWhichCannotHaveInstructors")
      .split(",")
      .map(_.trim)
      .filterNot(_.isEmpty)
      .flatMap(UUIDHelper.fromString)
      .toSet

  private val simpleUser: RowParser[User] = Macro
    .parser[User](
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
      "phone_number",
      "observable_organisation_ids",
      "shared_account",
      "internal_support_comment"
    )
    .map(a => a.copy(creationDate = a.creationDate.withZoneSameInstant(Time.timeZoneParis)))

  def allNoNameNoEmail: Future[List[User]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""SELECT *, '' as name, '' as email, '' as qualite FROM "user"""").as(simpleUser.*)
      }
    }

  def all: Future[List[User]] =
    Future {
      db.withConnection(implicit connection => SQL("""SELECT * FROM "user"""").as(simpleUser.*))
    }

  def allNotDisabled: Future[List[User]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""SELECT * FROM "user" WHERE NOT disabled""").as(simpleUser.*)
      }
    }

  def allExperts: Future[List[User]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""SELECT * FROM "user" WHERE expert = true AND disabled = false"""
          .as(simpleUser.*)
      }
    }

  // Note: this is deprecated, should check via the UserGroup
  def byAreaIds(areaIds: List[UUID]): List[User] =
    db.withConnection { implicit connection =>
      SQL"""SELECT * FROM "user" WHERE ARRAY[$areaIds]::uuid[] && areas""".as(simpleUser.*)
    }

  def allDBOnlybyArea(areaId: UUID) =
    db.withConnection { implicit connection =>
      SQL("""SELECT * FROM "user" WHERE areas @> ARRAY[{areaId}]::uuid[]""")
        .on("areaId" -> areaId)
        .as(simpleUser.*)
    }

  def byGroupIdsFuture(ids: List[UUID], includeDisabled: Boolean = false): Future[List[User]] =
    Future(byGroupIds(ids, includeDisabled))

  def byGroupIds(ids: List[UUID], includeDisabled: Boolean = false): List[User] =
    db.withConnection { implicit connection =>
      val disabledSQL: String = if (includeDisabled) {
        ""
      } else {
        "AND disabled = false"
      }
      SQL(s"""SELECT * FROM "user" WHERE ARRAY[{ids}]::uuid[] && group_ids $disabledSQL""")
        .on("ids" -> ids)
        .as(simpleUser.*)
    }

  // Note: this function is used in the stats,
  // pseudonymization is possible (removing name, etc.)
  def byGroupIdsAnonymous(ids: List[UUID]): Future[List[User]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""SELECT *, '' as name, '' as email, '' as qualite
            FROM "user"
            WHERE ARRAY[$ids]::uuid[] && group_ids""".as(simpleUser.*)
      }
    }

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
      SQL(s"""SELECT * FROM "user" WHERE ARRAY[{ids}]::uuid[] @> ARRAY[id]::uuid[] $disabledSQL""")
        .on("ids" -> ids)
        .as(simpleUser.*)
    }

  def byIdsFuture(ids: List[UUID], includeDisabled: Boolean = false): Future[List[User]] =
    Future(byIds(ids, includeDisabled))

  def byKey(key: String): Option[User] =
    db.withConnection { implicit connection =>
      SQL("""SELECT * FROM "user" WHERE key = {key} AND disabled = false""")
        .on("key" -> key)
        .as(simpleUser.singleOpt)
    }

  def byEmail(email: String): Option[User] =
    db.withConnection { implicit connection =>
      SQL("""SELECT * FROM "user" WHERE lower(email) = {email} AND disabled = false""")
        .on("email" -> email.toLowerCase)
        .as(simpleUser.singleOpt)
    }

  def byEmailFuture(email: String): Future[Option[User]] = Future(byEmail(email))

  def byEmails(emails: List[String]): List[User] = {
    val lowerCaseEmails = emails.map(_.toLowerCase)
    db.withConnection { implicit connection =>
      SQL"""SELECT * FROM "user" WHERE  ARRAY[$lowerCaseEmails]::text[] @> ARRAY[lower(email)]::text[]"""
        .as(simpleUser.*)
    }
  }

  def byEmailsFuture(emails: List[String]): Future[Either[Error, List[User]]] =
    Future(byEmails(emails).asRight)

  def deleteById(userId: UUID): Boolean =
    db.withTransaction { implicit connection =>
      SQL"""DELETE FROM "user" WHERE id = $userId::uuid""".execute()
    }

  def add(users: List[User]): Either[String, Unit] =
    try {
      val result = db.withTransaction { implicit connection =>
        users.foldRight(true) { (user, success) =>
          assert(user.areas.nonEmpty)
          success && SQL"""
        INSERT INTO "user" (id, key, first_name, last_name, name, qualite, email, helper, instructor, admin, areas, creation_date,
                            commune_code, group_admin, group_ids, cgu_acceptation_date, expert, phone_number, shared_account) VALUES (
           ${user.id}::uuid,
           ${Hash.sha256(s"${user.id}$cryptoSecret")},
           ${user.firstName},
           ${user.lastName},
           ${user.name},
           ${user.qualite},
           ${user.email},
           ${user.helper},
           ${instructorFlag(user)},
           ${user.admin},
           array[${user.areas.distinct}]::uuid[],
           ${user.creationDate},
           ${user.communeCode},
           ${user.groupAdmin},
           array[${user.groupIds.distinct}]::uuid[],
           ${user.cguAcceptationDate},
           ${user.expert},
           ${user.phoneNumber},
           ${user.sharedAccount})
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
      val observableOrganisationIds = user.observableOrganisationIds.map(_.id)
      SQL"""
          UPDATE "user" SET
          first_name = ${user.firstName},
          last_name = ${user.lastName},
          name = ${user.name},
          qualite = ${user.qualite},
          email = ${user.email},
          helper = ${user.helper},
          instructor = ${instructorFlag(user)},
          areas = array[${user.areas.distinct}]::uuid[],
          commune_code = ${user.communeCode},
          group_admin = ${user.groupAdmin},
          group_ids = array[${user.groupIds.distinct}]::uuid[],
          phone_number = ${user.phoneNumber},
          disabled = ${user.disabled},
          observable_organisation_ids = array[${observableOrganisationIds.distinct}]::varchar[],
          shared_account = ${user.sharedAccount},
          internal_support_comment = ${user.internalSupportComment}
          WHERE id = ${user.id}::uuid
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
        val name = s"${normalizedLastName.toUpperCase} ${normalizedFirstName.capitalizeWords}"
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

  def enable(userId: UUID): Future[Either[Error, Unit]] =
    executeUserUpdate(
      s"Impossible de réactiver l'utilisateur $userId"
    ) { implicit connection =>
      SQL"""
        UPDATE "user" SET
          disabled = false
        WHERE id = $userId::uuid
      """.executeUpdate()
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
            error
          )
        )
    )

  private def instructorFlag(user: User): Boolean =
    user.instructor &&
      groupsWhichCannotHaveInstructors.intersect(user.groupIds.toSet).isEmpty

}
