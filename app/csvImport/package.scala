package object csvImport {

  val USER_NAME_LABEL: String = "Nom de l'utilisateur"
  val QUALITE_LABEL: String = "Qualité de l'utilisateur"
  val USER_EMAIL_LABEL: String = "Email de l'utilisateur"
  val HELPER_LABEL: String = "Aidant"
  val INSTRUCTOR_LABEL: String = "Instructeur"
  val ADMINISTRATOR_LABEL: String = "Administrateur"
  val DEPARTEMENT_LABEL: String = "Département du groupe"
  val ORGANISATION_LABEL: String = "Organisation du groupe"
  val DESCRIPTION_LABEL: String = "Description du groupe"
  val GROUP_NAME_LABEL: String = "Nom du groupe"
  val GROUP_EMAIL_LABEL: String = "Email du groupe"

  val SEPARATOR: String = ";"

  sealed trait CSVImportError

  object GROUP_NAME_UNDEFINED extends CSVImportError

  object DEPARTEMENT_UNDEFINED extends CSVImportError

  object QUALITE_UNDEFINED extends CSVImportError

  object EMAIL_UNDEFINED extends CSVImportError

  object NO_CONTENT extends CSVImportError

}