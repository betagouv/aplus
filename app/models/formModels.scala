package models

import java.util.UUID

object formModels {
  // TOOD : rename Data -> FormData

  case class ApplicationFormData(
      subject: String,
      description: String,
      infos: Map[String, String],
      users: List[UUID],
      organismes: List[String],
      category: Option[String],
      selectedSubject: Option[String],
      signature: Option[String],
      mandatType: String,
      mandatDate: String,
      linkedMandat: Option[UUID]
  )

  case class AnswerFormData(
      message: String,
      applicationIsDeclaredIrrelevant: Boolean,
      infos: Map[String, String],
      privateToHelpers: Boolean,
      signature: Option[String]
  )

  case class InvitationData(message: String, invitedUsers: List[UUID], privateToHelpers: Boolean)

  case class UserFormData(
      user: User,
      line: Int,
      alreadyExists: Boolean,
      alreadyExistingUser: Option[User] = None,
      isInMoreThanOneGroup: Option[Boolean] = None
  )

  case class UserGroupFormData(
      group: UserGroup,
      users: List[UserFormData],
      alreadyExistsOrAllUsersAlreadyExist: Boolean,
      doNotInsert: Boolean,
      alreadyExistingGroup: Option[UserGroup] = None
  )

  case class CSVImportData(csvLines: String, areaIds: List[UUID], separator: Char)
}
