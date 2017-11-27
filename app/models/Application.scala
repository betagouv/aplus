package models

import java.util.UUID

import org.joda.time.DateTime

case class Application(id: UUID,
                       status: String,
                       creationDate: DateTime,
                       creatorUserName: String,
                       creatorUserId: UUID,
                       subject: String,
                       description: String,
                       userInfos: Map[String, String],
                       invitedUsers: Map[UUID, String],
                       area: UUID)