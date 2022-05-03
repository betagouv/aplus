package controllers

import java.util.UUID

import actions.LoginAction
import cats.syntax.all._
import constants.Constants
import controllers.Operators.UserOperators
import helper.UUIDHelper
import javax.inject.{Inject, Singleton}
import models.EventType.{
  AllAreaUnauthorized,
  AreaChanged,
  ChangeAreaUnauthorized,
  DeploymentDashboardUnauthorized
}
import models._
import org.webjars.play.WebJarsUtil
import play.api.mvc.InjectedController
import serializers.Keys
import services.{EventService, UserGroupService, UserService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class AreaController @Inject() (
    loginAction: LoginAction,
    eventService: EventService,
    userService: UserService,
    userGroupService: UserGroupService,
    configuration: play.api.Configuration
)(implicit ec: ExecutionContext, val webJarsUtil: WebJarsUtil)
    extends InjectedController
    with Operators.Common
    with UserOperators {

  private lazy val areasWithLoginByKey: List[UUID] = configuration.underlying
    .getString("app.areasWithLoginByKey")
    .split(",")
    .flatMap(UUIDHelper.fromString)
    .toList

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
          Ok(
            views.html
              .allArea(request.currentUser, request.rights)(
                Area.all,
                areasWithLoginByKey,
                userGroups
              )
          )
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

  def franceServices =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        DeploymentDashboardUnauthorized,
        "Accès non autorisé aux France Services"
      ) { () =>
        Future.successful(
          Ok(views.franceServices.page(request.currentUser, request.rights))
        )
      }
    }

}
