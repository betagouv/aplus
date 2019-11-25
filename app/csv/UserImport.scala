package csv

import java.util.UUID

import extentions.{Operators, Time}
import models.User
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.Constraints.{maxLength, nonEmpty}

object UserImport {

  val HEADERS = List(USER_NAME_HEADER_PREFIX, USER_QUALITY_HEADER_PREFIX, USER_EMAIL_HEADER_PREFIX, HELPER_HEADER_PREFIX, INSTRUCTOR_HEADER_PREFIX, GROUP_MANAGER_HEADER_PREFIX)
  val HEADER = HEADERS.mkString(SEPARATOR)

  val userMappingForCVSImport: Mapping[User] = mapping(
    "id" -> default(uuid, undefined).transform[UUID](uuid => if(uuid == null) undefined else uuid,
      uuid => if(uuid == null) undefined else uuid),
    "key" -> default(nonEmptyText, "key"),
    USER_NAME_HEADER_PREFIX -> nonEmptyText.verifying(maxLength(100)),
    USER_QUALITY_HEADER_PREFIX -> nonEmptyText.verifying(maxLength(100)),
    USER_EMAIL_HEADER_PREFIX -> email.verifying(maxLength(200), nonEmpty),

    HELPER_HEADER_PREFIX -> optional(text.verifying(s => s.startsWith(HELPER_HEADER_PREFIX) || s.isEmpty))
      .transform[Boolean](os => os.exists(s=>Operators.not(s.isEmpty)), helper => if (helper) Some(HELPER_HEADER_PREFIX) else None),

    INSTRUCTOR_HEADER_PREFIX -> optional(text.verifying(s => s.startsWith(INSTRUCTOR_HEADER_PREFIX) || s.isEmpty))
      .transform[Boolean](os => os.exists(s=>Operators.not(s.isEmpty)), helper => if (helper) Some(INSTRUCTOR_HEADER_PREFIX) else None),

    "admin" -> ignored(false),
    "areas" -> default(list(uuid).verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
      List.empty[UUID]),
    "creationDate" -> ignored(null: DateTime),
    "hasAcceptedCharte" -> default(boolean, false),
    "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),

    GROUP_MANAGER_HEADER_PREFIX -> optional(text.verifying(s => s.startsWith(GROUP_MANAGER_HEADER_PREFIX) || s.isEmpty))
      .transform[Boolean](os => os.exists(s=>Operators.not(s.isEmpty)), helper => if (helper) Some(GROUP_MANAGER_HEADER_PREFIX) else None),

    "disabled" -> boolean,
    "expert" -> ignored(false),
    "groupIds" -> default(list(uuid), List()),
    "delegations" -> default(seq(tuple("name" -> nonEmptyText, "email" -> email))
      .transform[Map[String, String]](_.toMap, _.toSeq), Map.empty[String, String]),

    "cguAcceptationDate" -> optional(ignored(Time.now())),
    "newsletterAcceptationDate" -> optional(ignored(Time.now()))
  )(User.apply)(User.unapply)
}