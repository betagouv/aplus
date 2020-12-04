package views.helpers

import cats.implicits.toShow
import models.Application.Status.{Archived, New, Processed, Processing, Sent, ToArchive}
import models.{Application, User}
import scalatags.Text.all._

object applications {

  def statusTag(application: Application, user: User): Tag = {
    val status = application.longStatus(user)
    val classes: String = status match {
      case Processing =>
        "tag mdl-color--light-blue-300 mdl-color-text--black"
      case Processed | ToArchive =>
        "tag mdl-color--grey-500 mdl-color-text--white"
      case Archived =>
        "tag mdl-color--grey-200 mdl-color-text--black"
      case New =>
        "tag mdl-color--pink-400 mdl-color-text--white"
      case Sent =>
        "tag mdl-color--deep-purple-100 mdl-color-text--black"
    }
    span(
      cls := classes,
      status.show
    )
  }

}
