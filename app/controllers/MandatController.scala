package controllers

import actions.LoginAction
import Operators.UserOperators
import helper.StringHelper
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.EventType.DeploymentDashboardUnauthorized
import models.{Area, Authorization, Organisation, UserGroup}
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import serializers.ApiModel._
import services.{
  EventService,
  MandatService,
  NotificationService,
  OrganisationService,
  SmsService,
  UserGroupService,
  UserService
}
import java.time.{LocalDate, ZonedDateTime}

import services.SmsId
import org.webjars.play.WebJarsUtil

import play.api.libs.json._
import play.api.libs.functional.syntax._

import services.{ApiSms, SmsSendRequest}
import models.mandat._

@Singleton
case class MandatController @Inject() (
    eventService: EventService,
    loginAction: LoginAction,
    mandatService: MandatService,
    notificationsService: NotificationService,
    smsService: SmsService,
    userService: UserService
)(implicit val ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with UserOperators {

  // envoyer le lien vers l'échange par email...

  // TODO: disallow sending to rapidly sms to the same number (if clicked to many times, or in case of browser error)

  // TODO: link with Application: verify creatorId == Mandat.userId
  // TODO: log everythin

  implicit val mandatIdReads: Reads[Mandat.Id] =
    implicitly[Reads[UUID]].map(Mandat.Id.apply)

  implicit val mandatIdWrites: Writes[Mandat.Id] =
    implicitly[Writes[UUID]].contramap((id: Mandat.Id) => id.underlying)

  implicit val mandatWrites: Writes[Mandat] = Json.writes[Mandat]

  // TODO: security
  val nameNormalizer: Reads[String] = implicitly[Reads[String]].map(x => x)

  /** Validate all fields for security. */
  implicit val smsMandatInitiationFormat: Format[SmsMandatInitiation] =
    (JsPath \ "prenom")
      .format[String](nameNormalizer)
      .and((JsPath \ "nom").format[String])
      .and((JsPath \ "birthDate").format[String])
      .and((JsPath \ "phoneNumber").format[String])(
        SmsMandatInitiation.apply,
        unlift(SmsMandatInitiation.unapply)
      )

  /** To have a somewhat consistent process:
    * - a SMS can fail for various reasons, even after having received a 2xx response from the api provider
    * - we assume many SMS could potentially be sent for one `Mandat` (to manage SMS failures)
    * - the `Mandat` is created but not signed until we have a response SMS
    * - we assume no other `Mandat` will be opened for the same end-user before receiving a SMS response
    * - a `Mandat` is allowed to be "dangling" (without a linked `Application`)
    * - once a `Mandat` has been linked to an `Application`, it is used, and cannot be reused
    *
    * This is an JSON API, mandats are initiated via Ajax calls.
    */
  def beginMandatSms: Action[JsValue] = loginAction(parse.json).async { implicit request =>
    request.body
      .validate[SmsMandatInitiation]
      .fold(
        errors => Future(BadRequest(Json.obj("message" -> JsError.toJson(errors)))),
        entity =>
          // Note: create `Mandat` first in DB due to failure cases:
          // OK: creating the entity in DB, then failing to send the SMS
          // NOT OK: sending the SMS, but failing to save the entity
          mandatService.createSmsMandat(entity, request.currentUser).flatMap { result =>
            val mandat = result.toOption.get
            val body =
              "En répondant OUI, vous donnez mandat à la structure XXX de faire à votre place les démarches pendant la durée de celles-ci."
            smsService
              .sendSms(
                SmsSendRequest(
                  originator = "Administration+", // TODO
                  body = body,
                  recipients =
                    mandat.enduserPhoneLocal.toList.map(ApiSms.localPhoneFranceToInternational)
                )
              )
              .flatMap { smsResult =>
                val sms = smsResult.toOption.get // TODO Unit
                mandatService.addSmsToMandat(mandat.id, sms).map { result =>
                  result.toOption.get // TODO: .get

                  notificationsService.mandatSmsSent(mandat.id, request.currentUser)
                  Ok(Json.toJson(mandat))
                }
              }
          }
      )

  }

  /** This is an `Action[AnyContent]` because we don't want to begin parsing
    * before validating the content.
    * TODO: if SMS received after Mandat is closed, send back 200, but log as error/warn
    */
  // TODO: all the .get
  def webhookSmsReceived: Action[AnyContent] = Action.async { implicit request =>
    smsService.smsReceivedCallback(request).flatMap { result =>
      val sms = result.toOption.get
      mandatService.addSmsResponse(sms).flatMap { result =>
        val mandatId = result.toOption.get
        mandatService.byId(mandatId).map { mandatResult =>
          val mandat = mandatResult.toOption.get.get
          val userOpt = userService.byId(mandat.userId)
          notificationsService.mandatSmsClosed(mandatId, userOpt.get)
          Ok
        }
      }
    }
  }

  def mandat(rawId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    // TODO: validate SmsId
    val id = Mandat.Id(rawId)
    mandatService.byId(id).map { result =>
      // TODO: anonymize
      // TODO: check user (security)
      val mandat = result.toOption.get.get
      Ok(views.html.showMandat(request.currentUser, request.rights)(mandat))
    }
  }

}
