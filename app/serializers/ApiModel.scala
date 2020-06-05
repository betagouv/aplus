package serializers

import java.time.ZonedDateTime
import play.api.libs.json._
import scala.util.Try

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
