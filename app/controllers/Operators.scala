package controllers

import java.util.UUID

import actions.RequestWithUserData
import constants.Constants
import helper.BooleanHelper.not
import models.EventType._
import models.{Application, EventType, User, UserGroup}
import play.api.mvc.Results.{NotFound, Unauthorized}
import play.api.mvc.{AnyContent, Result, Results}
import scala.concurrent.{ExecutionContext, Future}
import services.{ApplicationService, EventService, UserGroupService, UserService}

object Operators {

  trait GroupOperators {
    def groupService: UserGroupService
    def eventService: EventService

    import Results._

    def withGroup(
        groupId: UUID
    )(payload: UserGroup => Result)(implicit request: RequestWithUserData[AnyContent]): Result =
      groupService
        .groupById(groupId)
        .fold({
          eventService
            .log(UserGroupNotFound, description = "Tentative d'accès à un groupe inexistant.")
          NotFound("Groupe inexistant.")
        })({ group: UserGroup =>
          payload(group)
        })

    def asAdminOfGroupZone(group: UserGroup)(event: () => (EventType, String))(
        payload: () => Result
    )(implicit request: RequestWithUserData[AnyContent]): Result =
      if (not(request.currentUser.admin)) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        if (group.areaIds.forall(request.currentUser.areas.contains)) {
          payload()
        } else {
          eventService.log(
            AdminOutOfRange,
            description = "L'administrateur n'est pas dans son périmètre de responsabilité."
          )
          Unauthorized("Vous n'êtes pas en charge de la zone de ce groupe.")
        }
      }
  }

  trait UserOperators {
    def userService: UserService
    def eventService: EventService

    import Results._

    def withUser(userId: UUID, includeDisabled: Boolean = false)(
        payload: User => Result
    )(implicit request: RequestWithUserData[AnyContent]): Result =
      userService
        .byId(userId, includeDisabled)
        .fold({
          eventService
            .log(UserNotFound, description = "Tentative d'accès à un utilisateur inexistant.")
          NotFound("Utilisateur inexistant.")
        })({ user: User =>
          payload(user)
        })

    def asAdmin(event: () => (EventType, String))(
        payload: () => Result
    )(implicit request: RequestWithUserData[AnyContent]): Result =
      if (not(request.currentUser.admin)) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        payload()
      }

    def asAdminWhoSeesUsersOfArea(areaId: UUID)(event: () => (EventType, String))(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[AnyContent], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin) || not(request.currentUser.canSeeUsersInArea(areaId))) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        payload()
      }

    def asUserWhoSeesUsersOfArea(areaId: UUID)(event: () => (EventType, String))(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[AnyContent], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.canSeeUsersInArea(areaId))) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        payload()
      }

    def asAdminOfUserZone(user: User)(event: () => (EventType, String))(
        payload: () => Result
    )(implicit request: RequestWithUserData[AnyContent]): Result =
      if (not(request.currentUser.admin)) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        if (request.currentUser.areas.intersect(user.areas).isEmpty) {
          eventService.log(
            AdminOutOfRange,
            description = "L'administrateur n'est pas dans son périmètre de responsabilité."
          )
          Unauthorized("Vous n'êtes pas en charge de la zone de cet utilisateur.")
        } else {
          payload()
        }
      }
  }

  trait ApplicationOperators {
    def applicationService: ApplicationService
    def eventService: EventService

    def withApplication(
        applicationId: UUID
    )(
        payload: Application => Future[Result]
    )(implicit request: RequestWithUserData[AnyContent], ec: ExecutionContext): Future[Result] =
      applicationService
        .byId(
          applicationId,
          fromUserId = request.currentUser.id,
          rights = request.currentUser.rights
        )
        .fold({
          eventService.log(
            ApplicationNotFound,
            description = "Tentative d'accès à une application inexistante."
          )
          Future(NotFound("Application inexistante."))
        })({ application: Application =>
          if (not(application.canBeShowedBy(request.currentUser))) {
            eventService.log(
              ApplicationUnauthorized,
              description = "Tentative d'accès à une application non autorisé."
            )
            Future(
              Unauthorized(
                s"Vous n'avez pas les droits suffisants pour voir cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
              )
            )
          } else {
            payload(application)
          }
        })

  }
}
