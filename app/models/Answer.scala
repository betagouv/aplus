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
                  declareApplicationHasIrrelevant: Boolean,
                  userInfos: Option[Map[String, String]],
                  files: Option[Map[String, Long]] = Some(Map())) extends AgeModel {
  lazy val filesAvailabilityLeftInDays: Option[Int] = if(ageInDays > 8 ) { None } else { Some(7 - ageInDays) }
}