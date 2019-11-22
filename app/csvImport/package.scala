import java.util.UUID

import models.{User, UserGroup}
import play.api.data.Forms.{list, mapping}
import play.api.data.{FormError, Mapping}

package object csvImport {

  val USER_NAME_HEADER_PREFIX: String = "Nom"
  val USER_QUALITY_HEADER_PREFIX: String = "Qualité"
  val USER_EMAIL_HEADER_PREFIX: String = "Email"
  val HELPER_HEADER_PREFIX: String = "Aidant"
  val INSTRUCTOR_HEADER_PREFIX: String = "Instructeur"
  val GROUP_MANAGER_HEADER_PREFIX: String = "Responsable"

  val TERRITORY_HEADER_PREFIX: String = "Territoire"
  val GROUP_ORGANISATION_HEADER_PREFIX: String = "Organisation"
  val GROUP_NAME_HEADER_PREFIX: String = "Groupe"
  val GROUP_EMAIL_HEADER_PREFIX: String = "Bal"

  val SEPARATOR: String = ";"

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