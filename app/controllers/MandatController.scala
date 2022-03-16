package controllers

import java.util.UUID

import actions.LoginAction
import cats.data.EitherT
import cats.syntax.all._
import constants.Constants
import controllers.Operators.UserOperators
import javax.inject.{Inject, Singleton}
import models.mandat.{Mandat, SmsMandatInitiation}
import models.{Error, EventType, Sms}
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.libs.json.{JsError, JsString, JsValue, Json}
import play.api.mvc.{Action, AnyContent, InjectedController, PlayBodyParsers}
import serializers.JsonFormats._
import services._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class MandatController @Inject() (
    bodyParsers: PlayBodyParsers,
    val configuration: Configuration,
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
    with Operators.Common
    with UserOperators {

  /** To have a somewhat consistent process:
    *   - a SMS can fail for various reasons, even after having received a 2xx response from the api
    *     provider
    *   - we assume many SMS could potentially be sent for one `Mandat` (to manage SMS failures)
    *   - the `Mandat` is created but not signed until we have a response SMS
    *   - we assume no other `Mandat` will be opened for the same end-user before receiving a SMS
    *     response
    *   - a `Mandat` is allowed to be "dangling" (without a linked `Application`)
    *   - once a `Mandat` has been linked to an `Application`, it is used, and cannot be reused
    *
    * This is a JSON API, mandats are initiated via Ajax calls.
    *
    * Note: protection against rapidly sending SMS to the same number is only performed client-side,
    * we might want to revisit that.
    */
  def beginMandatSms: Action[JsValue] = loginAction(parse.json).async { implicit request =>
    request.body
      .validate[SmsMandatInitiation]
      .fold(
        errors => {
          val errorMessage = helper.PlayFormHelper.prettifyJsonFormInvalidErrors(errors)
          eventService.log(EventType.MandatInitiationBySmsFormValidationError, s"$errorMessage")
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
          (
            for {
              mandat <- EitherT(mandatService.createSmsMandat(entity, request.currentUser))
              userGroups = userGroupService.byIds(request.currentUser.groupIds)
              recipient = Sms.PhoneNumber.fromLocalPhoneFrance(entity.usagerPhoneLocal)
              sms <- EitherT(
                smsService.sendMandatSms(recipient, mandat, request.currentUser, userGroups)
              )
              _ <- EitherT(mandatService.addSmsToMandat(mandat.id, sms))
            } yield (mandat, sms)
          ).value
            .map(
              _.fold(
                error => {
                  eventService.logError(error)
                  mandatJsonInternalServerError(error)
                },
                { case (mandat, sms) =>
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

  }

  /** This is an `Action[String]` because we need to parse both as bytes and json.
    *
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
                error.unsafeData,
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
                        error.unsafeData,
                        underlyingException = error.underlyingException
                      )
                      if (error.eventType === EventType.MandatNotFound)
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
                                error.unsafeData,
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
              case _: Error.Authorization | _: Error.Authentication =>
                Unauthorized(
                  s"Vous n'avez pas les droits suffisants pour voir ce mandat. " +
                    s"Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
                )
              case _: Error.Database | _: Error.SqlException |
                  _: Error.UnexpectedServerResponse | _: Error.Timeout | _: Error.MiscException =>
                InternalServerError(
                  s"Une erreur s'est produite sur le serveur. " +
                    "Celle-ci semble être temporaire. Nous vous invitons à réessayer plus tard. " +
                    s"Si cette erreur persiste, " +
                    s"vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
                )
            }
          },
          mandat => {
            eventService.log(EventType.MandatShowed, s"Mandat $rawId consulté")
            Ok(views.html.showMandat(request.currentUser, request.rights)(mandat))
              .withHeaders(CACHE_CONTROL -> "no-store")
          }
        )
      )
  }

}
