package controllers

import java.util.UUID

import actions.LoginAction
import javax.inject.{Inject, Singleton}
import models.Area
import org.webjars.play.WebJarsUtil
import play.api.mvc.{InjectedController, Request}
import services.EventService

@Singleton
class AreaController @Inject()(loginAction: LoginAction,
                               eventService: EventService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {
  
  def change(areaId: UUID) = loginAction { implicit request =>
    if(!request.currentUser.areas.contains(areaId)) {
      eventService.warn("CHANGE_AREA_UNAUTHORIZED", s"Accès à la zone $areaId non autorisé")
      Unauthorized("Vous n'avez pas les droits suffisants pour accèder à cette zone. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
    } else {
      eventService.info("AREA_CHANGE", s"Changement vers la zone $areaId")
      Redirect(routes.AreaController.all()).withSession(request.session - "areaId" + ("areaId" -> areaId.toString))
    }
  }


  def all = loginAction { implicit request =>
    Ok(views.html.allArea(request.currentUser, request.currentArea)(Area.all))
  }
}