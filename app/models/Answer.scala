package models

import java.time.ZonedDateTime
import java.util.UUID

case class Answer(
    id: UUID,
    applicationId: UUID,
    creationDate: ZonedDateTime,
    message: String,
    creatorUserID: UUID,
    creatorUserName: String,
    invitedUsers: Map[UUID, String],
    visibleByHelpers: Boolean,
    declareApplicationHasIrrelevant: Boolean,
    userInfos: Option[Map[String, String]],
    files: Option[Map[String, Long]] = Some(Map())
) extends AgeModel {

  lazy val filesAvailabilityLeftInDays: Option[Int] = if (ageInDays > 8) {
    None
  } else {
    Some(7 - ageInDays)
  }

}
