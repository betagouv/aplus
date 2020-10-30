package serializers

import constants.Constants
import helper.{StringHelper, UUIDHelper}
import java.util.UUID

import models.mandat.{Mandat, SmsMandatInitiation}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.JsonConfiguration.Aux
import play.api.mvc.Results.InternalServerError

object JsonFormats {

  implicit val jsonConfiguration: Aux[Json.MacroOptions] =
    JsonConfiguration(naming = JsonNaming.SnakeCase)

  implicit val mapUUIDReads: Reads[Map[UUID, String]] = (jv: JsValue) =>
    JsSuccess(jv.as[Map[String, String]].map { case (k, v) =>
      UUIDHelper.fromString(k).get -> v.asInstanceOf[String]
    })

  implicit val mapUUIDWrites: Writes[Map[UUID, String]] = (map: Map[UUID, String]) =>
    Json.obj(map.map { case (s, o) =>
      val ret: (String, JsValueWrapper) = s.toString -> JsString(o)
      ret
    }.toSeq: _*)

  implicit val mapUUIDFormat: Format[Map[UUID, String]] = Format(mapUUIDReads, mapUUIDWrites)

  //
  // Mandat
  //
  import serializers.DataModel.SmsFormats._

  implicit val mandatIdReads: Reads[Mandat.Id] = implicitly[Reads[UUID]].map(Mandat.Id.apply)

  implicit val mandatIdWrites: Writes[Mandat.Id] =
    implicitly[Writes[UUID]].contramap((id: Mandat.Id) => id.underlying)

  implicit val mandatWrites: Writes[Mandat] = Json.writes[Mandat]

  private val nameValidationRegex = """\p{L}[\p{L}\p{Z}\p{P}]{0,200}""".r

  private val looseDateValidationRegex = """[\p{L}\p{Z}\p{P}\p{N}]{1,100}""".r

  private val localPhoneRegex = """\d{10}""".r

  private val nameValidator: Reads[String] = implicitly[Reads[String]]
    .map(StringHelper.commonStringInputNormalization)
    .filter(JsonValidationError("Nom invalide"))(nameValidationRegex.matches)

  private val birthDateValidator: Reads[String] = implicitly[Reads[String]]
    .map(StringHelper.commonStringInputNormalization)
    .filter(JsonValidationError("Date de naissance invalide"))(looseDateValidationRegex.matches)

  private val phoneValidator: Reads[String] = implicitly[Reads[String]]
    .map(StringHelper.stripSpaces)
    .filter(JsonValidationError("Téléphone invalide"))(localPhoneRegex.matches)

  /** Normalize and validate all fields for security. */
  implicit val smsMandatInitiationFormat: Format[SmsMandatInitiation] =
    (JsPath \ "prenom")
      .format[String](nameValidator)
      .and((JsPath \ "nom").format[String](nameValidator))
      .and((JsPath \ "birthDate").format[String](birthDateValidator))
      .and((JsPath \ "phoneNumber").format[String](phoneValidator))(
        SmsMandatInitiation.apply,
        unlift(SmsMandatInitiation.unapply)
      )

  def jsonInternalServerError =
    InternalServerError(
      Json.obj(
        "message" -> JsString(
          "Une erreur est survenue sur le serveur. " +
            s"Si le problème persiste, pouvez contacter l’équipe A+ : ${Constants.supportEmail}."
        )
      )
    )

}
