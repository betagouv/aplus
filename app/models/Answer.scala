package models

import java.time.ZonedDateTime
import java.util.UUID

import cats.syntax.all._
import models.Authorization.UserRights

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
    files: Option[Map[String, Long]] = Some(Map()),
    // Note: the `Option` is here to keep compatibility with the existing json
    invitedGroupIds: Option[List[UUID]],
) extends AgeModel

object Answer {

  def filesAvailabilityLeftInDays(filesExpirationInDays: Int)(answer: Answer): Option[Int] =
    answer.ageInDays.some.map(filesExpirationInDays - _).filter(_ >= 0)

}
