package csvImport

import java.util.UUID

import models.{Area, UserGroup}
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.Constraints.{maxLength, nonEmpty}

object GroupImport {

  val HEADERS = List(TERRITORY_HEADER_PREFIX, GROUP_ORGANISATION_HEADER_PREFIX, GROUP_NAME_HEADER_PREFIX, GROUP_EMAIL_HEADER_PREFIX)
  val HEADER = HEADERS.mkString(SEPARATOR)

  // CSV import mapping
  val groupMappingForCSVImport: Mapping[UserGroup] = mapping(
    "id" -> ignored(deadbeef),
    GROUP_NAME_HEADER_PREFIX -> nonEmptyText.verifying(maxLength(100)),
    "description" -> ignored(Option.empty[String]),
    "inseeCode" -> ignored(List.empty[String]),
    "creationDate" -> ignored(null: DateTime),
    "createByUserId" -> ignored(deadbeef),
    TERRITORY_HEADER_PREFIX -> nonEmptyText.transform[UUID](s =>
      // TODO Allow light variations
      Area.all.find(_.name == s).map(_.id).getOrElse(deadbeef), uuid => uuid.toString),

    GROUP_ORGANISATION_HEADER_PREFIX -> optional(nonEmptyText), // TODO apply infer function
    GROUP_EMAIL_HEADER_PREFIX -> optional(email.verifying(maxLength(200), nonEmpty)),
  )(UserGroup.apply)(UserGroup.unapply)
}