package services

import java.util.UUID

import javax.inject.Inject
import anorm.{Macro, RowParser, SQL}
import models.{Time, User}
import play.api.db.Database
import extentions.Hash
import play.api.libs.json.Json
import anorm.JodaParameterMetaData._

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
    "delegations"
  ).map(a => a.copy(creationDate = a.creationDate.withZone(Time.dateTimeZone)))

  def all = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user"""").as(simpleUser.*)
  }

  def allDBOnlybyArea(areaId: UUID) = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE areas @> ARRAY[{areaId}]::uuid[]""").on('areaId -> areaId).as(simpleUser.*)
  }

  def byArea(areaId: UUID): List[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE areas @> ARRAY[{areaId}]::uuid[]""").on('areaId -> areaId).as(simpleUser.*)
  } ++ User.admins.filter( user => user.areas.contains(areaId) || user.areas.isEmpty )

  def byId(id: UUID): Option[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE id = {id}::uuid""").on('id -> id).as(simpleUser.singleOpt)
  }.orElse(User.admins.find(_.id == id))

  def byIds(ids: List[UUID]): List[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE ARRAY[{ids}]::uuid[] @> ARRAY[id]::uuid[]""").on('ids -> ids).as(simpleUser.*)
  } ++ User.admins.filter(user => ids.contains(user.id))


  def byKey(key: String): Option[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE key = {key}""").on('key -> key).as(simpleUser.singleOpt)
  }.orElse(User.admins.find(_.key == key))

  def byEmail(email: String): Option[User] = db.withConnection { implicit connection =>
    SQL("""SELECT * FROM "user" WHERE lower(email) = {email}""").on('email -> email.toLowerCase()).as(simpleUser.singleOpt)
  }.orElse(User.admins.find(_.email.toLowerCase() == email.toLowerCase()))

  def add(users: List[User]) = db.withTransaction { implicit connection =>
    users.foldRight(true) { (user, success)  =>
      success && SQL(
        """
      INSERT INTO "user" VALUES (
         {id}::uuid,
         {key},
         {name},
         {qualite},
         {email},
         {helper},
         {instructor},
         {admin},
         array[{areas}]::uuid[],
         {delegations},
         {creation_date},
         {has_accepted_charte},
         {commune_code})
      """)
      .on(
        'id -> user.id,
        'key -> Hash.sha256(s"${user.id}$cryptoSecret"),
        'name -> user.name,
        'qualite -> user.qualite,
        'email -> user.email,
        'helper -> user.helper,
        'instructor -> user.instructor,
        'admin -> user.admin,
        'areas -> user.areas.map(_.toString),
        'delegations -> Json.toJson(user.delegations),
        'creation_date -> user.creationDate,
        'has_accepted_charte -> user.hasAcceptedCharte,
        'commune_code -> user.communeCode
      ).executeUpdate() == 1
    }
  }
  def update(user: User) = db.withConnection {  implicit connection =>
    SQL(
      """
          UPDATE "user" SET
          name = {name},
          qualite = {qualite},
          email = {email},
          helper = {helper},
          instructor = {instructor},
          admin = {admin},
          has_accepted_charte = {has_accepted_charte},
          commune_code = {commune_code},
          delegations = {delegations}
          WHERE id = {id}::uuid
       """
    ).on(
      'id -> user.id,
      'name -> user.name,
      'qualite -> user.qualite,
      'email -> user.email,
      'helper -> user.helper,
      'instructor -> user.instructor,
      'admin -> user.admin,
      'has_accepted_charte -> user.hasAcceptedCharte,
      'commune_code -> user.communeCode,
      'delegations -> Json.toJson(user.delegations)
    ).executeUpdate() == 1
  }

  def acceptCharte(userId: UUID) = db.withConnection {  implicit connection =>
    SQL(
      """
          UPDATE "user" SET
          has_accepted_charte = {has_accepted_charte}
          WHERE id = {id}::uuid
       """
    ).on(
      'id -> userId,
      'has_accepted_charte -> true
    ).executeUpdate() == 1
  }
}