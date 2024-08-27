package models

import helper.StringHelper.withQuotes
import helper.Time
import java.time.Instant
import java.util.UUID

case class SignupRequest(
    id: UUID,
    requestDate: Instant,
    email: String,
    invitingUserId: UUID
) {

  lazy val requestDateLog: String = Time.formatForAdmins(requestDate)
  lazy val emailLog: String = withQuotes(email)

  lazy val toLogString: String =
    "[" + List[(String, String)](
      ("Id", id.toString),
      ("Date", requestDateLog),
      ("Email", emailLog),
      ("Utilisateur responsable", invitingUserId.toString),
    ).map { case (fieldName, value) => s"$fieldName : $value" }.mkString(" | ") + "]"

}
