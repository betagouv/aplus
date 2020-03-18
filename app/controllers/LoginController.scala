package controllers

import actions.RequestWithUserData
import javax.inject.{Inject, Singleton}
import models.EventType.{GenerateToken, UnknownEmail}
import models.{Area, LoginToken, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Action, AnyContent, InjectedController, Request, Result}
import play.filters.csrf.CSRFCheck
import services.{EventService, NotificationService, TokenService, UserService}

@Singleton
class LoginController @Inject() (
    checkToken: CSRFCheck,
    userService: UserService,
    notificationService: NotificationService,
    tokenService: TokenService,
    configuration: play.api.Configuration,
    eventService: EventService
)(implicit val webJarsUtil: WebJarsUtil)
    extends InjectedController {

  private lazy val tokenExpirationInMinutes =
    configuration.underlying.getInt("app.tokenExpirationInMinutes")

  def login(): Action[AnyContent] = Action { implicit request =>
    innerLogin(None)
  }

  // Security: we check the CSRF token when the email is in the query here because play
  // only checks CSRF by default on POST actions
  // See also
  // https://github.com/playframework/playframework/blob/2.8.x/web/play-filters-helpers/src/main/scala/views/html/helper/CSRF.scala#L27
  def loginWithEmailInQueryString(): Action[AnyContent] = checkToken(
    Action { implicit request =>
      innerLogin(request.getQueryString("email"))
    }
  )

  private def innerLogin(
      emailFromQuery: Option[String]
  )(implicit request: Request[AnyContent]): Result = {
    val emailFromRequestOrQueryParamOrFlash: Option[String] = request.body.asFormUrlEncoded
      .flatMap(_.get("email").flatMap(_.headOption))
      .orElse(emailFromQuery)
      .orElse(request.flash.get("email"))
    emailFromRequestOrQueryParamOrFlash.fold {
      Ok(views.html.home(HomeController.HomeInnerPage.ConnectionForm))
    } { email =>
      userService
        .byEmail(email)
        .fold {
          implicit val requestWithUserData =
            new RequestWithUserData(User.systemUser, Area.notApplicable, request)
          eventService.log(UnknownEmail, s"Aucun compte actif à cette adresse mail $email")
          val message =
            """Aucun compte actif n'est associé à cette adresse e-mail.
              |Merci de vérifier qu'il s'agit bien de votre adresse professionnelle et nominative qui doit être sous la forme : prenom.nom@votre-structure.fr""".stripMargin
          Redirect(routes.LoginController.login())
            .flashing("error" -> message, "email-value" -> email)
        } { user: User =>
          val loginToken =
            LoginToken.forUserId(user.id, tokenExpirationInMinutes, request.remoteAddress)
          tokenService.create(loginToken)
          val path = request.flash.get("path").getOrElse(routes.HomeController.index().url)
          val url = routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL()
          notificationService.newLoginRequest(url, path, user, loginToken)

          implicit val requestWithUserData =
            new RequestWithUserData(user, Area.notApplicable, request)
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
            views.html.home(
              HomeController.HomeInnerPage
                .EmailSentFeedback(user, tokenExpirationInMinutes, successMessage)
            )
          )
        }
    }
  }

  def magicLinkAntiConsumptionPage() = Action { implicit request =>
    (request.getQueryString("token"), request.getQueryString("path")) match {
      case (Some(token), Some(path)) =>
        Ok(views.html.loginHome(Right((token, path)), tokenExpirationInMinutes))
      case _ =>
        TemporaryRedirect(routes.LoginController.login().url).flashing(
          "error" -> "Il y a une erreur dans votre lien de connexion. Merci de contacter l'équipe Administration+"
        )
    }
  }

  def disconnect() = Action {
    Redirect(routes.LoginController.login()).withNewSession
  }
}
