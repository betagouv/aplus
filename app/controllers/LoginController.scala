package controllers

import actions.RequestWithUserData
import javax.inject.{Inject, Singleton}
import models.EventType.{GenerateToken, UnknownEmail}
import models.{Area, LoginToken, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.InjectedController
import services.{EventService, NotificationService, TokenService, UserService}

@Singleton
class LoginController @Inject()(userService: UserService,
                                notificationService: NotificationService,
                                tokenService: TokenService,
                                configuration: play.api.Configuration,
                                eventService: EventService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {
  private lazy val tokenExpirationInMinutes = configuration.underlying.getInt("app.tokenExpirationInMinutes")

  def login() = Action { implicit request =>
    val emailFromRequestOrQueryParamOrFlash: Option[String] = request.body.asFormUrlEncoded
      .flatMap(_.get("email").flatMap(_.headOption))
      .orElse(request.getQueryString("email"))
      .orElse(request.flash.get("email"))
    emailFromRequestOrQueryParamOrFlash.fold {
       Ok(views.html.loginHome(Left(None)))
     } { email =>
       userService.byEmail(email).fold {
         implicit val requestWithUserData = new RequestWithUserData(User.systemUser, Area.notApplicable, request)
         eventService.log(UnknownEmail, s"Aucun compte actif à cette adresse mail $email")
         val message =
           """Aucun compte actif n'est associé à cette adresse e-mail.
             |Merci de vérifier qu'il s'agit bien de votre adresse professionnelle et nominative qui doit être sous la forme : prenom.nom@votre-structure.fr""".stripMargin
         Redirect(routes.LoginController.login()).flashing("error" -> message, "email-value" -> email)
       } { user: User =>
         val loginToken = LoginToken.forUserId(user.id, tokenExpirationInMinutes, request.remoteAddress)
         tokenService.create(loginToken)
         val path = request.flash.get("path").getOrElse(routes.HomeController.index().url)
         val url = routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL()
         notificationService.newLoginRequest(url, path, user, loginToken)

         implicit val requestWithUserData = new RequestWithUserData(user, Area.notApplicable, request)
         eventService.log(GenerateToken, s"Génére un token pour une connexion par email body=${request.body.asFormUrlEncoded.flatMap(_.get("email")).nonEmpty}&flash=${request.flash.get("email").nonEmpty}")

         Ok(views.html.loginHome(Left(Some(user)))).flashing("email" -> email)
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