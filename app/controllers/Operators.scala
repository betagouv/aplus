package controllers

import java.util.UUID

import actions.RequestWithUserData
import cats.syntax.all._
import constants.Constants
import helper.BooleanHelper.not
import models.EventType._
import models.{Application, Authorization, Error, EventType, User, UserGroup}
import modules.AppConfig
import play.api.mvc.Results.{InternalServerError, NotFound, Unauthorized}
import play.api.mvc.{AnyContent, RequestHeader, Result, Results}
import scala.concurrent.{ExecutionContext, Future}
import services.{ApplicationService, EventService, UserGroupService, UserService}
import views.MainInfos

object Operators {

  trait Common {
    def config: AppConfig

    implicit def mainInfos(implicit request: RequestHeader): MainInfos = {
      val isDemo = request.domain.contains("localhost") ||
        request.domain.contains("demo")
      MainInfos(
        isDemo = isDemo,
        config = config
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
            .log(UserGroupNotFound, "Tentative d'accès à un groupe inexistant")
          Future(NotFound("Groupe inexistant."))
        })({ group: UserGroup => payload(group) })

    def asAdminOfGroupZone(group: UserGroup)(errorEventType: EventType, errorMessage: => String)(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin)) {
        eventService.log(errorEventType, errorMessage)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        if (group.areaIds.forall(request.currentUser.areas.contains)) {
          payload()
        } else {
          eventService.log(
            AdminOutOfRange,
            "L'administrateur n'est pas dans son périmètre de responsabilité"
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
        errorMessage: Option[String] = none,
        errorResult: Option[Result] = none
    )(
        payload: User => Future[Result]
    )(implicit request: RequestWithUserData[_]): Future[Result] =
      userService
        .byId(userId, includeDisabled)
        .fold({
          eventService.log(
            UserNotFound,
            errorMessage.getOrElse(s"Tentative d'accès à un utilisateur inexistant ($userId)"),
            involvesUser = userId.some
          )
          Future.successful(
            errorResult.getOrElse(NotFound(s"L'utilisateur n'existe pas."))
          )
        })({ user: User => payload(user) })

    def asUserWithAuthorization(authorizationCheck: Authorization.Check)(
        errorEventType: EventType,
        errorMessage: => String,
        errorResult: Option[Result] = none,
        errorInvolvesUser: Option[UUID] = none,
    )(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_]): Future[Result] =
      if (authorizationCheck(request.rights)) {
        payload()
      } else {
        eventService.log(errorEventType, errorMessage, involvesUser = errorInvolvesUser)
        Future.successful(
          errorResult.getOrElse(Unauthorized("Vous n'avez pas le droit de faire ça"))
        )
      }

    def asAdmin(errorEventType: EventType, errorMessage: => String)(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin)) {
        eventService.log(errorEventType, errorMessage)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        payload()
      }

    def asUserWhoSeesUsersOfArea(areaId: UUID)(errorEventType: EventType, errorMessage: => String)(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      // TODO: use only Authorization
      if (
        not(
          request.currentUser.canSeeUsersInArea(areaId) ||
            Authorization.isObserver(request.rights)
        )
      ) {
        eventService.log(errorEventType, errorMessage)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        payload()
      }

    def asAdminOfUserZone(user: User)(errorEventType: EventType, errorMessage: => String)(
        payload: () => Future[Result]
    )(implicit request: RequestWithUserData[_], ec: ExecutionContext): Future[Result] =
      if (not(request.currentUser.admin)) {
        eventService.log(errorEventType, errorMessage)
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        if (request.currentUser.areas.intersect(user.areas).isEmpty) {
          eventService.log(
            AdminOutOfRange,
            "L'administrateur n'est pas dans son périmètre de responsabilité"
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
          case _: Error.EntityNotFound | _: Error.RequirementFailed =>
            NotFound("Nous n'avons pas trouvé cette demande")
          case _: Error.Authorization | _: Error.Authentication =>
            Unauthorized(
              s"Vous n'avez pas les droits suffisants pour voir cette demande. " +
                s"Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
          case _: Error.Database | _: Error.SqlException | _: Error.UnexpectedServerResponse |
              _: Error.Timeout | _: Error.MiscException =>
            InternalServerError(
              s"Une erreur s'est produite sur le serveur. " +
                "Celle-ci semble être temporaire. Nous vous invitons à réessayer plus tard. " +
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
