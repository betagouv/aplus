package views.home

sealed trait LoginPanel

object LoginPanel {
  case object ConnectionForm extends LoginPanel

  case class SendbackEmailForm(email: String, errorMessage: Option[String]) extends LoginPanel

  case class EmailSentFeedback(
      userEmail: String,
      tokenExpirationInMinutes: Int,
      successMessage: Option[String]
  ) extends LoginPanel

}
