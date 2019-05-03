package models

import java.util.UUID

import org.joda.time.DateTime

case class Event(id: UUID,
                 level: String,
                 code: String,
                 fromUserName: String,
                 fromUserId: UUID,
                 creationDate: DateTime,
                 description: String,
                 area: UUID,
                 toApplicationId: Option[UUID],
                 toUserId: Option[UUID],
                 ipAddress: String)
