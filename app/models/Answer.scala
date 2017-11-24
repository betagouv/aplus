package models

import org.joda.time.DateTime

case class Answer(applicationId: String, creationDate: DateTime, message: String, createBy: User, invitedUsers: List[User], visibleByTheCreator: Boolean, area: String)