package controllers

import java.util.UUID

import actions.LoginAction
import javax.inject.{Inject, Singleton}
import models.Area
import org.webjars.play.WebJarsUtil
import play.api.mvc.{InjectedController, Request}
import play.twirl.api.Html
import services.EventService

@Singleton
class AreaController @Inject()(loginAction: LoginAction,
                               eventService: EventService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {

  def changeArea(areaId: UUID) = loginAction {  implicit request =>
    if(request.currentUser.areas.nonEmpty && !request.currentUser.areas.contains(areaId)) {
      eventService.warn("CHANGE_AREA_UNAUTHORIZED", s"Accès à la zone $areaId non autorisé")
      Unauthorized("Vous n'avez pas les droits suffisants pour accèder à cette zone. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
    } else {
      eventService.info("AREA_CHANGE", s"Changement vers la zone $areaId")
      Redirect(routes.ApplicationController.all()).withSession(request.session - "areaId" + ("areaId" -> areaId.toString))
    }
  }

  def all = loginAction { implicit request =>
    val show = Area.all.map(area => s"<li>${area.name} : ${area.id}</li>").mkString
    Ok(views.html.main(request.currentUser, request.currentArea)("Liste des territoires")(Html(""))(Html(s"<ul>${show}</ul>")))
  }
}