package models

import java.util.UUID

import org.joda.time.DateTime

case class Event(
    id: UUID,
    level: String,
    code: String,
    fromUserName: String,
    fromUserId: UUID,
    creationDate: DateTime,
    description: String,
    area: UUID,
    toApplicationId: Option[UUID],
    toUserId: Option[UUID],
    ipAddress: String
) {

  lazy val searchData = {
    val stripChars = "\"<>'"
    s"${Area.fromId(area).map(_.name).getOrElse("")} $level $code ${fromUserName.filterNot(stripChars contains _)} " +
      s"${description.filterNot(stripChars contains _)} ${ipAddress} ${id} ${fromUserId} ${toApplicationId
        .getOrElse("")} ${toUserId.getOrElse("")}"
  }
}
