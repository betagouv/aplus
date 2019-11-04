package forms

import java.util.UUID

object Models {

  case class ApplicationData(subject: String, description: String, infos: Map[String, String], users: List[UUID], organismes: List[String], category: Option[String], selectedSubject: Option[String])

  case class AnswerData(message: String, applicationIsDeclaredIrrelevant: Boolean, infos: Map[String, String], privateToHelpers: Boolean)

  case class InvitationData(message: String, invitedUsers: List[UUID], privateToHelpers: Boolean)
}
