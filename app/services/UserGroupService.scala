package services

import java.sql.ResultSet
import java.util.UUID

import anorm._
import cats.syntax.all._
import helper.{Time, UUIDHelper}
import javax.inject.Inject
import models.{Organisation, UserGroup}
import org.postgresql.util.PSQLException
import play.api.db.Database

import scala.concurrent.Future

@javax.inject.Singleton
class UserGroupService @Inject() (
    configuration: play.api.Configuration,
    db: Database,
    dependencies: ServicesDependencies
) {

  import dependencies.databaseExecutionContext

  private val simpleUserGroup: RowParser[UserGroup] = Macro
    .parser[UserGroup](
      "id",
      "name",
      "description",
      "insee_code",
      "creation_date",
      "area_ids",
      "organisation",
      "email",
      "public_note"
    )
    .map(a => a.copy(creationDate = a.creationDate.withZoneSameInstant(Time.timeZoneParis)))

  def add(groups: List[UserGroup]): Either[String, Unit] =
    try {
      val result = db.withTransaction { implicit connection =>
        groups.foldRight(true) { (group, success) =>
          success &&
          SQL"""
      INSERT INTO user_group(id, name, description, insee_code, creation_date, create_by_user_id, area_ids, organisation, email) VALUES (
         ${group.id}::uuid,
         ${group.name},
         ${group.description},
         array[${group.inseeCode}]::character varying(5)[],
         ${group.creationDate},
         ${UUIDHelper.namedFrom("deprecated")}::uuid,
         array[${group.areaIds}]::uuid[],
         ${group.organisation.map(_.id)},
         ${group.email})
      """.executeUpdate() === 1
        }
      }
      if (result)
        Right(())
      else
        Left("Aucun groupe n'a été ajouté")
    } catch {
      case ex: PSQLException =>
        val EmailErrorPattern =
          """[^()@]+@[^()@.]+\.[^()@]+""".r // This didn't work in that case : """ Detail: Key \(email\)=\(([^()]*)\) already exists."""".r  (don't know why, the regex is correct)
        val errorMessage = EmailErrorPattern.findFirstIn(ex.getServerErrorMessage.toString) match {
          case Some(email) => s"Un groupe avec l'adresse $email existe déjà."
          case _           => s"SQL Erreur : ${ex.getServerErrorMessage.toString}"
        }
        Left(errorMessage)
    }

  def add(group: UserGroup): Either[String, Unit] = add(List(group))

  def edit(group: UserGroup): Boolean =
    db.withConnection { implicit connection =>
      SQL"""
          UPDATE user_group SET
          name = ${group.name},
          description = ${group.description},
          organisation = ${group.organisation.map(_.id)},
          area_ids = array[${group.areaIds}]::uuid[],
          email = ${group.email},
          public_note = ${group.publicNote}
          WHERE id = ${group.id}::uuid
       """.executeUpdate() === 1
    //TODO: insee_code = array[${group.inseeCode}]::character varying(5)[], have been remove temporary
    }

  def allGroups: List[UserGroup] =
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM user_group".as(simpleUserGroup.*)
    }

  def all: Future[List[UserGroup]] =
    Future {
      db.withConnection(implicit connection => SQL"SELECT * FROM user_group".as(simpleUserGroup.*))
    }

  def byIds(groupIds: List[UUID]): List[UserGroup] =
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM user_group WHERE ARRAY[$groupIds]::uuid[] @> ARRAY[id]::uuid[]".as(
        simpleUserGroup.*
      )
    }

  def byIdsFuture(groupIds: List[UUID]): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"SELECT * FROM user_group WHERE ARRAY[$groupIds]::uuid[] @> ARRAY[id]::uuid[]".as(
          simpleUserGroup.*
        )
      }
    }

  def groupById(groupId: UUID): Option[UserGroup] =
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM user_group WHERE id = $groupId::uuid".as(simpleUserGroup.singleOpt)
    }

  def groupByName(groupName: String): Option[UserGroup] =
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM user_group WHERE name = $groupName".as(simpleUserGroup.singleOpt)
    }

  def deleteById(groupId: UUID): Boolean =
    db.withConnection { implicit connection =>
      SQL"""DELETE FROM "user_group" WHERE id = $groupId::uuid""".execute()
    }

  def isGroupEmpty(groupId: UUID): Boolean =
    db.withConnection { implicit connection =>
      val cardinality: Int =
        SQL"""SELECT COUNT(id) as cardinality FROM "user" WHERE group_ids @> ARRAY[$groupId]::uuid[]"""
          .executeQuery()
          .resultSet
          .apply[Int]({ rs: ResultSet =>
            rs.next()
            rs.getInt("cardinality")
          })
      cardinality === 0
    }

  def byArea(areaId: UUID): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""SELECT * FROM "user_group" WHERE area_ids @> ARRAY[$areaId]::uuid[]"""
          .as(simpleUserGroup.*)
      }
    }

  def byAreas(areaIds: List[UUID]): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""SELECT * FROM "user_group" WHERE ARRAY[$areaIds]::uuid[] && area_ids"""
          .as(simpleUserGroup.*)
      }
    }

  def byOrganisationIds(organisationIds: List[Organisation.Id]): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        val organisationIdStrings = organisationIds.map(_.id)
        SQL"""SELECT * FROM "user_group" WHERE ARRAY[$organisationIdStrings]::varchar[] @> ARRAY[organisation]"""
          .as(simpleUserGroup.*)
      }
    }

}
