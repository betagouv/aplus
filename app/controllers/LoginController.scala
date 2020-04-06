package controllers

import actions.{LoginAction, RequestWithUserData}
import javax.inject.{Inject, Singleton}
import models.EventType.{GenerateToken, UnknownEmail}
import models.{LoginToken, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Action, AnyContent, InjectedController}
import scala.concurrent.{ExecutionContext, Future}
import services.{EventService, NotificationService, TokenService, UserService}
import views.home.LoginPanel

@Singleton
class LoginController @Inject() (
    userService: UserService,
    notificationService: NotificationService,
    tokenService: TokenService,
    configuration: play.api.Configuration,
    eventService: EventService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController {

  private lazy val tokenExpirationInMinutes =
    configuration.underlying.getInt("app.tokenExpirationInMinutes")

  /** Security Note:
    * when the email is in the query "?email=xxx", we do not check the CSRF token
    * because the API is used externally.
    */
  def login: Action[AnyContent] = Action.async { implicit request =>
    val emailFromRequestOrQueryParamOrFlash: Option[String] = request.body.asFormUrlEncoded
      .flatMap(_.get("email").flatMap(_.headOption))
      .orElse(request.getQueryString("email"))
      .orElse(request.flash.get("email"))
    emailFromRequestOrQueryParamOrFlash.fold {
      Future(Ok(views.html.home.page(LoginPanel.ConnectionForm)))
    } { email =>
      userService
        .byEmail(email)
        .fold {
          // TODO: this should be removed
          val user = User.systemUser
          LoginAction.readUserRights(user).map { userRights =>
            implicit val requestWithUserData =
              new RequestWithUserData(user, userRights, request)
            eventService.log(UnknownEmail, s"Aucun compte actif à cette adresse mail $email")
            val message =
              """Aucun compte actif n'est associé à cette adresse e-mail.
                |Merci de vérifier qu'il s'agit bien de votre adresse professionnelle et nominative qui doit être sous la forme : prenom.nom@votre-structure.fr""".stripMargin
            Redirect(routes.LoginController.login)
              .flashing("error" -> message, "email-value" -> email)
          }
        } { user: User =>
          LoginAction.readUserRights(user).map { userRights =>
            val loginToken =
              LoginToken.forUserId(user.id, tokenExpirationInMinutes, request.remoteAddress)
            tokenService.create(loginToken)
            val path = request.flash.get("path").getOrElse(routes.HomeController.index().url)
            val url = routes.LoginController.magicLinkAntiConsumptionPage.absoluteURL()
            notificationService.newLoginRequest(url, path, user, loginToken)

            implicit val requestWithUserData =
              new RequestWithUserData(user, userRights, request)
            val emailInBody = request.body.asFormUrlEncoded.flatMap(_.get("email")).nonEmpty
            val emailInFlash = request.flash.get("email").nonEmpty
            eventService.log(
              GenerateToken,
              s"Génère un token pour une connexion par email body=${emailInBody}&flash=${emailInFlash}"
            )

            val successMessage = request
              .getQueryString("action")
              .flatMap(actionName =>
                if (actionName == "sendemailback")
                  Some("Un nouveau lien de connexion vient de vous être envoyé par e-mail.")
                else
                  None
              )
            Ok(
              views.html.home.page(
                LoginPanel.EmailSentFeedback(user, tokenExpirationInMinutes, successMessage)
              )
            )
          }
        }
    }
  }

  def magicLinkAntiConsumptionPage: Action[AnyContent] = Action { implicit request =>
    (request.getQueryString("token"), request.getQueryString("path")) match {
      case (Some(token), Some(path)) =>
        Ok(views.html.loginHome(Right((token, path)), tokenExpirationInMinutes))
      case _ =>
        TemporaryRedirect(routes.LoginController.login.url).flashing(
          "error" -> "Il y a une erreur dans votre lien de connexion. Merci de contacter l'équipe Administration+"
        )
    }
  }

  def disconnect: Action[AnyContent] = Action {
    Redirect(routes.LoginController.login).withNewSession
  }
}
