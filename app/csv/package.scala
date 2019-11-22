import java.util.UUID

import models.{User, UserGroup}
import play.api.data.Forms.{list, mapping}
import play.api.data.{FormError, Mapping}

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

  sealed trait CSVImportError

  object GROUP_NAME_UNDEFINED extends CSVImportError

  object DEPARTEMENT_UNDEFINED extends CSVImportError

  object QUALITE_UNDEFINED extends CSVImportError

  object EMAIL_UNDEFINED extends CSVImportError

  object NO_CONTENT extends CSVImportError

  def convertToPrefixForm(values: Map[String, String], headers: List[String]): Map[String, String] = {
    values.map({ case (key, value) =>
      headers.find(key.startsWith).map(_ -> value)
    }).flatten.toMap
  }

  val sectionMapping: Mapping[SectionImport] = mapping(
    "group" -> GroupImport.groupMappingForCSVImport,
    "users" -> list(UserImport.userMappingForCVSImport)
  )(SectionImport.apply)(SectionImport.unapply)

  case class SectionImport(group: UserGroup, users: List[User])

  def fromCSVLine[T](values: Map[String, String], mapping: Mapping[T], headers: List[String]): Either[Seq[FormError], T] = {
    mapping.bind(convertToPrefixForm(values, headers))
  }

  val deadbeef: UUID = UUID.fromString("deadbeef-0000-0000-0000-000000000000")
}