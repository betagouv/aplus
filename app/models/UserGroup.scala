package models

import java.util.UUID

import org.joda.time.DateTime

case class UserGroup(id: UUID,
                     name: String,
                     description: Option[String],
                     inseeCode: List[String],
                     creationDate: DateTime,
                     createByUserId: UUID,
                     areaIds: List[UUID],
                     organisation: Option[String] = None,
                     email: Option[String] = None) {

  def canHaveUsersAddedBy(user: User): Boolean =
    (user.groupAdmin && user.groupIds.contains(id)) || (user.admin && areaIds.forall(user.areas.contains))
  
  lazy val organisationSetOrDeducted: Option[String] = organisation.orElse(UserGroup.organisationDeductedFromName(name))
}

object UserGroup {

  def organisationDeductedFromName(name: String): Option[String] = {
    val lowerCaseName = name.toLowerCase()
    Organisation.all.find { organisation =>
      lowerCaseName.contains(organisation.shortName.toLowerCase()) || lowerCaseName.contains(organisation.name.toLowerCase())
    }.map(_.shortName)
  }
}