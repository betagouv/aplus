package models.jsonApiModels

import constants.Constants
import helper.StringHelper
import java.util.UUID
import models.{Error, Mandat}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.Result
import play.api.mvc.Results.InternalServerError

object mandat {

  case class MandatGeneration(
      usagerPrenom: String,
      usagerNom: String,
      usagerBirthdate: String,
      creatorGroupId: Option[UUID],
  )

  case class SmsMandatInitiation(
      usagerPrenom: String,
      usagerNom: String,
      usagerBirthDate: String,
      usagerPhoneLocal: String
  )

  implicit val mandatIdReads: Reads[Mandat.Id] =
    implicitly[Reads[UUID]].map(Mandat.Id.apply)

  implicit val mandatIdWrites: Writes[Mandat.Id] =
    implicitly[Writes[UUID]].contramap((id: Mandat.Id) => id.underlying)

  import models.dataModels.SmsFormats._

  implicit val mandatWrites: Writes[Mandat] = Json.writes[Mandat]

  private val nameValidationRegex = """\p{L}[\p{L}\p{Z}\p{P}]{0,200}""".r

  private val looseDateValidationRegex = """[\p{L}\p{Z}\p{P}\p{N}]{1,100}""".r

  private val localPhoneRegex = """\d{10}""".r

  private val nameValidator: Reads[String] = implicitly[Reads[String]]
    .map(StringHelper.commonStringInputNormalization)
    .filter(JsonValidationError("Nom invalide"))(nameValidationRegex.matches)

  private val birthdateValidator: Reads[String] = implicitly[Reads[String]]
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
      .and((JsPath \ "birthDate").format[String](birthdateValidator))
      .and((JsPath \ "phoneNumber").format[String](phoneValidator))(
        SmsMandatInitiation.apply,
        unlift(SmsMandatInitiation.unapply)
      )

  /** Normalize and validate all fields for security. */
  implicit val mandatGenerationFormat: Reads[MandatGeneration] =
    (JsPath \ "usagerPrenom")
      .read[String](nameValidator)
      .and((JsPath \ "usagerNom").read[String](nameValidator))
      .and((JsPath \ "usagerBirthdate").read[String](birthdateValidator))
      .and((JsPath \ "creatorGroupId").readNullable[UUID])(
        MandatGeneration.apply _
      )

  def mandatJsonInternalServerError(error: Error): Result =
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
