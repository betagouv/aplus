package forms

import java.util.UUID

object Models {
  case class ApplicationData(subject: String, description: String, infos: Map[String, String], users: List[UUID], organismes: List[String])

  case class AnswerData(message: String, notifiedUsers: List[UUID])
}
