package views.home

import models.User

sealed trait LoginPanel

object LoginPanel {
  case object ConnectionForm extends LoginPanel

  case class EmailSentFeedback(
      user: User,
      tokenExpirationInMinutes: Int,
      successMessage: Option[String]
  ) extends LoginPanel
}
