package services

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
    "insee_code",
    "creation_date",
    "create_by_user_id",
    "area",        //TODO rename to area_id
    "organisation",
    "email"
  ).map(a => a.copy(creationDate = a.creationDate.withZone(Time.dateTimeZone)))


  def add(group: UserGroup) = db.withConnection { implicit connection =>
    SQL"""
      INSERT INTO user_group VALUES (
         ${group.id}::uuid,
         ${group.name},
         ${group.inseeCode},
         ${group.creationDate},
         ${group.createByUserId}::uuid,
         ${group.area}::uuid,
         ${group.organisation},
         ${group.email}
      )"""
      .executeUpdate() == 1
  }

  def edit(group: UserGroup) = db.withConnection {  implicit connection =>
    SQL"""
          UPDATE user_group SET
          name = ${group.name},
          insee_code = ${group.inseeCode},
          organisation = ${group.organisation},
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

  def byIds(groupIds: List[UUID]) = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE ARRAY[$groupIds]::uuid[] @> ARRAY[id]::uuid[]".as(simpleUserGroup.*)
  }

  def groupById(groupId: UUID) = db.withConnection { implicit connection =>
    SQL"SELECT * FROM user_group WHERE id = $groupId::uuid".as(simpleUserGroup.singleOpt)
  }
}