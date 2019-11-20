package models

import java.util.UUID

import extentions.{Time, UUIDHelper}
import org.joda.time.DateTime

case class UserGroup(id: UUID,
                     name: String,
                     description: Option[String],
                     inseeCode: List[String],
                     creationDate: DateTime,
                     createByUserId: UUID,
                     area: UUID,
                     organisation: Option[String] = None,
                     email: Option[String] = None) {

  def canHaveUsersAddedBy(user: User): Boolean =
    (user.groupAdmin && user.groupIds.contains(id)) || (user.admin && user.areas.contains(area))

  def organisationDeductedFromName(): Option[String] = {
    val lowerCaseName = name.toLowerCase()
    Organisation.all.find { organisation =>
      lowerCaseName.contains(organisation.shortName.toLowerCase()) || lowerCaseName.contains(organisation.name.toLowerCase())
    }.map(_.shortName)
  }

  lazy val organisationSetOrDeducted: Option[String] = organisation.orElse(organisationDeductedFromName)
}

object UserGroup {
  def fromMap(creator: UUID, area: UUID)(values: Map[String, String]) : Option[UserGroup] = {
    val creationDate = Time.now()
    for {
      name <- values.get("name")
      areas <- values.get("areas").map(_.split(",").toList)
    } yield {
      val id = values.get("id").flatMap(UUIDHelper.fromString).getOrElse(UUIDHelper.randomUUID)
      val description = values.get("description")
      val organisation = values.get("organisation")
      val email = values.get("email")
      UserGroup(id = id, name = name, description = description, inseeCode = areas, creationDate = creationDate,
        createByUserId = creator, area = area, organisation = organisation, email = email)
    }
  }
}