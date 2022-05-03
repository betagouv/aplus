package serializers

import constants.Constants
import helper.{StringHelper, UUIDHelper}
import java.util.UUID
import models.Error
import models.mandat.{Mandat, SmsMandatInitiation}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.Results.InternalServerError

object JsonFormats {
  implicit val jsonConfiguration = JsonConfiguration(naming = JsonNaming.SnakeCase)

  implicit val mapUUIDReads = new Reads[Map[UUID, String]] {

    def reads(jv: JsValue): JsResult[Map[UUID, String]] =
      JsSuccess(jv.as[Map[String, String]].map { case (k, v) =>
        UUIDHelper.fromString(k).get -> v.asInstanceOf[String]
      })

  }

  implicit val mapUUIDWrites = new Writes[Map[UUID, String]] {

    def writes(map: Map[UUID, String]): JsValue =
      Json.obj(map.map { case (s, o) =>
        val ret: (String, JsValueWrapper) = s.toString -> JsString(o)
        ret
      }.toSeq: _*)

  }

  implicit val mapUUIDFormat = Format(mapUUIDReads, mapUUIDWrites)

  //
  // Mandat
  //
  import models.dataModels.SmsFormats._

  implicit val mandatIdReads: Reads[Mandat.Id] =
    implicitly[Reads[UUID]].map(Mandat.Id.apply)

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

  def mandatJsonInternalServerError(error: Error) =
    error match {
      case _: Error.UnexpectedServerResponse | _: Error.Timeout =>
        InternalServerError(
          Json.obj(
            "message" -> JsString(
              "Un incident s’est produit chez notre fournisseur de SMS. " +
                "Celui-ci est temporaire mais peut durer 30 minutes, " +
                "nous vous invitons à réessayer plus tard ou à utiliser le mandat papier. " +
                s"Si le problème persiste, vous pouvez contacter l’équipe A+ : ${Constants.supportEmail}."
            )
          )
        )
      case _: Error.EntityNotFound | _: Error.Authorization | _: Error.Authentication |
          _: Error.RequirementFailed =>
        InternalServerError(
          Json.obj(
            "message" -> JsString(
              "Une erreur est survenue sur le serveur. " +
                s"Si le problème persiste, vous pouvez contacter l’équipe A+ : ${Constants.supportEmail}."
            )
          )
        )
      case _: Error.Database | _: Error.SqlException =>
        InternalServerError(
          Json.obj(
            "message" -> JsString(
              s"Une erreur s’est produite sur le serveur. " +
                "Celle-ci semble être temporaire. Nous vous invitons à réessayer plus tard ou à utiliser le mandat papier. " +
                s"Si cette erreur persiste, " +
                s"vous pouvez contacter l’équipe A+ : ${Constants.supportEmail}"
            )
          )
        )
      case _: Error.MiscException =>
        InternalServerError(
          Json.obj(
            "message" -> JsString(
              s"Une erreur s’est produite sur le serveur. " +
                "Celle-ci semble être temporaire. Nous vous invitons à réessayer plus tard ou à utiliser le mandat papier. " +
                s"Si cette erreur persiste, " +
                s"vous pouvez contacter l’équipe A+ : ${Constants.supportEmail}"
            )
          )
        )
    }

}
