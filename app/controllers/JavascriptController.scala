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
          routes.javascript.ApiController.franceServices,
          routes.javascript.ApiController.addFranceServices,
          routes.javascript.ApiController.updateFranceService,
          routes.javascript.ApiController.deleteFranceService,
          routes.javascript.GroupController.deleteUnusedGroupById,
          routes.javascript.GroupController.editGroup,
          routes.javascript.ApplicationController.applicationsAdmin,
          routes.javascript.ApplicationController.applicationsMetadata,
          routes.javascript.ApplicationController.show,
          routes.javascript.UserController.all,
          routes.javascript.UserController.deleteUnusedUserById,
          routes.javascript.UserController.editUser,
          routes.javascript.UserController.search,
        )
      ).as(MimeTypes.JAVASCRIPT)
    }

}
