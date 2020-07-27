package views.helpers

import models.{Application, User}
import scalatags.Text.all._

object applications {

  def statusTag(application: Application, user: User): Tag = {
    val status: String = application.longStatus(user)
    val classes: String = status match {
      case "Clôturée" =>
        "tag mdl-color--blue-500 mdl-color-text--black"
      case "Nouvelle" =>
        "tag mdl-color--purple-600 mdl-color-text--white"
      case someStatus if status.contains("Répondu") =>
        "tag mdl-color--pink-700 mdl-color-text--white"
      case "Consultée" =>
        "tag mdl-color--green-A400 mdl-color-text--black"
      case "Envoyée" =>
        "tag mdl-color--purple-200 mdl-color-text--black"
      case _ =>
        "tag"
    }
    span(
      cls := classes,
      status
    )
  }

}
