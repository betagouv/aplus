package services

import java.sql.ResultSet
import java.util.UUID

import anorm._
import extentions.Time
import javax.inject.Inject
import models.UserGroup
import play.api.db.Database
import anorm.JodaParameterMetaData._

@javax.inject.Singleton
class UserGroupService @Inject()(configuration: play.api.Configuration, db: Database) {

  import extentions.Anorm._

  private val simpleUserGroup: RowParser[UserGroup] = Macro.parser[UserGroup](
    "id",
    "name",
    "description",
    "insee_code",
    "creation_date",
    "create_by_user_id",
    "area", //TODO rename to area_id
    "organisation",
    "email"
  ).map(a => a.copy(creationDate = a.creationDate.withZone(Time.dateTimeZone)))


  def add(group: UserGroup) = db.withConnection { implicit connection =>
    SQL"""
      INSERT INTO user_group(id, name, description, insee_code, creation_date, create_by_user_id, area, organisation, email) VALUES (
         ${group.id}::uuid,
         ${group.name},
         ${group.description},
         ${group.inseeCode},
         ${group.creationDate},
         ${group.createByUserId}::uuid,
         ${group.area}::uuid,
         ${group.organisation},
         ${group.email}
      )"""
      .executeUpdate() == 1
  }

  def edit(group: UserGroup) = db.withConnection { implicit connection =>
    SQL"""
          UPDATE user_group SET
          name = ${group.name},
          description = ${group.description},
          insee_code = ${group.inseeCode},
          organisation = ${group.organisation},
          area = ${group.area}::uuid,
          email = ${group.email}
          WHERE id = ${group.id}::uuid
       """.executeUpdate() == 1
  }

  def allGroupByAreas(areaIds: List[UUID]) = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE ARRAY[$areaIds]::uuid[] @> ARRAY[area]::uuid[]".as(simpleUserGroup.*)
  }

  def allGroups = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group".as(simpleUserGroup.*)
  }

  def groupByIds(groupIds: List[UUID]) = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE ARRAY[$groupIds]::uuid[] @> ARRAY[id]::uuid[]".as(simpleUserGroup.*)
  }

  def groupById(groupId: UUID) = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE id = $groupId::uuid".as(simpleUserGroup.singleOpt)
  }

  def deleteById(groupId: UUID): Unit = db.withConnection { implicit connection =>
    SQL"""DELETE FROM "user_group" WHERE id = ${groupId}::uuid""".execute()
  }

  def isGroupEmpty(groupId: UUID): Boolean = db.withConnection { implicit connection =>
    val cardinality: Int =
      SQL"""SELECT COUNT(id) as cardinality FROM "user" WHERE group_ids @> ARRAY[$groupId]::uuid[]"""
        .executeQuery()
        .resultSet.apply[Int]({ (rs: ResultSet) =>
        rs.next()
        rs.getInt("cardinality")
      })
    cardinality == 0
  }
}