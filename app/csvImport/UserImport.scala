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
  val HEADER: String = List(USER_NAME_HEADER_PREFIX, USER_QUALITY_HEADER_PREFIX, USER_EMAIL_HEADER_PREFIX, HELPER_HEADER_PREFIX, INSTRUCTOR_HEADER_PREFIX, GROUP_MANAGER_HEADER_PREFIX).mkString(SEPARATOR)

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
    searchByPrefix(USER_NAME_HEADER_PREFIX, values).fold[Either[CSVImportError, UserImport]]({
      Left[CSVImportError, UserImport](GROUP_NAME_UNDEFINED)
    })({ name: String =>
      searchByPrefix(USER_QUALITY_HEADER_PREFIX, values).fold[Either[CSVImportError, UserImport]]({
        Left[CSVImportError, UserImport](QUALITE_UNDEFINED)
      })({ qualite: String =>
        searchByPrefix(USER_EMAIL_HEADER_PREFIX, values).fold[Either[CSVImportError, UserImport]]({
          Left[CSVImportError, UserImport](EMAIL_UNDEFINED)
        })({ email: String =>
          Right[CSVImportError, UserImport](UserImport.apply(name = name,
            qualite = qualite,
            email = email,
            helper = searchByPrefix(HELPER_HEADER_PREFIX, values).exists(!_.isEmpty),
            instructor = searchByPrefix(INSTRUCTOR_HEADER_PREFIX, values).exists(!_.isEmpty),
            groupManager = searchByPrefix(GROUP_MANAGER_HEADER_PREFIX, values).exists(!_.isEmpty)))
        })
      })
    })
  }
}