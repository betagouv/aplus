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
    files: Option[Map[String, Long]] = Some(Map())
) extends AgeModel

object Answer {

  def filesAvailabilityLeftInDays(expiryDayCount: Int)(answer: Answer): Option[Int] =
    answer.ageInDays.some.map(expiryDayCount - _).filter(_ >= 0)

  def fileCanBeShowed(
      application: Application,
      answer: UUID,
      expiryDayCount: Int
  )(user: User, rights: UserRights) =
    application.answers
      .find(_.id === answer)
      .map(Answer.filesAvailabilityLeftInDays(expiryDayCount)) match {
      case Some(_) => Application.fileCanBeShowed(application, expiryDayCount)(user)(rights)
      case _       => false
    }

}
