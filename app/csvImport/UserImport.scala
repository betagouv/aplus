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

  val HEADERS = List(USER_NAME_HEADER_PREFIX, USER_QUALITY_HEADER_PREFIX, USER_EMAIL_HEADER_PREFIX, HELPER_HEADER_PREFIX, INSTRUCTOR_HEADER_PREFIX, GROUP_MANAGER_HEADER_PREFIX)
  val HEADER = HEADERS.mkString(SEPARATOR)

  val userMapping: Mapping[UserImport] = mapping(
    USER_NAME_HEADER_PREFIX -> nonEmptyText.verifying(maxLength(100)),
    USER_QUALITY_HEADER_PREFIX -> nonEmptyText,
    USER_EMAIL_HEADER_PREFIX -> email.verifying(maxLength(200), nonEmpty),
    HELPER_HEADER_PREFIX -> text.verifying(s => s.isEmpty || s.startsWith(HELPER_HEADER_PREFIX))
      .transform[Boolean](s => if (s.isEmpty) false else true, b => if (b) HELPER_HEADER_PREFIX else ""),

    INSTRUCTOR_HEADER_PREFIX -> text.verifying(s => s.isEmpty || s.startsWith(INSTRUCTOR_HEADER_PREFIX))
      .transform[Boolean](s => if (s.isEmpty) false else true, b => if (b) INSTRUCTOR_HEADER_PREFIX else ""),

    GROUP_MANAGER_HEADER_PREFIX -> text.verifying(s => s.isEmpty || s.startsWith(GROUP_MANAGER_HEADER_PREFIX))
      .transform[Boolean](s => if (s.isEmpty) false else true, b => if (b) GROUP_MANAGER_HEADER_PREFIX else ""),

    EXISTING_UUID -> optional(uuid)
  )(UserImport.apply)(UserImport.unapply)
}