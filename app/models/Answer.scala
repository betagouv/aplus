package models

import java.util.UUID

import org.joda.time.DateTime

case class Answer(applicationId: UUID,
                  creationDate: DateTime,
                  message: String,
                  createBy: User,
                  invitedUsers: List[User],
                  visibleByTheCreator: Boolean,
                  area: UUID)