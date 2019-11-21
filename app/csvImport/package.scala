import play.api.data.Forms.{mapping, list}
import play.api.data.Mapping

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

  def searchByPrefix(prefix: String, values: Map[String, String]): Option[String] = {
    values.find(_._1.startsWith(prefix)).map(_._2)
  }

  val sectionMapping: Mapping[SectionImport] = mapping(
    "group" -> GroupImport.groupMapping,
    "users" -> list(UserImport.userMaping)
  )(SectionImport.apply)(SectionImport.unapply)

  case class SectionImport(group: GroupImport, users: List[UserImport])

}