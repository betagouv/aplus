package services

import java.util.UUID

import javax.inject.Inject
import anorm._
import models.User
import play.api.db.Database
import extentions.{Hash, Time}
import play.api.libs.json.Json
import anorm.JodaParameterMetaData._
import org.postgresql.util.PSQLException

@javax.inject.Singleton
class UserService @Inject()(configuration: play.api.Configuration, db: Database) {
  import extentions.Anorm._
  
  private lazy val cryptoSecret = configuration.underlying.getString("play.http.secret.key ")

  private val simpleUser: RowParser[User] = Macro.parser[User](
    "id",
    "key",
    "name",
    "qualite",
    "email",
    "helper",
    "instructor",
    "admin",
    "areas",
    "creation_date",
    "has_accepted_charte",
    "commune_code",
    "group_admin",
    "disabled",
    "expert",
    "group_ids",
    "delegations",
    "cgu_acceptation_date",
    "newsletter_acceptation_date",
    "phone_number"
  ).map(a => a.copy(creationDate = a.creationDate.withZone(Time.dateTimeZone)))

  def all = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user"""").as(simpleUser.*)
  }

  def allDBOnlybyArea(areaId: UUID) = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE areas @> ARRAY[{areaId}]::uuid[]""").on('areaId -> areaId).as(simpleUser.*)
  }

  def byArea(areaId: UUID): List[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE areas @> ARRAY[{areaId}]::uuid[]""").on('areaId -> areaId).as(simpleUser.*)
  } ++ User.admins.filter( user => user.areas.contains(areaId))

  def byAreas(areaIds: List[UUID]): List[User] = db.withConnection { implicit connection =>
    SQL"""SELECT * FROM "user" WHERE ARRAY[$areaIds]::uuid[] && areas""".as(simpleUser.*)
  } ++ User.admins.filter( user => user.areas.intersect(areaIds).nonEmpty)

  def byGroupIds(ids: List[UUID]): List[User] = db.withConnection { implicit connection =>
    SQL"""SELECT * FROM "user" WHERE ARRAY[$ids]::uuid[] && group_ids""".as(simpleUser.*)
  }

  def byId(id: UUID, includeDisabled: Boolean = false): Option[User] = {
    val results = byIds(List(id), includeDisabled)
    assert(results.length <= 1)
    results.headOption
  }

  def byIds(ids: List[UUID], includeDisabled: Boolean = false): List[User] = db.withConnection { implicit connection =>
    val disabledSQL: String = if(includeDisabled) { "" } else { "AND disabled = false" }
    SQL(s"""SELECT * FROM "user" WHERE ARRAY[{ids}]::uuid[] @> ARRAY[id]::uuid[] $disabledSQL""").on('ids -> ids).as(simpleUser.*)
  } ++ User.admins.filter(user => ids.contains(user.id)).filter(!_.disabled || includeDisabled)

  def byKey(key: String): Option[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE key = {key} AND disabled = false""").on('key -> key).as(simpleUser.singleOpt)
  }.orElse(User.admins.find(_.key == key))

  def byEmail(email: String): Option[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE lower(email) = {email} AND disabled = false""").on('email -> email.toLowerCase()).as(simpleUser.singleOpt)
  }.orElse(User.admins.find(_.email.toLowerCase() == email.toLowerCase())).filter(!_.disabled)

  def byEmails(emails: List[String]): List[User] = {
    val lowerCaseEmails = emails.map(_.toLowerCase)
    db.withConnection { implicit connection =>
      SQL"""SELECT * FROM "user" WHERE  ARRAY[${lowerCaseEmails}]::text[] @> ARRAY[lower(email)]::text[]""".as(simpleUser.*)
    }.toList ++ (User.admins.filter(user => lowerCaseEmails.contains(user.email.toLowerCase)))
  }

  def deleteById(userId: UUID): Unit = db.withTransaction { implicit connection =>
    SQL"""DELETE FROM "user" WHERE id = ${userId}::uuid""".execute()
  }

  def add(users: List[User]): Either[String, Unit] = try {
    val result = db.withTransaction { implicit connection =>
      users.foldRight(true) { (user, success)  =>
        success && SQL"""
        INSERT INTO "user" (id, key, name, qualite, email, helper, instructor, admin, areas, delegations, creation_date,
        has_accepted_charte, commune_code, group_admin, group_ids, expert, phone_number) VALUES (
           ${user.id}::uuid,
           ${Hash.sha256(s"${user.id}$cryptoSecret")},
           ${user.name},
           ${user.qualite},
           ${user.email},
           ${user.helper},
           ${user.instructor},
           ${user.admin},
           array[${user.areas}]::uuid[],
           ${Json.toJson(user.delegations)},
           ${user.creationDate},
           ${user.hasAcceptedCharte},
           ${user.communeCode},
           ${user.groupAdmin},
           array[${user.groupIds}]::uuid[],
           ${user.expert},
           ${user.phoneNumber})
        """.executeUpdate() == 1
      }
    }
    if(result)
      Right(Unit)
    else
      Left("Aucun utilisateur n'a été ajouté")
  } catch {
    case ex: PSQLException =>
      val EmailErrorPattern = """[^()@]+@[^()@.]+\.[^()@]+""".r // This didn't work in that case : """ Detail: Key \(email\)=\(([^()]*)\) already exists."""".r  (don't know why, the regex is correct)
      val errorMessage = EmailErrorPattern.findFirstIn(ex.getServerErrorMessage.toString) match {
        case Some(email) => s"Un utilisateur avec l'adresse $email existe déjà."
        case _ => s"SQL Erreur : ${ex.getServerErrorMessage.toString}"
      }
      Left(errorMessage)
  }

  def update(users: List[User]) = db.withTransaction { implicit connection =>
    users.foldRight(true) { (user, success)  =>
      success && SQL"""
          UPDATE "user" SET
          name = ${user.name},
          qualite = ${user.qualite},
          email = ${user.email},
          helper = ${user.helper},
          instructor = ${user.instructor},
          admin = ${user.admin},
          areas = array[${user.areas}]::uuid[],
          has_accepted_charte = ${user.hasAcceptedCharte},
          commune_code = ${user.communeCode},
          delegations = ${Json.toJson(user.delegations)},
          group_admin = ${user.groupAdmin},
          group_ids = array[${user.groupIds}]::uuid[],
          expert = ${user.expert},
          phone_number = ${user.phoneNumber},
          disabled = ${user.disabled}
          WHERE id = ${user.id}::uuid
       """.executeUpdate() == 1
    }
  }

  def update(user: User) = db.withConnection {  implicit connection =>
    SQL"""
          UPDATE "user" SET
          name = ${user.name},
          qualite = ${user.qualite},
          email = ${user.email},
          helper = ${user.helper},
          instructor = ${user.instructor},
          admin = ${user.admin},
          areas = array[${user.areas}]::uuid[],
          has_accepted_charte = ${user.hasAcceptedCharte},
          commune_code = ${user.communeCode},
          delegations = ${Json.toJson(user.delegations)},
          group_admin = ${user.groupAdmin},
          group_ids = array[${user.groupIds}]::uuid[],
          expert = ${user.expert},
          phone_number = ${user.phoneNumber},
          disabled = ${user.disabled}
          WHERE id = ${user.id}::uuid
       """.executeUpdate() == 1
  }

  def acceptCGU(userId: UUID, acceptNewsletter: Boolean) = db.withConnection { implicit connection =>
    val now = Time.now()
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
    } else { true }
    resultCGUAcceptation && resultNewsletterAcceptation
  }
}