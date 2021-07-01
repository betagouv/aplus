package controllers

import java.util.UUID

import actions.RequestWithUserData
import cats.syntax.all._
import constants.Constants
import helper.BooleanHelper.not
import models.EventType._
import models.{Application, Authorization, Error, EventType, User, UserGroup}
import play.api.Configuration
import play.api.mvc.Results.{InternalServerError, NotFound, Unauthorized}
import play.api.mvc.{AnyContent, RequestHeader, Result, Results}
import scala.concurrent.{ExecutionContext, Future}
import services.{ApplicationService, EventService, UserGroupService, UserService}
import views.MainInfos

object Operators {

  trait Common {
    def configuration: Configuration

    implicit def mainInfos(implicit request: RequestHeader): MainInfos = {
      val isDemo = request.domain.contains("localhost") ||
        request.domain.contains("demo")
      MainInfos(
        isDemo = isDemo,
        topHeaderWarningMessage = configuration.getOptional[String]("app.topHeaderWarningMessage")
      )
    }

  }

  trait GroupOperators {
    def groupService: UserGroupService
    def eventService: EventService

    import Results._

    def withGroup(
        groupId: UUID
    )(
        payload: UserGroup => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      groupService
        .groupById(groupId)
        .fold({
          eventService
            .log(UserGroupNotFound, description = "Tentative d'accès à un groupe inexistant.")
          Future(NotFound("Groupe inexistant."))
        })({ group: UserGroup => payload(group) })

    def asAdminOfGroupZone(group: UserGroup)(event: () => (EventType, String))(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin)) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        if (group.areaIds.forall(request.currentUser.areas.contains)) {
          payload()
        } else {
          eventService.log(
            AdminOutOfRange,
            description = "L'administrateur n'est pas dans son périmètre de responsabilité."
          )
          Future(Unauthorized("Vous n'êtes pas en charge de la zone de ce groupe."))
        }
      }

  }

  trait UserOperators {
    def userService: UserService
    def eventService: EventService

    import Results._

    def withUser(
        userId: UUID,
        includeDisabled: Boolean = false,
        errorMessage: String = "Tentative d'accès à un utilisateur inexistant",
        errorResult: Option[Result] = none
    )(
        payload: User => Future[Result]
    )(implicit request: RequestWithUserData[_]): Future[Result] =
      userService
        .byId(userId, includeDisabled)
        .fold({
          eventService.log(UserNotFound, description = errorMessage)
          Future.successful(
            errorResult.getOrElse(NotFound("Utilisateur inexistant"))
          )
        })({ user: User => payload(user) })

    def asUserWithAuthorization(authorizationCheck: Authorization.Check)(
        errorEvent: () => (EventType, String),
        errorResult: Option[Result] = none
    )(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_]): Future[Result] =
      if (authorizationCheck(request.rights)) {
        payload()
      } else {
        val (eventType, description) = errorEvent()
        eventService.log(eventType, description = description)
        Future.successful(
          errorResult.getOrElse(Unauthorized("Vous n'avez pas le droit de faire ça"))
        )
      }

    def asAdmin(event: () => (EventType, String))(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin)) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        payload()
      }

    def asAdminWhoSeesUsersOfArea(areaId: UUID)(event: () => (EventType, String))(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin) || not(request.currentUser.canSeeUsersInArea(areaId))) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        payload()
      }

    def asUserWhoSeesUsersOfArea(areaId: UUID)(event: () => (EventType, String))(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      // TODO: use only Authorization
      if (
        not(
          request.currentUser.canSeeUsersInArea(areaId) ||
            Authorization.isObserver(request.rights)
        )
      ) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        payload()
      }

    def asAdminOfUserZone(user: User)(event: () => (EventType, String))(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin)) {
        val (eventType, description) = event()
        eventService.log(eventType, description = description)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        if (request.currentUser.areas.intersect(user.areas).isEmpty) {
          eventService.log(
            AdminOutOfRange,
            description = "L'administrateur n'est pas dans son périmètre de responsabilité."
          )
          Future(Unauthorized("Vous n'êtes pas en charge de la zone de cet utilisateur."))
        } else {
          payload()
        }
      }

  }

  trait ApplicationOperators {
    def applicationService: ApplicationService
    def eventService: EventService

    private def manageApplicationError(applicationId: UUID, error: Error)(implicit
        request: RequestWithUserData[_],
        ec: ExecutionContext
    ): Future[Result] = {
      val result =
        error match {
          case _: Error.EntityNotFound =>
            NotFound("Nous n'avons pas trouvé cette demande")
          case _: Error.Authorization | _: Error.Authentication =>
            Unauthorized(
              s"Vous n'avez pas les droits suffisants pour voir cette demande. " +
                s"Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
          case _: Error.Database | _: Error.SqlException | _: Error.MiscException =>
            InternalServerError(
              s"Une erreur s'est produite sur le serveur. " +
                s"Si cette erreur persiste, " +
                s"vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
        }
      eventService.logError(error)
      Future(result)
    }

    def withApplication(
        applicationId: UUID
    )(
        payload: Application => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      applicationService
        .byId(
          applicationId,
          userId = request.currentUser.id,
          rights = request.rights
        )
        .flatMap(
          _.fold(
            error => manageApplicationError(applicationId, error),
            { application: Application =>
              payload(application)
            }
          )
        )

  }

}
