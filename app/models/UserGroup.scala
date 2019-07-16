package models

import java.util.UUID

import org.joda.time.DateTime

case class UserGroup(id: UUID,
                name: String,
                inseeCode: String,
                creationDate: DateTime,
                createByUserId: UUID,
                area: UUID,
                organisation: Option[String] = None)   {

  def canHaveUsersAddedBy(user: User) =
    (user.groupAdmin && user.groupIds.contains(id)) ||
      user.admin

}
