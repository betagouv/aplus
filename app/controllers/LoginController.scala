package controllers

import actions.{LoginAction, RequestWithUserData}
import cats.syntax.all._
import helper.{StringHelper, Time}
import java.time.ZoneId
import javax.inject.{Inject, Singleton}
import models.EventType.{GenerateToken, UnknownEmail}
import models.{Authorization, EventType, LoginToken, User}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Action, AnyContent, InjectedController, Request}
import serializers.Keys
import services.{EventService, NotificationService, SignupService, TokenService, UserService}
import views.home.LoginPanel
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LoginController @Inject() (
    val config: AppConfig,
    userService: UserService,
    notificationService: NotificationService,
    tokenService: TokenService,
    eventService: EventService,
    signupService: SignupService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with Operators.Common {

  /** Security Note: when the email is in the query "?email=xxx", we do not check the CSRF token
    * because the API is used externally.
    */
  def login: Action[AnyContent] =
    Action.async { implicit request =>
      val emailFromRequestOrQueryParamOrFlash: Option[String] = request.body.asFormUrlEncoded
        .flatMap(_.get("email").flatMap(_.headOption))
        .orElse(request.getQueryString(Keys.QueryParam.email))
        .orElse(request.flash.get("email"))
        .map(_.trim)
      emailFromRequestOrQueryParamOrFlash.fold {
        Future(Ok(views.html.home.page(LoginPanel.ConnectionForm)))
      } { email =>
        if (email.isEmpty) {
          Future.successful(emailIsEmpty)
        } else {
          userService
            .byEmail(email, includeDisabled = true)
            .fold {
              signupService
                .byEmail(email)
                .map(
                  _.fold(
                    e => {
                      eventService.logErrorNoUser(e)
                      val message = "Une erreur interne est survenue. " +
                        "Celle-ci étant possiblement temporaire, " +
                        "nous vous invitons à réessayer plus tard."
                      Redirect(routes.LoginController.login)
                        .flashing("error" -> message, "email-value" -> email)
                    },
                    {
                      case None =>
                        accountDoesNotExist(email)
                      case Some(signup) =>
                        val loginToken =
                          LoginToken
                            .forSignupId(
                              signup.id,
                              config.tokenExpirationInMinutes,
                              request.remoteAddress
                            )
                        loginHappyPath(loginToken, signup.email, None)
                    }
                  )
                )
            } { user: User =>
              if (user.disabled)
                Future(accountDoesNotExist(email))
              else
                LoginAction.readUserRights(user).map { userRights =>
                  val loginToken =
                    LoginToken
                      .forUserId(user.id, config.tokenExpirationInMinutes, request.remoteAddress)
                  val requestWithUserData =
                    new RequestWithUserData(user, userRights, request)
                  loginHappyPath(loginToken, user.email, requestWithUserData.some)
                }
            }
        }
      }
    }

  private def emailIsEmpty = {
    val message = "Veuillez saisir votre adresse professionnelle"
    Redirect(routes.LoginController.login)
      .flashing("error" -> message)
  }

  private def accountDoesNotExist(email: String)(implicit request: Request[AnyContent]) = {
    eventService
      .logSystem(
        UnknownEmail,
        s"Aucun compte actif à cette adresse mail",
        s"Email '$email'".some
      )
    val message =
      """Aucun compte actif n’est associé à cette adresse e-mail.
        |Merci de vérifier qu’il s’agit bien de votre adresse professionnelle et nominative.""".stripMargin
    Redirect(routes.LoginController.login)
      .flashing("error" -> message, "email-value" -> email)
  }

  private def loginHappyPath(
      token: LoginToken,
      email: String,
      requestWithUserData: Option[RequestWithUserData[_]]
  )(implicit request: Request[AnyContent]) = {
    // Note: we have a small race condition here
    //       this should be OK almost always
    tokenService.create(token)
    // Here we want to redirect some users to more useful pages:
    // observer => /stats
    val path: String = {
      val tmpPath = request.flash.get("path").getOrElse(routes.HomeController.index.url)
      val isHome = tmpPath === routes.HomeController.index.url
      val shouldChangePathToStats: Boolean =
        requestWithUserData
          .map(data =>
            (Authorization.isObserver(data.rights) ||
              Authorization.isAreaManager(data.rights)) &&
              data.currentUser.cguAcceptationDate.nonEmpty &&
              isHome
          )
          .getOrElse(false)
      if (shouldChangePathToStats) {
        routes.ApplicationController.stats.url
      } else {
        tmpPath
      }
    }
    val smtpHost = notificationService.newMagicLinkEmail(
      requestWithUserData.map(_.currentUser.name),
      email,
      requestWithUserData.map(_.currentUser.timeZone).getOrElse(Time.timeZoneParis),
      token,
      pathToRedirectTo = path
    )
    val emailInBody =
      request.body.asFormUrlEncoded.flatMap(_.get("email")).nonEmpty
    val emailInFlash = request.flash.get("email").nonEmpty
    val logMessage =
      s"Génère un token pour une connexion par email via '$smtpHost'"
    val data = s"Body '$emailInBody' Flash '$emailInFlash'"
    requestWithUserData.fold(
      eventService.logSystem(GenerateToken, logMessage, data.some)
    ) { implicit userData =>
      eventService.log(GenerateToken, logMessage)
    }

    val successMessage = request
      .getQueryString(Keys.QueryParam.action)
      .flatMap(actionName =>
        if (actionName === "sendemailback")
          Some(
            "Un nouveau lien de connexion vient de vous être envoyé par email."
          )
        else
          None
      )
    Ok(
      views.html.home.page(
        LoginPanel.EmailSentFeedback(
          email,
          requestWithUserData.map(_.currentUser.timeZone).getOrElse(Time.timeZoneParis),
          successMessage
        )
      )
    )
  }

  def magicLinkAntiConsumptionPage: Action[AnyContent] =
    Action { implicit request =>
      (
        request.getQueryString(Keys.QueryParam.token),
        request.getQueryString(Keys.QueryParam.path)
      ) match {
        case (Some(token), Some(uncheckedPath)) =>
          val path =
            if (PathValidator.isValidPath(uncheckedPath)) uncheckedPath
            else {
              eventService.logSystem(
                EventType.LoginInvalidPath,
                "Redirection invalide après le login",
                s"Path '$uncheckedPath'".some
              )
              routes.HomeController.index.url
            }
          Ok(
            views.html.magicLinkAntiConsumptionPage(
              token = token,
              pathToRedirectTo = path
            )
          )
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
