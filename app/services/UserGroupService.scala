package services

import java.sql.ResultSet
import java.util.UUID

import anorm._
import extentions.{Hash, Time, UUIDHelper}
import javax.inject.Inject
import models.{User, UserGroup}
import play.api.db.Database
import anorm.JodaParameterMetaData._
import play.api.libs.json.Json

@javax.inject.Singleton
class UserGroupService @Inject()(configuration: play.api.Configuration, db: Database) {

  private val simpleUserGroup: RowParser[UserGroup] = Macro.parser[UserGroup](
    "id",
    "name",
    "description",
    "insee_code",
    "creation_date",
    "area_ids",
    "organisation",
    "email"
  ).map(a => a.copy(creationDate = a.creationDate.withZone(Time.dateTimeZone)))

  def add(groups: List[UserGroup]) = db.withTransaction { implicit connection =>
    groups.foldRight(true) { (group, success) =>
      success &&
        SQL"""
      INSERT INTO user_group(id, name, description, insee_code, creation_date, create_by_user_id, area_ids, organisation, email) VALUES (
         ${group.id}::uuid,
         ${group.name},
         ${group.description},
         array[${group.inseeCode}]::character varying(5)[],
         ${group.creationDate},
         ${UUIDHelper.namedFrom("deprecated")}
         array[${group.areaIds}]::uuid[],
         ${group.organisation},
         ${group.email})
      """.executeUpdate() == 1
    }
  }

  def add(group: UserGroup): Boolean = add(List(group))

  def edit(group: UserGroup): Boolean = db.withConnection { implicit connection =>
    SQL"""
          UPDATE user_group SET
          name = ${group.name},
          description = ${group.description},
          insee_code = array[${group.inseeCode}]::character varying(5)[],
          organisation = ${group.organisation},
          area_ids = array[${group.areaIds}]::uuid[],
          email = ${group.email}
          WHERE id = ${group.id}::uuid
       """.executeUpdate() == 1
  }

  def allGroupByAreas(areaIds: List[UUID]): List[UserGroup] = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE ARRAY[$areaIds]::uuid[] && area_ids".as(simpleUserGroup.*)
  }

  def allGroups: List[UserGroup] = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group".as(simpleUserGroup.*)
  }

  def byIds(groupIds: List[UUID]): List[UserGroup] = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE ARRAY[$groupIds]::uuid[] @> ARRAY[id]::uuid[]".as(simpleUserGroup.*)
  }

  def groupById(groupId: UUID): Option[UserGroup] = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE id = $groupId::uuid".as(simpleUserGroup.singleOpt)
  }

  def groupByName(groupName: String): Option[UserGroup] = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE name = $groupName".as(simpleUserGroup.singleOpt)
  }

  def deleteById(groupId: UUID): Unit = db.withConnection { implicit connection =>
    SQL"""DELETE FROM "user_group" WHERE id = $groupId::uuid""".execute()
  }

  def isGroupEmpty(groupId: UUID): Boolean = db.withConnection { implicit connection =>
    val cardinality: Int =
      SQL"""SELECT COUNT(id) as cardinality FROM "user" WHERE group_ids @> ARRAY[$groupId]::uuid[]"""
        .executeQuery()
        .resultSet.apply[Int]({ rs: ResultSet =>
        rs.next()
        rs.getInt("cardinality")
      })
    cardinality == 0
  }
}