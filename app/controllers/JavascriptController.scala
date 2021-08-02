package controllers

import javax.inject.Singleton
import play.api.http.MimeTypes
import play.api.mvc.InjectedController
import play.api.routing.JavaScriptReverseRouter

@Singleton
class JavascriptController() extends InjectedController {

  def javascriptRoutes =
    Action { implicit request =>
      Ok(
        JavaScriptReverseRouter("jsRoutes")(
          routes.javascript.ApiController.franceServiceDeployment,
          routes.javascript.ApiController.deploymentData,
          routes.javascript.GroupController.deleteUnusedGroupById,
          routes.javascript.GroupController.editGroup,
          routes.javascript.ApplicationController.all,
          routes.javascript.UserController.all,
          routes.javascript.UserController.allJson,
          routes.javascript.UserController.deleteUnusedUserById,
          routes.javascript.UserController.editUser
        )
      ).as(MimeTypes.JAVASCRIPT)
    }

}
