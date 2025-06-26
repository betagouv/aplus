package models

import cats.kernel.Eq
import cats.syntax.all._
import java.time.ZonedDateTime
import java.util.UUID
import models.Answer.AnswerType

/** To cast `creationDate` to a valid PG type, use `regexp_replace(creation_date, '\[.*\]',
  * '')::timestamptz::timestamp`
  */
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
    invitedGroupIds: List[UUID]
) extends AgeModel

object Answer {

  sealed trait AnswerType {
    val name: String
  }

  object AnswerType {

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
    implicit val AnswerTypeEq: Eq[AnswerType] = (x: AnswerType, y: AnswerType) => x == y

    case object Custom extends AnswerType {
      override val name = "custom"
    }

    case object WorkInProgress extends AnswerType {
      override val name = "workInProgress"
    }

    case object ApplicationProcessed extends AnswerType {
      override val name = "applicationProcessed"
    }

    case object WrongInstructor extends AnswerType {
      override val name = "wrongInstructor"
    }

    case object InviteByUser extends AnswerType {
      override val name = "inviteByUser"
    }

    case object InviteAsExpert extends AnswerType {
      override val name = "inviteAsExpert"
    }

    case object InviteThroughGroupPermission extends AnswerType {
      override val name = "inviteThroughGroupPermission"
    }

    def fromString(value: String): AnswerType = value match {
      case WorkInProgress.name               => WorkInProgress
      case ApplicationProcessed.name         => ApplicationProcessed
      case WrongInstructor.name              => WrongInstructor
      case InviteByUser.name                 => InviteByUser
      case InviteAsExpert.name               => InviteAsExpert
      case InviteThroughGroupPermission.name => InviteThroughGroupPermission
      case _                                 => Custom
    }

  }

}
