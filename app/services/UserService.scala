package services

import java.util.UUID

import anorm._
import cats.implicits.{catsKernelStdMonoidForString, catsSyntaxOption}
import helper.{Hash, Time}
import javax.inject.Inject
import models.User
import models.formModels.ValidateSubscriptionForm
import org.postgresql.util.PSQLException
import play.api.db.Database

import scala.concurrent.Future

@javax.inject.Singleton
class UserService @Inject() (
    configuration: play.api.Configuration,
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext

  private lazy val cryptoSecret = configuration.underlying.getString("play.http.secret.key ")

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
      "shared_account"
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

  def byIds(ids: List[UUID], includeDisabled: Boolean = false): List[User] =
    db.withConnection { implicit connection =>
      val disabledSQL: String = if (includeDisabled) {
        ""
      } else {
        "AND disabled = false"
      }
      SQL(s"""SELECT * FROM "user" WHERE ARRAY[{ids}]::uuid[] @> ARRAY[id]::uuid[] $disabledSQL""")
        .on("ids" -> ids)
        .as(simpleUser.*)
    }

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

  def byEmails(emails: List[String]): List[User] = {
    val lowerCaseEmails = emails.map(_.toLowerCase)
    db.withConnection { implicit connection =>
      SQL"""SELECT * FROM "user" WHERE  ARRAY[$lowerCaseEmails]::text[] @> ARRAY[lower(email)]::text[]"""
        .as(simpleUser.*)
    }
  }

  def deleteById(userId: UUID): Boolean =
    db.withTransaction { implicit connection =>
      SQL"""DELETE FROM "user" WHERE id = ${userId}::uuid""".execute()
    }

  def add(users: List[User]): Either[String, Unit] =
    try {
      val result = db.withTransaction { implicit connection =>
        users.foldRight(true) { (user, success) =>
          assert(user.areas.nonEmpty)
          success && SQL"""
        INSERT INTO "user" (id, key, first_name, last_name, name, qualite, email, helper, instructor, admin, areas, creation_date,
                            commune_code, group_admin, group_ids, expert, phone_number, shared_account) VALUES (
           ${user.id}::uuid,
           ${Hash.sha256(s"${user.id}$cryptoSecret")},
           ${user.firstName},
           ${user.lastName},
           ${user.name},
           ${user.qualite},
           ${user.email},
           ${user.helper},
           ${user.instructor},
           ${user.admin},
           array[${user.areas.distinct}]::uuid[],
           ${user.creationDate},
           ${user.communeCode},
           ${user.groupAdmin},
           array[${user.groupIds.distinct}]::uuid[],
           ${user.expert},
           ${user.phoneNumber},
           ${user.sharedAccount})
        """.executeUpdate() == 1
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
          instructor = ${user.instructor},
          areas = array[${user.areas.distinct}]::uuid[],
          commune_code = ${user.communeCode},
          group_admin = ${user.groupAdmin},
          group_ids = array[${user.groupIds.distinct}]::uuid[],
          phone_number = ${user.phoneNumber},
          disabled = ${user.disabled},
          observable_organisation_ids = array[${observableOrganisationIds.distinct}]::varchar[],
          shared_account = ${user.sharedAccount}
          WHERE id = ${user.id}::uuid
       """.executeUpdate() == 1
    })

  def validateUser(userId: UUID, form: ValidateSubscriptionForm) =
    db.withConnection { implicit connection =>
      val now = Time.nowParis()
      val resultCGUAcceptation = SQL"""
        UPDATE "user" SET
        first_name = ${form.firstName.map(_.toLowerCase.capitalize).orEmpty},
        last_name = ${form.lastName.map(_.toLowerCase.capitalize).orEmpty},
        cgu_acceptation_date = $now
        WHERE id = $userId::uuid
     """.executeUpdate() == 1
      val resultNewsletterAcceptation = if (form.newsletter) {
        SQL"""
        UPDATE "user" SET
        newsletter_acceptation_date = $now
        WHERE id = $userId::uuid
     """.executeUpdate() == 1
      } else {
        true
      }
      resultCGUAcceptation && resultNewsletterAcceptation
    }

  def acceptCGU(userId: UUID, acceptNewsletter: Boolean) =
    db.withConnection { implicit connection =>
      val now = Time.nowParis()
      val resultCGUAcceptation = SQL"""
        UPDATE "user" SET
        cgu_acceptation_date = ${now}
        WHERE id = ${userId}::uuid
     """.executeUpdate() == 1
      val resultNewsletterAcceptation = if (acceptNewsletter) {
        SQL"""
        UPDATE "user" SET
        newsletter_acceptation_date = ${now}
        WHERE id = ${userId}::uuid
     """.executeUpdate() == 1
      } else {
        true
      }
      resultCGUAcceptation && resultNewsletterAcceptation
    }

}
