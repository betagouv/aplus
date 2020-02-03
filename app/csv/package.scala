import java.util.UUID

package object csv {

  case class Header(key: String, prefixes: List[String]) {
    val lowerPrefixes = prefixes.map(_.toLowerCase())
  }

  val USER_NAME = Header("user.name", List("Nom", "PRENOM NOM"))
  val USER_FIRST_NAME = Header("user.firstname", List("Prénom", "Prenom"))

  val USER_EMAIL =
    Header("user.email", List("Email", "Adresse e-mail", "Contact mail Agent", "MAIL"))
  val USER_INSTRUCTOR = Header("user.instructor", List("Instructeur"))
  val USER_GROUP_MANAGER = Header("user.admin-group", List("Responsable"))
  val USER_QUALITY = Header("user.quality", List("Qualité"))
  val USER_PHONE_NUMBER = Header("user.phone-number", List("Numéro de téléphone", "téléphone"))

  val GROUP_AREAS_IDS = Header("group.area-ids", List("Territoire", "DEPARTEMENTS"))
  val GROUP_ORGANISATION = Header("group.organisation", List("Organisation"))
  val GROUP_NAME = Header("group.name", List("Groupe", "Opérateur partenaire")) // "Nom de la structure labellisable"
  val GROUP_EMAIL = Header("group.email", List("Bal", "adresse mail générique"))

  val SEPARATOR = ";"

  val USER_HEADERS = List(
    USER_PHONE_NUMBER,
    USER_FIRST_NAME,
    USER_NAME,
    USER_EMAIL,
    USER_INSTRUCTOR,
    USER_GROUP_MANAGER
  )
  val USER_HEADER = USER_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  val GROUP_HEADERS = List(GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL, GROUP_AREAS_IDS)
  val GROUP_HEADER = GROUP_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  type UUIDGenerator = () => UUID
}
