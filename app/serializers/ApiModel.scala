package serializers

import java.util.UUID
import models.{Area, Organisation}
import play.api.libs.json._

object ApiModel {

  object implicits {
    implicit val areaFormat = Json.format[Area]

    implicit val organisationIdReads =
      implicitly[Reads[String]].map(Organisation.Id.apply)

    implicit val organisationIdWrites =
      implicitly[Writes[String]].contramap[Organisation.Id](_.id)

    implicit val organisationFormat = Json.format[Organisation]
  }

  // API model for the admin deploiement
  case class FranceServiceInstanceLine(
      nomFranceService: String,
      commune: String,
      departementName: String,
      departementCode: String,
      matchedGroup: Option[String],
      groupSize: Int,
      departementIsDone: Boolean,
      contactMail: Option[String],
      phone: Option[String]
  )

  implicit val franceServiceInstanceLineFormat = Json.format[FranceServiceInstanceLine]

  // Used for selecting group on the signup page
  case class SelectableGroup(id: UUID, name: String, organisationId: String, areaId: UUID)

  object SelectableGroup {
    implicit val format = Json.format[SelectableGroup]
  }

}
