package views.helpers

import models.{Application, User}
import scalatags.Text.all._

object applications {

  def statusTag(application: Application, user: User): Tag = {
    val status: String = application.longStatus(user)
    val classes: String = status match {
      case "Traitée" =>
        "tag mdl-color--grey-500 mdl-color-text--white"
      case "Archivée" =>
        "tag mdl-color--grey-200 mdl-color-text--black"
      case "Nouvelle" =>
        "tag mdl-color--pink-400 mdl-color-text--white"
      case someStatus if someStatus.contains("Répondu") =>
        "tag mdl-color--light-blue-300 mdl-color-text--black"
      case "Consultée" =>
        "tag mdl-color--light-blue-50 mdl-color-text--black"
      case "Envoyée" =>
        "tag mdl-color--deep-purple-100 mdl-color-text--black"
      case _ =>
        "tag"
    }
    span(
      cls := classes,
      status
    )
  }

}
