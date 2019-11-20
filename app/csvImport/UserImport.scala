package csvImport

import java.util.UUID

case class UserImport(name: String,
                      qualite: String,
                      email: String,
                      helper: Boolean,
                      instructor: Boolean,
                      admin: Boolean,
                      existingId: Option[UUID] = None)

object UserImport {
  val HEADER: String = List(USER_NAME_LABEL, QUALITE_LABEL, USER_EMAIL_LABEL, HELPER_LABEL, INSTRUCTOR_LABEL, ADMINISTRATOR_LABEL).mkString(SEPARATOR)

  def fromCSVLine(values: Map[String, String]): Either[CSVImportError, UserImport] = {
    println(values.toList)
    values.get(USER_NAME_LABEL).fold[Either[CSVImportError, UserImport]]({
      Left[CSVImportError, UserImport](GROUP_NAME_UNDEFINED)
    })({ name: String =>
      values.get(QUALITE_LABEL).fold[Either[CSVImportError, UserImport]]({
        Left[CSVImportError, UserImport](QUALITE_UNDEFINED)
      })({ qualite: String =>
        values.get(USER_EMAIL_LABEL).fold[Either[CSVImportError, UserImport]]({
          Left[CSVImportError, UserImport](EMAIL_UNDEFINED)
        })({ email: String =>
          Right[CSVImportError, UserImport](UserImport.apply(name = name,
            qualite = qualite,
            email = email,
            helper = values.get(HELPER_LABEL).exists(!_.isEmpty),
            instructor = values.get(INSTRUCTOR_LABEL).exists(!_.isEmpty),
            admin = values.get(ADMINISTRATOR_LABEL).exists(!_.isEmpty)))
        })
      })
    })
  }
}