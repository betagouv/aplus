package controllers

import actions.{LoginAction, RequestWithUserData}
import cats.implicits.{
  catsKernelStdMonoidForString,
  catsStdInstancesForOption,
  catsSyntaxOption,
  catsSyntaxOptionId,
  catsSyntaxTuple2Semigroupal
}
import javax.inject.{Inject, Singleton}
import models.EventType.{GenerateToken, UnknownEmail}
import models.LoginToken.forUserId
import models.{Application, Authorization, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Action, AnyContent, InjectedController}
import serializers.Keys
import services.{EventService, NotificationService, TokenService, UserService}
import views.home.LoginPanel
import views.home.LoginPanel.EmailSentFeedback
import views.html.home.page

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

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

  /** This serves the /login page with GET & POST
    * GET /login = used with '?email=xxx' as API so an email is sent automatically
    * POST /login = sends the magic link after having filled the "email" input on the login page
    *
    * Security Note:
    * when the email is in the query "?email=xxx", we do not check the CSRF token
    * because the API is used externally.
    */
  // maybe rename "generateMagicLink" ?
  def login: Action[AnyContent] =
    Action.async { implicit request =>
      val emailFromRequestOrQueryParamOrFlash: Option[String] = request.body.asFormUrlEncoded
        .flatMap(_.get("email").flatMap(_.headOption))
        .orElse(request.getQueryString(Keys.QueryParam.email))
        .orElse(request.flash.get("email"))
      emailFromRequestOrQueryParamOrFlash.fold {
        Future(Ok(views.html.home.page(LoginPanel.ConnectionForm)))
      } { email =>
        userService
          .byEmail(email)
          .flatMap(
            _.fold {
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
            } {
              case Left(user) =>
                val token = forUserId(user.id, tokenExpirationInMinutes, request.remoteAddress)
                tokenService.create(token)
                val url = routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL()
                val path = request.flash.get("path").getOrElse(routes.HomeController.index().url)
                val userName = (user.firstName, user.lastName).mapN(_ ++ "" ++ _).orEmpty
                notificationService.newLoginRequest(url, path, userName, user.email, token)
                val emailInBody = request.body.asFormUrlEncoded.flatMap(_.get("email")).nonEmpty
                val emailInFlash = request.flash.get("email").nonEmpty
                eventService.log(
                  request,
                  userName,
                  user.id,
                  GenerateToken,
                  s"Génère un token pour une connexion par email body=$emailInBody&flash=$emailInFlash",
                  Option.empty[Application],
                  Option.empty[User],
                  Option.empty[Throwable]
                )
                val message = request
                  .getQueryString(Keys.QueryParam.action)
                  .flatMap(actionName =>
                    actionName.some
                      .filter(_ == "sendemailback")
                      .map(_ =>
                        "Un nouveau lien de connexion vient de vous être envoyé par e-mail."
                      )
                  )
                successful(
                  Ok(
                    page(
                      LoginPanel.EmailSentFeedback(user.email, tokenExpirationInMinutes, message)
                    )
                  )
                )
              case Right(user) =>
                LoginAction.readUserRights(user).map { userRights =>
                  val loginToken =
                    forUserId(user.id, tokenExpirationInMinutes, request.remoteAddress)
                  tokenService.create(loginToken)
                  // Here we want to redirect some users to more useful pages:
                  // observer => /stats
                  val path: String = {
                    val tmpPath =
                      request.flash.get("path").getOrElse(routes.HomeController.index.url)
                    val shouldChangeObserverPath: Boolean =
                      Authorization.isObserver(userRights) &&
                        user.cguAcceptationDate.nonEmpty &&
                        ((tmpPath: String) == (routes.HomeController.index.url: String))
                    if (shouldChangeObserverPath) {
                      routes.ApplicationController.stats.url
                    } else {
                      tmpPath
                    }
                  }
                  val url = routes.LoginController.magicLinkAntiConsumptionPage.absoluteURL()
                  notificationService.newLoginRequest(url, path, user.name, user.email, loginToken)

                  implicit val requestWithUserData =
                    new RequestWithUserData(user, userRights, request)
                  val emailInBody = request.body.asFormUrlEncoded.flatMap(_.get("email")).nonEmpty
                  val emailInFlash = request.flash.get("email").nonEmpty
                  eventService.log(
                    GenerateToken,
                    s"Génère un token pour une connexion par email body=${emailInBody}&flash=${emailInFlash}"
                  )

                  val successMessage = request
                    .getQueryString(Keys.QueryParam.action)
                    .flatMap(actionName =>
                      if (actionName == "sendemailback")
                        Some("Un nouveau lien de connexion vient de vous être envoyé par e-mail.")
                      else
                        None
                    )
                  Ok(
                    page(
                      EmailSentFeedback(user.email, tokenExpirationInMinutes, successMessage)
                    )
                  )
                }
            }
          )
      }
    }

  def magicLinkAntiConsumptionPage: Action[AnyContent] =
    Action { implicit request =>
      (
        request.getQueryString(Keys.QueryParam.token),
        request.getQueryString(Keys.QueryParam.path)
      ) match {
        case (Some(token), Some(path)) =>
          Ok(views.html.loginHome(Right((token, path)), tokenExpirationInMinutes))
        case _ =>
          TemporaryRedirect(routes.LoginController.login.url).flashing(
            "error" -> "Il y a une erreur dans votre lien de connexion. Merci de contacter l'équipe Administration+"
          )
      }
    }

  def disconnect: Action[AnyContent] =
    Action {
      Redirect(routes.LoginController.login).withNewSession
    }

}
