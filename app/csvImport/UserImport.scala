package csvImport

import java.util.UUID

import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.Constraints.{maxLength, nonEmpty}

case class UserImport(name: String,
                      qualite: String,
                      email: String,
                      helper: Boolean,
                      instructor: Boolean,
                      groupManager: Boolean,
                      existingId: Option[UUID] = None)

object UserImport {
  val HEADER: String = List(USER_NAME_LABEL, QUALITE_LABEL, USER_EMAIL_LABEL, HELPER_LABEL, INSTRUCTOR_LABEL, GROUP_MANAGER_LABEL).mkString(SEPARATOR)

  val userMaping: Mapping[UserImport] = mapping(
    "name" -> nonEmptyText.verifying(maxLength(100)),
    "qualite" -> nonEmptyText,
    "email" -> email.verifying(maxLength(200), nonEmpty),
    "helper" -> boolean,
    "instructor" -> boolean,
    "groupManager" -> boolean,
    "existingId" -> optional(uuid)
  )(UserImport.apply)(UserImport.unapply)

  def fromCSVLine(values: Map[String, String]): Either[CSVImportError, UserImport] = {
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
            groupManager = values.get(GROUP_MANAGER_LABEL).exists(!_.isEmpty)))
        })
      })
    })
  }
}