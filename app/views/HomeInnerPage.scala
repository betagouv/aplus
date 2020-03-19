package views

import models.User

sealed trait HomeInnerPage

object HomeInnerPage {
  case object ConnectionForm extends HomeInnerPage

  case class EmailSentFeedback(
      user: User,
      tokenExpirationInMinutes: Int,
      successMessage: Option[String]
  ) extends HomeInnerPage
}
