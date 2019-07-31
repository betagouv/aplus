package controllers

import java.util.UUID

import actions.LoginAction
import extentions.UUIDHelper
import javax.inject.{Inject, Singleton}
import models.Area
import org.webjars.play.WebJarsUtil
import play.api.mvc.{InjectedController, Request}
import services.EventService
import scala.collection.JavaConverters._

@Singleton
class AreaController @Inject()(loginAction: LoginAction,
                               eventService: EventService,
                               configuration: play.api.Configuration)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {
  private lazy val areasWithLoginByKey = configuration.underlying.getString("app.areasWithLoginByKey").split(",").flatMap(UUIDHelper.fromString)

  def change(areaId: UUID) = loginAction { implicit request =>
    if (!request.currentUser.areas.contains(areaId)) {
      eventService.warn("CHANGE_AREA_UNAUTHORIZED", s"Accès à la zone $areaId non autorisé")
      Unauthorized("Vous n'avez pas les droits suffisants pour accèder à cette zone. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
    } else {
      eventService.info("AREA_CHANGE", s"Changement vers la zone $areaId")
      val redirect = request.getQueryString("redirect").map(url => Redirect(url))
        .getOrElse(Redirect(routes.ApplicationController.all()))
      redirect.withSession(request.session - "areaId" + ("areaId" -> areaId.toString))
    }
  }

  def all = loginAction { implicit request =>
    Ok(views.html.allArea(request.currentUser, request.currentArea)(Area.all, areasWithLoginByKey))
  }
}