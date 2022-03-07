package controllers

import actions.LoginAction
import cats.syntax.all._
import constants.Constants
import controllers.Operators.UserOperators
import javax.inject.{Inject, Singleton}
import models.EventType.{
  AllAreaUnauthorized,
  AreaChanged,
  ChangeAreaUnauthorized,
  DeploymentDashboardUnauthorized
}
import models._
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.mvc.InjectedController
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys
import services.{EventService, UserGroupService, UserService}

@Singleton
case class AreaController @Inject() (
    config: AppConfig,
    loginAction: LoginAction,
    eventService: EventService,
    userService: UserService,
    userGroupService: UserGroupService,
)(implicit ec: ExecutionContext, val webJarsUtil: WebJarsUtil)
    extends InjectedController
    with Operators.Common
    with UserOperators {

  def all =
    loginAction.async { implicit request =>
      if (!request.currentUser.admin && !request.currentUser.groupAdmin) {
        eventService.log(
          AllAreaUnauthorized,
          "Accès non autorisé pour voir la page des territoires"
        )
        Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
      } else {
        val userGroupsFuture: Future[List[UserGroup]] = if (request.currentUser.admin) {
          userGroupService.byAreas(request.currentUser.areas)
        } else {
          Future(userGroupService.byIds(request.currentUser.groupIds))
        }
        userGroupsFuture.map { userGroups =>
          Ok(views.html.allArea(request.currentUser, request.rights)(Area.all, userGroups))
        }
      }
    }

  def deploymentDashboard =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        DeploymentDashboardUnauthorized,
        "Accès non autorisé au dashboard de déploiement"
      ) { () =>
        Future.successful(Ok(views.html.deploymentDashboard(request.currentUser, request.rights)))
      }
    }

  def franceServiceDeploymentDashboard =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        DeploymentDashboardUnauthorized,
        "Accès non autorisé au dashboard de déploiement"
      ) { () =>
        Future.successful(
          Ok(views.html.franceServiceDeploymentDashboard(request.currentUser, request.rights))
        )
      }
    }

}
