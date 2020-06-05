package serializers

import play.api.libs.json._

object ApiModel {

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

}
