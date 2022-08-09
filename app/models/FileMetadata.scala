package models

import cats.kernel.Eq
import cats.syntax.all._
import helper.Crypto.EncryptedField
import java.time.Instant
import java.util.UUID

case class FileMetadata(
    id: UUID,
    uploadDate: Instant,
    // None if it has been wiped (avoids encrypting tons of empty strings)
    filename: Option[EncryptedField],
    filesize: Int,
    status: FileMetadata.Status,
    attached: FileMetadata.Attached,
)

object FileMetadata {

  def filenameAAD(id: UUID): String = s"FileMetadata_$id"

  sealed trait Status

  object Status {

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
    implicit val eq: Eq[Status] = (x: Status, y: Status) => x == y

    case object Scanning extends Status
    case object Quarantined extends Status
    case object Available extends Status
    case object Expired extends Status
    case object Error extends Status
  }

  sealed trait Attached {
    def isApplication: Boolean
    def isAnswer: Boolean
    def answerIdOpt: Option[UUID]
  }

  object Attached {

    case class Application(id: UUID) extends Attached {
      val isApplication = true
      val isAnswer = false
      val answerIdOpt = none
    }

    case class Answer(applicationId: UUID, answerId: UUID) extends Attached {
      val isApplication = false
      val isAnswer = true
      val answerIdOpt = answerId.some
    }

  }

}
