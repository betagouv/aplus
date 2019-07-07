package controllers

import actions.RequestWithUserData
import extentions.UUIDHelper
import javax.inject.{Inject, Singleton}
import models.{Area, LoginToken, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{InjectedController, Request}
import services.{EventService, NotificationService, TokenService, UserService}

@Singleton
class LoginController @Inject()(userService: UserService,
                                notificationService: NotificationService,
                                tokenService: TokenService,
                                configuration: play.api.Configuration,
                                eventService: EventService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {
  private lazy val tokenExpirationInMinutes = configuration.underlying.getInt("app.tokenExpirationInMinutes")

  def login() = Action { implicit request =>
     val emailFromRequest: Option[String] = request.body.asFormUrlEncoded.flatMap(_.get("email")).flatMap(_.headOption).orElse(request.flash.get("email"))
     if(emailFromRequest.isEmpty) {
       Ok(views.html.loginHome(None))
     } else {
       emailFromRequest.flatMap(email => userService.byEmail(email)).fold {
         implicit val requestWithUserData = new RequestWithUserData(User.systemUser, Area.notApplicable, request)
         eventService.warn("UNKNOWN_EMAIL", s"Il n'y pas d'utilisateur avec l'email $emailFromRequest")
         Redirect(routes.LoginController.login()).flashing("error" -> "Il n'y a pas d'utilisateur avec cette adresse email")
       } { user =>
         val loginToken = LoginToken.forUserId(user.id, tokenExpirationInMinutes, request.remoteAddress)
         tokenService.create(loginToken)
         val url = request.flash.get("url").getOrElse(routes.HomeController.index().absoluteURL())
         notificationService.newLoginRequest(url, user, loginToken)

         implicit val requestWithUserData = new RequestWithUserData(user, Area.notApplicable, request)
         eventService.info("GENERATE_TOKEN", s"Génére un token pour une connexion par email")

         Ok(views.html.loginHome(Some(user)))
       }
     }
   }

  def disconnect() = Action { implicit request =>
      Redirect(routes.LoginController.login()).withNewSession
  }
}