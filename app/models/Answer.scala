package models

import java.util.UUID

import org.joda.time.DateTime

case class Answer(id: UUID,
                  applicationId: UUID,
                  creationDate: DateTime,
                  message: String,
                  creatorUserID: UUID,
                  creatorUserName: String,
                  invitedUsers: Map[UUID, String],
                  visibleByHelpers: Boolean,
                  area: UUID,
                  declareApplicationHasIrrelevant: Boolean,
                  userInfos: Option[Map[String, String]])