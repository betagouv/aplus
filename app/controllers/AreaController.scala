package controllers

import java.util.UUID

import actions.LoginAction
import constants.Constants
import extentions.Operators.UserOperators
import extentions.UUIDHelper
import javax.inject.{Inject, Singleton}
import models.EventType.AreaChange
import models.{Area, Organisation, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.InjectedController
import services.{EventService, UserGroupService, UserService}

import scala.concurrent.ExecutionContext

@Singleton
case class AreaController @Inject()(loginAction: LoginAction,
                               eventService: EventService,
                               userService: UserService,
                               userGroupService: UserGroupService,
                               configuration: play.api.Configuration)(implicit val webJarsUtil: WebJarsUtil, ec: ExecutionContext) extends InjectedController with UserOperators {
  private lazy val areasWithLoginByKey = configuration.underlying.getString("app.areasWithLoginByKey").split(",").flatMap(UUIDHelper.fromString)

  @deprecated
  def change(areaId: UUID) = loginAction { implicit request =>
    if (!request.currentUser.areas.contains(areaId)) {
      eventService.warn("CHANGE_AREA_UNAUTHORIZED", s"Accès à la zone $areaId non autorisé")
      Unauthorized(s"Vous n'avez pas les droits suffisants pour accèder à cette zone. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
    } else {
      eventService.log(AreaChange, s"Changement vers la zone $areaId")
      val redirect = request.getQueryString("redirect").map(url => Redirect(url))
        .getOrElse(Redirect(routes.ApplicationController.myApplications()))
      redirect.withSession(request.session - "areaId" + ("areaId" -> areaId.toString))
    }
  }

  def all = loginAction { implicit request =>
    if (!request.currentUser.admin && !request.currentUser.groupAdmin) {
      eventService.warn("ALL_AREA_UNAUTHORIZED", s"Accès non autorisé pour voir la page des territoires")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      val userGroups = if (request.currentUser.admin) {
        userGroupService.allGroupByAreas(request.currentUser.areas)
      } else { 
        userGroupService.byIds(request.currentUser.groupIds)
      }
      Ok(views.html.allArea(request.currentUser)(Area.all, areasWithLoginByKey, userGroups))
    }
  }

  def deploymentDashboard = loginAction {  implicit  request =>
    asAdmin { () =>
      "DEPLOYMENT_DASHBOARD_UNAUTHORIZED" -> s"Accès non autorisé au dashboard de déploiement"
    } { () =>
      val userGroups = userGroupService.allGroups
      val users = userService.all

      def usersIn(area: Area, organisationSet: Set[Organisation]): List[User] = for {
        group <- userGroups.filter(group => group.areaIds.contains(area.id)
          && organisationSet.map(_.shortName).exists(group.organisationSetOrDeducted.contains))
        user <- users if user.groupIds.contains(group.id)
      } yield user

      val data = for {
        area <- request.currentUser.areas.flatMap(Area.fromId)
      } yield {
        val organisationMap: List[(Set[Organisation], Int)] = for {
          organisations <- Organisation.organisationGrouping
          users = usersIn(area, organisations)
          userSum = users.count(_.instructor)
        } yield organisations -> userSum
        (area, organisationMap, organisationMap.count(_._2 > 0))
      }
      
      Ok(views.html.deploymentDashboard(request.currentUser)(data))
    }
  }
}
