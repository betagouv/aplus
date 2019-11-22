package csvImport

import java.util.UUID

import extentions.Time
import models.User
import org.joda.time.DateTime
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

    "existingId" -> optional(uuid)
  )(UserImport.apply)(UserImport.unapply)

  val userMappingForCVSImport: Mapping[User] = mapping(
    "id" -> default(uuid, deadbeef),
    "key" -> default(nonEmptyText, "key"),
    USER_NAME_HEADER_PREFIX -> nonEmptyText.verifying(maxLength(100)),
    USER_QUALITY_HEADER_PREFIX -> nonEmptyText.verifying(maxLength(100)),
    USER_EMAIL_HEADER_PREFIX -> email.verifying(maxLength(200), nonEmpty),

    HELPER_HEADER_PREFIX -> text.verifying(s => s.startsWith(HELPER_HEADER_PREFIX) || s.isEmpty)
      .transform[Boolean](s => if (s.isEmpty) false else true, helper => if (helper) HELPER_HEADER_PREFIX else ""),

    INSTRUCTOR_HEADER_PREFIX -> text.verifying(s => s.startsWith(INSTRUCTOR_HEADER_PREFIX) || s.isEmpty)
      .transform[Boolean](s => if (s.isEmpty) false else true, helper => if (helper) INSTRUCTOR_HEADER_PREFIX else ""),

    "admin" -> ignored(false),
    "areas" -> default(list(uuid).verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
      List.empty[UUID]),
    "creationDate" -> ignored(null: DateTime),
    "hasAcceptedCharte" -> default(boolean, false),
    "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),

    GROUP_MANAGER_HEADER_PREFIX -> text.verifying(s => s.startsWith(GROUP_MANAGER_HEADER_PREFIX) || s.isEmpty)
      .transform[Boolean](s => if (s.isEmpty) false else true, helper => if (helper) GROUP_MANAGER_HEADER_PREFIX else ""),

    "disabled" -> boolean,
    "expert" -> ignored(false),
    "groupIds" -> default(list(uuid), List()),
    "delegations" -> default(seq(tuple("name" -> nonEmptyText, "email" -> email))
      .transform[Map[String, String]](_.toMap, _.toSeq), Map.empty[String, String]),

    "cguAcceptationDate" -> optional(ignored(Time.now())),
    "newsletterAcceptationDate" -> optional(ignored(Time.now()))
  )(User.apply)(User.unapply)
}