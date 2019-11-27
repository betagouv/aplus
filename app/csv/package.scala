import java.util.UUID

import models.{User, UserGroup}
import org.joda.time.DateTime
import play.api.data.Forms.{list, mapping}
import play.api.data.{Form, Mapping}

package object csv {

  val USER_NAME_HEADER_PREFIX = "Nom"
  val USER_QUALITY_HEADER_PREFIX = "Qualité"
  val USER_EMAIL_HEADER_PREFIX = "Email"
  val INSTRUCTOR_HEADER_PREFIX = "Instructeur"
  val GROUP_MANAGER_HEADER_PREFIX = "Responsable"

  val TERRITORY_HEADER_PREFIX = "Territoire"
  val GROUP_ORGANISATION_HEADER_PREFIX = "Organisation"
  val GROUP_NAME_HEADER_PREFIX = "Groupe"
  val GROUP_EMAIL_HEADER_PREFIX = "Bal"

  val SEPARATOR = ";"

  def convertToPrefixForm(values: Map[String, String], headers: List[String], prefix: String): Map[String, String] = {
    values.map({ case (key, value) =>
      headers.find(key.startsWith).map(prefix + _ -> value)
    }).flatten.toMap
  }

  val sectionMapping: UUID => UUID => UUID => DateTime => Mapping[Section] =
    (groupId: UUID) => (userId: UUID) => (creatorId: UUID) => (dateTime: DateTime) => mapping(
      "group" -> GroupImport.groupMappingForCSVImport(groupId)(creatorId)(dateTime),
      "users" -> list(UserImport.userMappingForCVSImport(userId)(dateTime))
    )(Section.apply)(Section.unapply)

  private val tupleMapping: UUID => UUID => UUID => DateTime => Mapping[(UserGroup, User)] =
    (groupId: UUID) => (userId: UUID) => (creatorId: UUID) => (dateTime: DateTime) => mapping(
      "group" -> GroupImport.groupMappingForCSVImport(groupId)(creatorId)(dateTime),
      "user" -> UserImport.userMappingForCVSImport(userId)(dateTime)
    )((g: UserGroup, u: User) => g -> u)(tuple => Option(tuple._1 -> tuple._2))

  val tupleForm: UUID => UUID => UUID => DateTime => Form[(UserGroup, User)] =
    (groupId: UUID) => (userId: UUID) => (creatorId: UUID) => (dateTime: DateTime) =>
      Form.apply(tupleMapping(groupId)(userId)(creatorId)(dateTime))

  case class Section(group: UserGroup, users: List[User])

  def fromCSVLine(values: Map[String, String], groupHeaders: List[String], userHeaders: List[String], groupId: UUID,
                  userId: UUID, creatorId: UUID, dateTime: DateTime): Form[(UserGroup, User)] = {
    tupleForm(groupId)(userId)(creatorId)(dateTime).bind(convertToPrefixForm(values, groupHeaders, "group.") ++ convertToPrefixForm(values, userHeaders, "user."))
  }

  //val undefined: UUID = UUID.fromString("deadbeef-0000-0000-0000-000000000000")
}