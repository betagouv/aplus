package csv

import java.util.UUID

import models.{Area, Organisation, UserGroup}
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.Constraints.{maxLength, nonEmpty}

object GroupImport {

  val HEADERS = List(TERRITORY_HEADER_PREFIX, GROUP_ORGANISATION_HEADER_PREFIX, GROUP_NAME_HEADER_PREFIX, GROUP_EMAIL_HEADER_PREFIX)
  val HEADER = HEADERS.mkString(SEPARATOR)

  // CSV import mapping
  val groupMappingForCSVImport: Mapping[UserGroup] = mapping(
    "id" -> default(uuid, undefined).transform[UUID](uuid => if (uuid == null) undefined else uuid,
      uuid => if (uuid == null) undefined else uuid),
    GROUP_NAME_HEADER_PREFIX -> nonEmptyText.verifying(maxLength(100)),
    "description" -> ignored(Option.empty[String]),
    "inseeCode" -> ignored(List.empty[String]),
    "creationDate" -> ignored(null: DateTime),
    "createByUserId" -> ignored(undefined),
    TERRITORY_HEADER_PREFIX -> nonEmptyText.transform[UUID]({ s =>
      s.split(",").map({ t: String =>
        val territory = canonizeTerritory(t.split(" ")(0))
        Area.all.find(a => canonizeTerritory(a.name.split(" ")(0)) == territory)
          .map(_.id)
          .getOrElse(Area.allArea.id)
      }).toList.head
    }, uuid => uuid.toString),

    GROUP_ORGANISATION_HEADER_PREFIX -> optional(nonEmptyText)
      .transform[Option[String]](_.flatMap(name => Organisation.fromName(name).map(_.shortName)), identity),
    GROUP_EMAIL_HEADER_PREFIX -> optional(email.verifying(maxLength(200), nonEmpty)),
  )(UserGroup.apply)(UserGroup.unapply)

  def canonizeTerritory(territory: String): String = territory.toLowerCase().replaceAll("[-'’]", "")
}
