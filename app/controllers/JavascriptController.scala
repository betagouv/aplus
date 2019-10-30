package controllers

import javax.inject.Singleton
import play.api.http.MimeTypes
import play.api.mvc.InjectedController
import play.api.routing.JavaScriptReverseRouter


@Singleton
class JavascriptController() extends InjectedController{

  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.GroupController.deleteUnusedGroupById,
        routes.javascript.ApplicationController.all,
        routes.javascript.UserController.all)
    ).as(MimeTypes.JAVASCRIPT)
  }
}
