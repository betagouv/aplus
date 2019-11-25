import java.util.UUID

import models.{User, UserGroup}
import play.api.data.Forms.{list, mapping}
import play.api.data.{Form, Mapping}

package object csv {

  val USER_NAME_HEADER_PREFIX = "Nom"
  val USER_QUALITY_HEADER_PREFIX = "Qualité"
  val USER_EMAIL_HEADER_PREFIX = "Email"
  val HELPER_HEADER_PREFIX = "Aidant"
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

  val sectionMapping: Mapping[SectionImport] = mapping(
    "group" -> GroupImport.groupMappingForCSVImport,
    "users" -> list(UserImport.userMappingForCVSImport)
  )(SectionImport.apply)(SectionImport.unapply)

  private val tupleMapping: Mapping[(UserGroup, User)] = mapping(
    "group" -> GroupImport.groupMappingForCSVImport,
    "user" -> UserImport.userMappingForCVSImport
  )((g: UserGroup, u: User) => g -> u)(tuple => Option(tuple._1 -> tuple._2))

  val tupleForm: Form[(UserGroup, User)] = Form.apply(tupleMapping)

  case class SectionImport(group: UserGroup, users: List[User])

  def fromCSVLine(values: Map[String, String], groupHeaders: List[String], userHeaders: List[String]): Form[(UserGroup, User)] = {
    tupleForm.bind(convertToPrefixForm(values, groupHeaders, "group.") ++ convertToPrefixForm(values, userHeaders, "user."))
  }

  val deadbeef: UUID = UUID.fromString("deadbeef-0000-0000-0000-000000000000")
}