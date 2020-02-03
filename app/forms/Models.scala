package forms

import java.util.UUID

import models.{User, UserGroup}

object Models {
  // TOOD : rename Data -> FormData

  case class ApplicationData(
      subject: String,
      description: String,
      infos: Map[String, String],
      users: List[UUID],
      organismes: List[String],
      category: Option[String],
      selectedSubject: Option[String]
  )

  case class AnswerData(
      message: String,
      applicationIsDeclaredIrrelevant: Boolean,
      infos: Map[String, String],
      privateToHelpers: Boolean
  )

  case class InvitationData(message: String, invitedUsers: List[UUID], privateToHelpers: Boolean)

  case class UserFormData(
      user: User,
      line: Int,
      alreadyExists: Boolean,
      alreadyExistingUser: Option[User] = None
  )

  case class UserGroupFormData(
      group: UserGroup,
      users: List[UserFormData],
      alreadyExists: Boolean,
      doNotInsert: Boolean,
      alreadyExistingGroup: Option[UserGroup] = None
  )

  case class CSVImportData(csvLines: String, areaIds: List[UUID], separator: Char)
}
