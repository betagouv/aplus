package controllers

import javax.inject.{Inject, Singleton}
import models.LoginToken
import org.webjars.play.WebJarsUtil
import play.api.mvc.InjectedController
import services.{NotificationService, TokenService, UserService}

@Singleton
class LoginController @Inject()(userService: UserService,
                                notificationService: NotificationService,
                                tokenService: TokenService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {

   def home() = Action { implicit request =>
     Ok(views.html.loginHome(None))
   }

   def login() = Action {implicit request =>
     request.body.asFormUrlEncoded.get.get("email").flatMap(_.headOption).flatMap(email => userService.byEmail(email)).fold {
       Redirect(routes.LoginController.home()).flashing("error" -> "Il n'y a pas d'utilisateur avec cette adresse email")
     } { user =>
       val loginToken = LoginToken.forUserId(user.id, 15)
       tokenService.create(loginToken)
       notificationService.newLoginRequest(request, user, loginToken)
       Ok(views.html.loginHome(Some(user)))
     }
   }

  def disconnect() = Action { implicit request =>
     Redirect(routes.LoginController.home()).withNewSession
  }
}