package controllers

import javax.inject.{Inject, Singleton}
import models.{LoginToken, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{InjectedController, Request}
import services.{NotificationService, TokenService, UserService}

@Singleton
class LoginController @Inject()(userService: UserService,
                                notificationService: NotificationService,
                                tokenService: TokenService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {
  
   def login() = Action { implicit request =>
     val emailFromRequest: Option[String] = request.body.asFormUrlEncoded.flatMap(_.get("email")).flatMap(_.headOption).orElse(request.flash.get("email"))
     if(emailFromRequest.isEmpty) {
       Ok(views.html.loginHome(None))
     } else {
       emailFromRequest.flatMap(email => userService.byEmail(email)).fold {
         Redirect(routes.LoginController.login()).flashing("error" -> "Il n'y a pas d'utilisateur avec cette adresse email")
       } { user =>
         val loginToken = LoginToken.forUserId(user.id, 15, request.remoteAddress)
         tokenService.create(loginToken)
         val url = request.flash.get("url").getOrElse(routes.HomeController.index().absoluteURL())
         notificationService.newLoginRequest(url, user, loginToken)
         Ok(views.html.loginHome(Some(user)))
       }
     }
   }

  def disconnect() = Action { implicit request =>
      Redirect(routes.LoginController.login()).withNewSession
  }
}