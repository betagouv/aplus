package forms

object Models {
  case class ApplicationData(subject: String, description: String, infos: Map[String, String], users: List[String])

  case class AnwserData(message: String)

  case class InviteData(message: String, invitedUsers: List[String])
}
