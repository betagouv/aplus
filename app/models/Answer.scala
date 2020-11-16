package models

import java.time.ZonedDateTime
import java.util.UUID

import cats.kernel.Eq
import cats.syntax.all._
import models.Answer.AnswerType

case class Answer(
    id: UUID,
    applicationId: UUID,
    creationDate: ZonedDateTime,
    answerType: AnswerType,
    message: String,
    creatorUserID: UUID,
    creatorUserName: String,
    invitedUsers: Map[UUID, String],
    visibleByHelpers: Boolean,
    declareApplicationHasIrrelevant: Boolean,
    userInfos: Option[Map[String, String]],
    files: Option[Map[String, Long]] = Map.empty[String, Long].some,
    invitedGroupIds: List[UUID]
) extends AgeModel

object Answer {

  sealed trait AnswerType {
    val name: String
  }

  object AnswerType {

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
    implicit val AnswerTypeEq: Eq[AnswerType] = (x: AnswerType, y: AnswerType) => x == y

    final case object Custom extends AnswerType {
      override val name = "custom"
    }

    final case object WorkInProgress extends AnswerType {
      override val name = "workInProgress"
    }

    final case object ApplicationProcessed extends AnswerType {
      override val name = "applicationProcessed"
    }

    final case object WrongInstructor extends AnswerType {
      override val name = "wrongInstructor"
    }

    def fromString(value: String): AnswerType = value match {
      case WorkInProgress.name       => WorkInProgress
      case ApplicationProcessed.name => ApplicationProcessed
      case WrongInstructor.name      => WrongInstructor
      case _                         => Custom
    }

  }

  def filesAvailabilityLeftInDays(filesExpirationInDays: Int)(answer: Answer): Option[Int] =
    answer.ageInDays.some.map(filesExpirationInDays - _).filter(_ >= 0)

}
