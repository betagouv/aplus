package views.home

import java.time.ZoneId

sealed trait LoginPanel

object LoginPanel {
  case object ConnectionForm extends LoginPanel

  case class SendbackEmailForm(email: String, errorMessage: Option[String]) extends LoginPanel

  case class EmailSentFeedback(
      email: String,
      timeZone: ZoneId,
      tokenExpirationInMinutes: Int,
      successMessage: Option[String]
  ) extends LoginPanel

}
