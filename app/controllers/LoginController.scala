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
       Ok(views.html.loginHome(Left(None)))
     } else {
       emailFromRequest.flatMap(email => userService.byEmail(email)).fold {
         implicit val requestWithUserData = new RequestWithUserData(User.systemUser, Area.notApplicable, request)
         eventService.warn("UNKNOWN_EMAIL", s"Aucun compte actif à cette adresse mail $emailFromRequest")
         Redirect(routes.LoginController.login()).flashing("error" -> "Aucun compte actif à cette adresse email")
       } { user =>
         val loginToken = LoginToken.forUserId(user.id, tokenExpirationInMinutes, request.remoteAddress)
         tokenService.create(loginToken)
         val path = request.flash.get("path").getOrElse(routes.HomeController.index().url)
         val url = routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL()
         notificationService.newLoginRequest(url, path, user, loginToken)

         implicit val requestWithUserData = new RequestWithUserData(user, Area.notApplicable, request)
         eventService.info("GENERATE_TOKEN", s"Génére un token pour une connexion par email body=${request.body.asFormUrlEncoded.flatMap(_.get("email")).nonEmpty}&flash=${request.flash.get("email").nonEmpty}")

         Ok(views.html.loginHome(Left(Some(user))))
       }
     }
   }

  def magicLinkAntiConsumptionPage() = Action { implicit request =>
    (request.getQueryString("token"),request.getQueryString("path")) match {
      case (Some(token), Some(path)) =>
        Ok(views.html.loginHome(Right((token,path))))
      case _ =>
        TemporaryRedirect(routes.LoginController.login().url).flashing("error" -> "Il y a une erreur dans votre lien de connexion. Merci de contacter l'équipe Administration+")
    }
  }

  def disconnect() = Action { implicit request =>
      Redirect(routes.LoginController.login()).withNewSession
  }
}