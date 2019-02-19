package models

import java.util.UUID

import org.joda.time.DateTime

case class UserGroup(id: UUID,
                name: String,
                userIds: List[UUID],
                inseeCode: String,
                creationDate: DateTime,
                createByUserId: UUID,
                area: UUID)
