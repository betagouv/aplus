package controllers

import actions.LoginAction
import constants.Constants
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, Organisation, Sms}
import models.mandat.{Mandat, SmsMandatInitiation}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Action, AnyContent, InjectedController, PlayBodyParsers}
import play.api.libs.json.{JsError, JsString, JsValue, Json}
import scala.concurrent.{ExecutionContext, Future}
import serializers.JsonFormats._
import services.{
  EventService,
  MandatService,
  NotificationService,
  OrganisationService,
  SmsService,
  UserGroupService,
  UserService
}
import Operators.UserOperators

@Singleton
case class MandatController @Inject() (
    bodyParsers: PlayBodyParsers,
    eventService: EventService,
    loginAction: LoginAction,
    mandatService: MandatService,
    notificationsService: NotificationService,
    organisationService: OrganisationService,
    smsService: SmsService,
    userGroupService: UserGroupService,
    userService: UserService
)(implicit val ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with UserOperators {

  /** To have a somewhat consistent process:
    * - a SMS can fail for various reasons, even after having received a 2xx response from the api provider
    * - we assume many SMS could potentially be sent for one `Mandat` (to manage SMS failures)
    * - the `Mandat` is created but not signed until we have a response SMS
    * - we assume no other `Mandat` will be opened for the same end-user before receiving a SMS response
    * - a `Mandat` is allowed to be "dangling" (without a linked `Application`)
    * - once a `Mandat` has been linked to an `Application`, it is used, and cannot be reused
    *
    * This is a JSON API, mandats are initiated via Ajax calls.
    *
    * Note: protection against rapidly sending SMS to the same number is only performed
    *       client-side, we might want to revisit that.
    *
    */
  def beginMandatSms: Action[JsValue] = loginAction(parse.json).async { implicit request =>
    request.body
      .validate[SmsMandatInitiation]
      .fold(
        errors => {
          val errorMessage = helper.PlayFormHelper.prettifyJsonFormInvalidErrors(errors)
          eventService.log(EventType.MandatInitiationBySmsInvalid, s"$errorMessage")
          Future(
            BadRequest(
              Json.obj("message" -> JsString(errorMessage), "errors" -> JsError.toJson(errors))
            )
          )
        },
        entity =>
          // Note: we create the `Mandat` first in DB due to failure cases:
          // OK: creating the entity in DB, then failing to send the SMS
          // NOT OK: sending the SMS, but failing to save the entity
          mandatService
            .createSmsMandat(entity, request.currentUser)
            .flatMap(
              _.fold(
                error => {
                  eventService.logError(error)
                  Future(jsonInternalServerError)
                },
                mandat => {
                  val franceServiceGroups = userGroupService
                    .byIds(request.currentUser.groupIds)
                    .filter(group =>
                      (group.organisation: Option[Organisation.Id]) ==
                        (Some(Organisation.franceServicesId): Option[Organisation.Id])
                    )
                  val usagerInfos: String =
                    mandat.usagerPrenom.getOrElse("") + " " +
                      mandat.usagerNom.getOrElse("") + " " +
                      mandat.usagerBirthDate.getOrElse("")
                  val userInfos: String = request.currentUser.name
                  val groupInfos: String =
                    if (franceServiceGroups.size <= 0) {
                      eventService.log(
                        EventType.MandatInitiationBySmsWarn,
                        s"Lors de la création du SMS, l'utilisateur ${request.currentUser.id} " +
                          s"n'a pas de groupe FS"
                      )
                      ""
                    } else if (franceServiceGroups.size == 1) {
                      " de la structure " + franceServiceGroups.map(_.name).mkString(", ")
                    } else if (franceServiceGroups.size <= 3) {
                      " des structures " + franceServiceGroups.map(_.name).mkString(", ")
                    } else {
                      eventService.log(
                        EventType.MandatInitiationBySmsWarn,
                        s"Lors de la création du SMS, l'utilisateur ${request.currentUser.id} " +
                          s"est dans trop de groupes (${franceServiceGroups.size}) " +
                          "pour les inclure dans le SMS"
                      )
                      ""
                    }

                  val body =
                    s"En répondant OUI, vous assurez sur l'honneur que les informations communiquées ($usagerInfos) sont exactes et vous autorisez $userInfos$groupInfos, à utiliser vos données personnelles dans le cadre d'une demande et pour la durée d'instruction de celle-ci. Conformément aux conditions générales d'utilisation de la plateforme Adminitration+."
                  smsService
                    .sendSms(
                      body = body,
                      recipient = Sms.PhoneNumber.fromLocalPhoneFrance(entity.usagerPhoneLocal)
                    )
                    .flatMap(
                      _.fold(
                        error => {
                          eventService.logError(error)
                          Future(jsonInternalServerError)
                        },
                        sms =>
                          mandatService
                            .addSmsToMandat(mandat.id, sms)
                            .map(
                              _.fold(
                                error => {
                                  eventService.logError(error)
                                  jsonInternalServerError
                                },
                                _ => {
                                  eventService.log(
                                    EventType.MandatInitiationBySmsDone,
                                    s"Le mandat par SMS ${mandat.id.underlying} a été créé. " +
                                      s"Le SMS de demande ${sms.apiId.underlying} a été envoyé"
                                  )
                                  notificationsService.mandatSmsSent(mandat.id, request.currentUser)
                                  Ok(Json.toJson(mandat))
                                }
                              )
                            )
                      )
                    )
                }
              )
            )
      )

  }

  /** This is an `Action[String]` because we need to parse both as bytes and json.
    * Also, this is a webhook, only the returned status code is useful
    */
  // TODO: What if usager send an incorrect response the first time? close sms_thread only after some time has passed?
  def webhookSmsReceived: Action[String] = Action(bodyParsers.tolerantText).async {
    implicit request =>
      smsService
        .smsReceivedCallback(request)
        .flatMap(
          _.fold(
            error => {
              eventService.logSystem(
                error.eventType,
                error.description,
                underlyingException = error.underlyingException
              )
              Future(InternalServerError)
            },
            sms =>
              mandatService
                .addSmsResponse(sms)
                .flatMap(
                  _.fold(
                    error => {
                      eventService.logSystem(
                        error.eventType,
                        error.description,
                        underlyingException = error.underlyingException
                      )
                      if (error.eventType == EventType.MandatNotFound)
                        Future(Ok)
                      else
                        Future(InternalServerError)
                    },
                    mandatId =>
                      mandatService
                        .byIdAnonymous(mandatId)
                        .map(
                          _.fold(
                            error => {
                              eventService.logSystem(
                                error.eventType,
                                (s"Après ajout de la réponse par SMS ${sms.apiId.underlying}. " +
                                  error.description),
                                underlyingException = error.underlyingException
                              )
                              Ok
                            },
                            mandat => {
                              eventService.logSystem(
                                EventType.MandatBySmsResponseSaved,
                                s"Le mandat par SMS ${mandat.id.underlying} a reçu la réponse " +
                                  s"${sms.apiId.underlying}"
                              )

                              userService
                                .byId(mandat.userId)
                                .foreach(user =>
                                  notificationsService.mandatSmsClosed(mandatId, user)
                                )
                              Ok
                            }
                          )
                        )
                  )
                )
          )
        )
  }

  def mandat(rawId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    mandatService
      .byId(Mandat.Id(rawId), request.rights)
      .map(
        _.fold(
          error => {
            eventService.logError(error)
            error match {
              case _: Error.EntityNotFound =>
                NotFound("Nous n'avons pas trouvé ce mandat.")
              case _: Error.Authorization =>
                Unauthorized(
                  s"Vous n'avez pas les droits suffisants pour voir ce mandat. " +
                    s"Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
                )
              case _: Error.Database | _: Error.SqlException | _: Error.MiscException =>
                InternalServerError(
                  s"Une erreur s'est produite sur le serveur. " +
                    s"Si cette erreur persiste, " +
                    s"vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
                )
            }
          },
          mandat => {
            eventService.log(EventType.MandatShowed, s"Mandat $rawId consulté")
            Ok(views.html.showMandat(request.currentUser, request.rights)(mandat))
          }
        )
      )
  }

}
