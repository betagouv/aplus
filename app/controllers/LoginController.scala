package controllers

import actions.{LoginAction, RequestWithUserData}
import cats.effect.IO
import cats.syntax.all._
import helper.PlayFormHelpers.formErrorsLog
import helper.ScalatagsHelpers.writeableOf_Modifier
import helper.Time
import java.time.Instant
import javax.inject.{Inject, Singleton}
import models.{Authorization, EventType, LoginToken, User}
import models.forms.{PasswordChange, PasswordCredentials, PasswordRecovery}
import models.EventType.{GenerateToken, UnknownEmail}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Request, Result}
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys
import services.{
  EventService,
  NotificationService,
  PasswordService,
  ServicesDependencies,
  SignupService,
  TokenService,
  UserService
}
import views.home.LoginPanel

@Singleton
class LoginController @Inject() (
    val config: AppConfig,
    val controllerComponents: ControllerComponents,
    dependencies: ServicesDependencies,
    userService: UserService,
    notificationService: NotificationService,
    tokenService: TokenService,
    eventService: EventService,
    passwordService: PasswordService,
    signupService: SignupService,
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends BaseController
    with I18nSupport
    with Operators.Common {

  import dependencies.ioRuntime

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
        Future.successful(Ok(views.html.home.page(LoginPanel.ConnectionForm)))
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
                        magicLinkAuth(loginToken, signup.email, None)
                    }
                  )
                )
            } { (user: User) =>
              if (user.disabled)
                Future(accountDoesNotExist(email))
              else if (user.passwordActivated && request.getQueryString("nopassword").isEmpty)
                // 303 is supposed to be the correct code after POST
                // Just random knowledge here, since Play `Redirect` is 303 by default
                Future.successful(
                  addingPasswordEmailToSession(user.email.some)(
                    SeeOther(routes.LoginController.passwordPage.url)
                  )
                )
              else
                LoginAction.readUserRights(user).map { userRights =>
                  val loginToken =
                    LoginToken
                      .forUserId(user.id, config.tokenExpirationInMinutes, request.remoteAddress)
                  // userSession = none since there are no session around
                  val requestWithUserData =
                    new RequestWithUserData(user, userRights, none, request)
                  magicLinkAuth(loginToken, user.email, requestWithUserData.some)
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

  private def magicLinkAuth(
      token: LoginToken,
      email: String,
      requestWithUserData: Option[RequestWithUserData[_]]
  )(implicit request: Request[AnyContent]): Result = {
    // Note: we have a small race condition here
    //       this should be OK almost always
    val _ = tokenService.create(token)
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
    val logMessage =
      s"Génère un token pour une connexion par email via '$smtpHost'"
    val logData = {
      val emailInBody =
        request.body.asFormUrlEncoded.flatMap(_.get("email")).nonEmpty
      val emailInFlash = request.flash.get("email").nonEmpty
      s"Body '$emailInBody' Flash '$emailInFlash'"
    }
    requestWithUserData.fold(
      eventService.logSystem(GenerateToken, logMessage, logData.some)
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

  def passwordPage: Action[AnyContent] =
    Action { implicit request =>
      val email: String = request.session.get(Keys.Session.passwordEmail).getOrElse("")
      val form = PasswordCredentials.form.fill(PasswordCredentials(email, ""))
      eventService.logSystem(
        EventType.PasswordPageShowed,
        "Visualise la page de connexion par mot de passe",
        email.some
      )
      Ok(views.password.loginPage(form))
    }

  def tryLoginByPassword: Action[AnyContent] =
    Action.async { implicit request =>
      val errorTitle =
        "Erreur : Vos identifiant et mot de passe ne correspondent pas, merci de réessayer."
      val errorMessage =
        "Cette erreur peut être due à une adresse électronique ou un mot de passe invalide, ou alors un compte inexistant ou désactivé."
      PasswordCredentials.form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            val email = formWithErrors("email").value
            eventService.logSystem(
              EventType.PasswordFormValidationError,
              s"Erreurs dans le formulaire de connexion par mot de passe : ${formErrorsLog(formWithErrors)}",
              email
            )
            Future.successful(
              addingPasswordEmailToSession(email)(
                BadRequest(
                  views.password
                    .loginPage(formWithErrors, errorMessage = (errorTitle, errorMessage).some)
                )
              )
            )
          },
          credentials =>
            passwordService
              .verifyPassword(credentials.email, credentials.password.toArray)
              .flatMap(
                _.fold(
                  e => {
                    eventService.logErrorNoUser(e)
                    // Note: we remove the password on purpose here
                    val form =
                      PasswordCredentials.form.fill(PasswordCredentials(credentials.email, ""))
                    Future.successful(
                      addingPasswordEmailToSession(credentials.email.some)(
                        BadRequest(
                          views.password
                            .loginPage(form, errorMessage = (errorTitle, errorMessage).some)
                        )
                      )
                    )
                  },
                  user =>
                    LoginAction.readUserRights(user).map { userRights =>
                      val requestWithUserData =
                        new RequestWithUserData(user, userRights, none, request) // TODO
                      eventService.log(
                        EventType.PasswordVerificationSuccessful,
                        s"Identification par mot de passe"
                      )(requestWithUserData)
                      Redirect(routes.ApplicationController.myApplications)
                        .withSession(
                          request.session - Keys.Session.passwordEmail + (Keys.Session.userId -> user.id.toString)
                        )
                    }
                )
              )
        )
    }

  def passwordReinitializationEmailPage: Action[AnyContent] =
    Action { implicit request =>
      Ok(views.password.reinitializationEmailPage(PasswordRecovery.form))
    }

  def passwordReinitializationEmail: Action[AnyContent] =
    Action.async { implicit request =>
      PasswordRecovery.form
        .bindFromRequest()
        .data
        .get("email")
        .map(_.trim)
        .filter(_.nonEmpty) match {
        case None =>
          Future.successful(
            BadRequest(
              views.password
                .reinitializationEmailPage(
                  PasswordRecovery.form,
                  errorMessage = (
                    "Erreur : l’adresse électronique ne peut pas être vide.",
                    "Merci de renseigner votre adresse électronique."
                  ).some
                )
            )
          )
        case Some(email) =>
          passwordService
            .sendRecoverEmail(email, request.remoteAddress)
            .map(
              _.fold(
                e => {
                  eventService.logErrorNoUser(e)
                  val title =
                    "Erreur : impossible de réinitialiser le mot de passe de ce compte."
                  val description =
                    "Il se peut que votre adresse électronique n’existe pas dans Administration+ " +
                      "ou soit désactivée : veuillez demander à votre responsable de structure " +
                      "la création ou la mise à jour du compte. " +
                      "Il est aussi possible que vous ne soyez pas autorisé à vous connecter par mot de passe : " +
                      "veuillez utiliser le bouton d’envoi de lien magique."
                  val form = PasswordRecovery.form.fill(PasswordRecovery(email))
                  BadRequest(
                    views.password
                      .reinitializationEmailPage(form, errorMessage = (title, description).some)
                  )
                    .withSession(request.session - Keys.Session.passwordEmail)
                },
                expiration => {
                  eventService.logSystem(
                    EventType.PasswordTokenSent,
                    "Lien de changement de mot de passe envoyé",
                    email.some
                  )

                  val expirationDate = expiration.atZone(Time.timeZoneParis)
                  val description =
                    "Un lien d’accès au formulaire de réinitialisation de mot de passe, " +
                      "valide jusqu’à " +
                      expirationDate.format(Time.hourAndMinutesFormatter) +
                      " (UTC" + expirationDate.getOffset + ")" +
                      ", a été envoyé sur votre adresse électronique."
                  // Note: we add the email here, in case the user takes too much time
                  // to use their token
                  addingPasswordEmailToSession(email.some)(
                    Ok(
                      views.password.reinitializationEmailPage(
                        PasswordRecovery.form,
                        successMessage = (
                          "Succès de l’envoi.",
                          description
                        ).some
                      )
                    )
                  )
                }
              )
            )
      }
    }

  def passwordReinitializationPage: Action[AnyContent] =
    Action.async { implicit request =>
      request.getQueryString("token") match {
        case None =>
          eventService.logSystem(
            EventType.PasswordTokenEmpty,
            "Accès à la page de changement de mot de passe sans token",
            none
          )
          Future.successful(
            BadRequest(views.password.reinitializationPage(none, PasswordChange.form))
          )
        case Some(token) =>
          passwordService
            .verifyPasswordRecoveryToken(token)
            .map(
              _.fold(
                e => {
                  eventService.logErrorNoUser(e)
                  val title = "Erreur interne"
                  val description =
                    "Une erreur interne est survenue. Celle-ci étant possiblement temporaire, nous vous invitons à réessayer plus tard."
                  // Removes passwords from form (on purpose)
                  val form = PasswordChange.form.fill(PasswordChange(token, "", ""))
                  InternalServerError(
                    views.password.reinitializationPage(
                      token.some,
                      form,
                      errorMessage = (title, description).some
                    )
                  )
                },
                {
                  case None =>
                    eventService.logSystem(
                      EventType.PasswordTokenIncorrect,
                      s"Token de changement de mot de passe non trouvé en base de données",
                      token.take(100).some
                    )
                    val title = "Lien de changement de mot de passe invalide."
                    val description = "Le lien n’est plus valide, veuillez en générer un autre."
                    BadRequest(
                      views.password
                        .reinitializationPage(
                          none,
                          PasswordChange.form,
                          errorMessage = (title, description).some
                        )
                    )
                  case Some(row) =>
                    if (row.expirationDate.isBefore(Instant.now())) {
                      eventService.logSystem(
                        EventType.PasswordTokenIncorrect,
                        s"Token de changement de mot de passe expiré " +
                          s"[token '${row.token}' ; expiration '${row.expirationDate}' ; " +
                          s"utilisé ${row.used}]",
                        none
                      )
                      val title = "Lien de changement de mot de passe expiré."
                      val description = s"Le lien a expiré, veuillez en générer un autre."
                      BadRequest(
                        views.password
                          .reinitializationPage(
                            none,
                            PasswordChange.form,
                            errorMessage = (title, description).some
                          )
                      )
                    } else if (row.used) {
                      eventService.logSystem(
                        EventType.PasswordTokenIncorrect,
                        s"Token de changement de mot de passe déjà utilisé " +
                          s"[token '${row.token}' ; expiration '${row.expirationDate}' ; " +
                          s"utilisé ${row.used}]",
                        none
                      )
                      val title = "Lien de changement de mot de passe déjà utilisé."
                      val description = s"Le lien a déjà été utilisé, veuillez en générer un autre."
                      BadRequest(
                        views.password
                          .reinitializationPage(
                            none,
                            PasswordChange.form,
                            errorMessage = (title, description).some
                          )
                      )
                    } else {
                      eventService.logSystem(
                        EventType.PasswordChangeShowed,
                        "Visualise le formulaire de changement de mot de passe",
                      )
                      val form = PasswordChange.form.fill(PasswordChange(token, "", ""))
                      Ok(views.password.reinitializationPage(token.some, form))
                    }
                }
              )
            )
      }
    }

  def passwordReinitialization: Action[AnyContent] =
    Action.async { implicit request =>
      PasswordChange.form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            eventService.logSystem(
              EventType.PasswordChangeFormValidationError,
              s"Erreurs dans le formulaire de changement de mot de passe : ${formErrorsLog(formWithErrors)}",
              none
            )
            Future.successful(
              BadRequest(
                views.password.reinitializationPage(formWithErrors("token").value, formWithErrors)
              )
            )
          },
          newCredentials =>
            passwordService
              .changePasswordFromToken(newCredentials.token, newCredentials.newPassword.toArray)
              .map(
                _.fold(
                  e => {
                    eventService.logErrorNoUser(e)
                    val message = "Lien expiré ou déjà utilisé"
                    Redirect(routes.LoginController.passwordPage).flashing("error" -> message)
                  },
                  { case (userId, email) =>
                    eventService.logSystem(
                      EventType.PasswordChanged,
                      s"Mot de passe changé pour l'utilisateur $userId",
                      email.some,
                      involvesUser = userId.some
                    )
                    addingPasswordEmailToSession(email.some)(
                      Redirect(routes.LoginController.passwordPage)
                    )
                      .flashing(
                        "success" -> "Mot de passe changé. Vous pouvez l’utiliser dès à présent pour vous connecter"
                      )
                  }
                )
              )
        )
    }

  def disconnect: Action[AnyContent] =
    Action.async { implicit request =>
      def result = Redirect(routes.LoginController.login).withNewSession
      request.session.get(Keys.Session.sessionId) match {
        case None => Future.successful(result)
        case Some(sessionId) =>
          userService
            .revokeUserSession(sessionId)
            .flatMap(
              _.fold(
                e =>
                  IO.blocking(eventService.logErrorNoUser(e))
                    .as(InternalServerError(views.errors.public500(None))),
                _ => IO.pure(result)
              )
            )
            .unsafeToFuture()
      }

    }

  private def addingPasswordEmailToSession(
      email: Option[String]
  )(result: Result)(implicit request: Request[_]): Result =
    email.map(_.trim).filter(_.nonEmpty) match {
      case None =>
        result.withSession(request.session - Keys.Session.passwordEmail)
      case Some(email) =>
        result
          .withSession(
            request.session - Keys.Session.passwordEmail +
              (Keys.Session.passwordEmail -> email.take(User.emailMaxLength))
          )
    }

}
