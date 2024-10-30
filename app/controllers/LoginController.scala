package controllers

import actions.{LoginAction, RequestWithUserData}
import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import constants.Constants
import helper.PlayFormHelpers.formErrorsLog
import helper.ScalatagsHelpers.writeableOf_Modifier
import helper.Time
import java.time.Instant
import javax.inject.{Inject, Singleton}
import models.{
  Authorization,
  Error,
  EventType,
  LoginToken,
  ProConnectClaims,
  SignupRequest,
  User,
  UserSession
}
import models.EventType.{GenerateToken, UnknownEmail}
import models.forms.{PasswordChange, PasswordCredentials, PasswordRecovery}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Request, Result}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try
import serializers.Keys
import services.{
  EventService,
  NotificationService,
  PasswordService,
  ProConnectService,
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
    proConnectService: ProConnectService,
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

  def loginPage: Action[AnyContent] = Action { request =>
    val proConnectErrorMessage = (
      request.flash.get(proConnectErrorTitleFlashKey),
      request.flash.get(proConnectErrorDescriptionFlashKey)
    ) match {
      case (Some(title), Some(description)) => Some((title, description))
      case (Some(title), None)              => Some((title, ""))
      case (None, Some(description))        => Some(("", description))
      case (None, None)                     => None
    }
    Ok(
      views.login.page(
        featureProConnectEnabled = config.featureProConnectEnabled,
        proConnectErrorMessage = proConnectErrorMessage
      )
    )
  }

  /** Will send back a Redirect with the states in the session */
  def proConnectLoginRedirection: Action[AnyContent] = Action.async { implicit request =>
    if (config.featureProConnectEnabled)
      proConnectService.authenticationRequestUrl.value
        .flatMap(
          _.fold(
            error =>
              logProConnectError(error) >>
                IO(proConnectService.resetSessionKeys(proConnectErrorRedirect(error))),
            { case (authenticationRequestUrl, withResultSettings) =>
              IO(withResultSettings(Redirect(authenticationRequestUrl), request))
            }
          )
        )
        .unsafeToFuture()
    else
      Future.successful(NotFound(views.errors.public404()))
  }

  /** OAuth 2 / OpenID Connect "redirect_uri" */
  def proConnectAuthenticationResponseCallback: Action[AnyContent] = Action.async {
    implicit request =>
      def noAccountError(userInfo: ProConnectService.UserInfo) = EitherT.right[Error](
        IO.blocking(
          eventService.logSystem(
            EventType.ProConnectUnknownEmail,
            s"Connexion ProConnect réussie avec l'email ${userInfo.email}, mais aucun compte actif à cette adresse [subject: ${userInfo.subject}]",
          )
        ) >>
          IO(
            proConnectErrorRedirectResult(
              errorTitle = s"Aucun compte à l’adresse « ${userInfo.email} »",
              errorDescription =
                "Aucun compte actif n’est associé à cette adresse email. Veuillez noter que la création de compte doit être effectuée par votre responsable de structure ou départemental.",
            )
          )
      )
      def logSignupIn(
          signup: SignupRequest,
          userInfo: ProConnectService.UserInfo
      ): EitherT[IO, Error, Result] =
        EitherT.right[Error](
          IO.blocking(
            eventService.logSystem(
              EventType.ProConnectSignupLoginSuccessful,
              s"Identification via ProConnect, préinscription ${signup.id} [subject: ${userInfo.subject}]"
            )
          ) >>
            IO.realTimeInstant
              .flatMap(ProConnectService.calculateExpiresAt)
              .map(expiresAt =>
                Redirect(routes.SignupController.signupForm)
                  .addingToSession(
                    Keys.Session.signupId -> signup.id.toString,
                    Keys.Session.signupProConnectSubject -> userInfo.subject,
                    Keys.Session.signupLoginExpiresAt -> expiresAt.getEpochSecond.toString,
                  )
              )
        )

      def logUserIn(
          user: User,
          idToken: ProConnectService.IDToken,
          userInfo: ProConnectService.UserInfo
      ): EitherT[IO, Error, Result] = {
        val userRights = Authorization.readUserRights(user)
        if (user.disabled) {
          val requestWithUserData =
            new RequestWithUserData(user, userRights, none, request)
          EitherT.right[Error](
            IO.blocking(
              eventService.log(
                EventType.ProConnectLoginDeactivatedUser,
                s"Identification via ProConnect de l'utilisateur désactivé ${user.id} [subject: ${userInfo.subject}]"
              )(requestWithUserData)
            ) >>
              IO(
                proConnectErrorRedirectResult(
                  errorTitle = s"Compte « ${userInfo.email} » désactivé",
                  errorDescription =
                    s"Le compte lié à l’adresse email que vous avez renseignée « ${userInfo.email} » est désactivé. Le responsable de votre structure ou le responsable départemental peut réactiver le compte. Alternativement, si vous possédez un compte actif, vous pouvez utiliser l’adresse email correspondante."
                )
              )
          )
        } else {
          val expiresAtIO = IO.realTimeInstant.flatMap(now =>
            request.session
              .get(Keys.Session.signupLoginExpiresAt)
              .flatMap(epoch => Try(Instant.ofEpochSecond(epoch.toLong)).toOption) match {
              case None            => ProConnectService.calculateExpiresAt(now)
              case Some(expiresAt) => IO.pure(expiresAt)
            }
          )
          for {
            expiresAt <- EitherT.right[Error](expiresAtIO)
            _ <- EitherT.right[Error](IO.blocking(userService.recordLogin(user.id)))
            session <- userService.createNewUserSession(
              user.id,
              UserSession.LoginType.ProConnect,
              expiresAt,
              request.remoteAddress,
              request.headers.get(USER_AGENT),
            )
            _ <- EitherT.right[Error] {
              val requestWithUserData =
                new RequestWithUserData(user, userRights, session.some, request)
              val idTokenClaimsNames = idToken.signedToken.getPayload.keySet.asScala.toSet
              val userInfoClaimsNames = userInfo.signedToken.getPayload.keySet.asScala.toSet
              val proConnectInfos = s"subject: ${userInfo.subject} ; " +
                s"IDToken claims: $idTokenClaimsNames ; " +
                s"UserInfo claims: $userInfoClaimsNames"
              IO.blocking(
                eventService.log(
                  EventType.ProConnectUserLoginSuccessful,
                  s"Identification via ProConnect, utilisateur ${user.id} [$proConnectInfos]"
                )(requestWithUserData)
              )
            }
          } yield Redirect(routes.ApplicationController.myApplications)
            .addingToSession(
              Keys.Session.userId -> user.id.toString,
              Keys.Session.sessionId -> session.id,
            )
        }
      }

      proConnectService
        .handleAuthenticationResponse(request) { error =>
          logProConnectError(error) >>
            IO(proConnectErrorRedirect(error))
        } { case (idToken, userInfo) =>
          IO.realTimeInstant.flatMap { now =>
            IO.blocking(userService.byEmail(userInfo.email, includeDisabled = true)).flatMap {
              user =>
                val claims = ProConnectClaims(
                  subject = userInfo.subject,
                  email = userInfo.email,
                  givenName = userInfo.givenName,
                  usualName = userInfo.usualName,
                  uid = userInfo.uid,
                  siret = userInfo.siret,
                  creationDate = now,
                  lastAuthTime = idToken.authTime.map(Instant.ofEpochSecond),
                  userId = user.map(_.id),
                )
                EitherT(userService.saveProConnectClaims(claims))
                  .flatMap(_ =>
                    user match {
                      case None =>
                        EitherT(IO.fromFuture(IO(signupService.byEmail(userInfo.email)))).flatMap(
                          _.fold(noAccountError(userInfo))(signup => logSignupIn(signup, userInfo))
                        )
                      case Some(user) =>
                        logUserIn(user, idToken, userInfo)
                    }
                  )
                  .valueOrF(error =>
                    IO.blocking(eventService.logErrorNoUser(error))
                      .as(
                        proConnectErrorRedirectResult(
                          errorTitle = "Erreur interne",
                          errorDescription =
                            "Une erreur interne est survenue. Celle-ci étant possiblement temporaire, nous vous invitons à réessayer plus tard.",
                        )
                      )
                  )
            }

          }
        }
        .unsafeToFuture()
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
                    LoginAction.readUserRights(user).flatMap { userRights =>
                      (
                        for {
                          expiresAt <- EitherT.right[Error](
                            IO.realTimeInstant
                              .map(_.plusSeconds(config.passwordSessionDurationInSeconds))
                          )
                          _ <- EitherT.right[Error](IO.blocking(userService.recordLogin(user.id)))
                          userSession <- userService.createNewUserSession(
                            user.id,
                            UserSession.LoginType.Password,
                            expiresAt,
                            request.remoteAddress,
                            request.headers.get(USER_AGENT),
                          )
                          _ <- EitherT.right[Error](
                            IO.blocking(
                              eventService.log(
                                EventType.PasswordVerificationSuccessful,
                                s"Identification par mot de passe"
                              )(
                                new RequestWithUserData(user, userRights, userSession.some, request)
                              )
                            )
                          )
                        } yield Redirect(routes.ApplicationController.myApplications)
                          .removingFromSession(Keys.Session.passwordEmail)
                          .addingToSession(
                            Keys.Session.userId -> user.id.toString,
                            Keys.Session.sessionId -> userSession.id,
                          )
                      ).valueOrF(error =>
                        IO.blocking(
                          eventService.logError(error)(
                            new RequestWithUserData(user, userRights, none, request)
                          )
                        ).as(InternalServerError(views.errors.public500(None)))
                      ).unsafeToFuture()
                    }
                )
              )
        )
    }

  def passwordReinitializationEmailPage: Action[AnyContent] =
    Action { implicit request =>
      val form = request.session.get(Keys.Session.passwordEmail) match {
        case None        => PasswordRecovery.form
        case Some(email) => PasswordRecovery.form.fill(PasswordRecovery(email))
      }
      Ok(views.password.reinitializationEmailPage(form))
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
                    .removingFromSession(Keys.Session.passwordEmail)
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

  private val proConnectErrorTitleFlashKey = "proConnectErrorTitle"
  private val proConnectErrorDescriptionFlashKey = "proConnectErrorDescription"

  private def proConnectErrorRedirect(error: ProConnectService.Error): Result = {
    val (title, description) = proConnectErrorMessage(error)
    proConnectErrorRedirectResult(title, description)
  }

  private def proConnectErrorRedirectResult(
      errorTitle: String,
      errorDescription: String
  ): Result =
    Redirect(routes.LoginController.loginPage)
      .flashing(
        proConnectErrorTitleFlashKey -> errorTitle,
        proConnectErrorDescriptionFlashKey -> errorDescription
      )

  private val proConnectErrorMessage =
    s"Une erreur s’est produite lors de notre communication avec ProConnect. Il n’est donc pas possible de vous connecter via ProConnect. L’erreur étant probablement temporaire, vous pouvez réessayer plus tard, ou utiliser la connexion par lien à usage unique disponible sur notre page d’accueil. Si l’erreur venait à persister, vous pouvez contacter le support d’Administration+."

  private def proConnectErrorMessage(error: ProConnectService.Error): (String, String) = {
    import ProConnectService.Error._

    error match {
      case FailedGeneratingFromSecureRandom(_) | AuthResponseMissingStateInSession |
          AuthResponseMissingNonceInSession =>
        ("Erreur inattendue", Constants.genericError500Message)
      // Error codes:
      // - https://www.rfc-editor.org/rfc/rfc6749.html#section-4.1.2.1
      // - https://openid.net/specs/openid-connect-core-1_0.html#AuthError
      case AuthResponseEndpointError(
            "access_denied" | "interaction_required" | "login_required" |
            "account_selection_required" | "consent_required",
            _,
            _
          ) =>
        (
          "Erreur : échec de la connexion",
          "Votre connexion via ProConnect a échoué, vous pouvez réessayer ou utiliser la connexion par lien à usage unique disponible sur notre page d’accueil"
        )
      // Note: TokenResponseError error codes:
      // - https://www.rfc-editor.org/rfc/rfc6749.html#section-5.2
      case _ => ("Erreur : impossible de communiquer avec ProConnect", proConnectErrorMessage)
    }
  }

  private def logProConnectError(
      error: ProConnectService.Error
  )(implicit request: Request[_]): IO[Unit] = IO.blocking {
    import ProConnectService.Error._

    val (eventType, description, additionalUnsafeData, exception)
        : (EventType, String, Option[String], Option[Throwable]) = error match {
      case FailedGeneratingFromSecureRandom(error) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect - Impossible de générer des données aléatoire avec le CSPRNG",
          None,
          Some(error)
        )
      case ProviderConfigurationRequestFailure(error) =>
        (
          EventType.ProConnectError,
          "ProConnect (Configuration) - La connexion à l'url de configuration ProConnect a échouée",
          None,
          Some(error)
        )
      case ProviderConfigurationErrorResponse(status, body) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Configuration) - L'url de configuration ProConnect a renvoyé une erreur, status $status",
          Some(s"Body : $body"),
          None
        )
      case ProviderConfigurationUnparsableJson(status, error) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Configuration) - Impossible de lire le JSON reçu de l'url de configuration ProConnect (status $status)",
          None,
          Some(error)
        )
      case ProviderConfigurationInvalidJson(error) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Configuration) - Formatage inattendu du JSON de configuration ProConnect: ${error.errors}",
          None,
          None
        )
      case ProviderConfigurationInvalidIssuer(wantedIssuer, providedIssuer) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Configuration) - Le champ iss de l'url de configuration ProConnect ne correspond pas au notre",
          Some(s"Issuer attendu : '$wantedIssuer' ; Issuer reçu : '$providedIssuer'"),
          None
        )
      case NotEnoughElapsedTimeBetweenDiscoveryCalls(lastFetchTime, now) =>
        (
          EventType.ProConnectError,
          s"ProConnect - Demande trop récente de rafraîchissement du cache de la configuration ProConnect, dernière demande $lastFetchTime, date présente $now",
          None,
          None
        )
      case AuthResponseMissingStateInSession =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Authorization Response) - La session de l'utilisateur n'a pas le state posé avant l'appel à ProConnect",
          None,
          None
        )
      case AuthResponseMissingNonceInSession =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Authorization Response) - La session de l'utilisateur n'a pas le nonce posé avant l'appel à ProConnect",
          None,
          None
        )
      case AuthResponseUnparseableState(requestState, error) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Authorization Response) - Impossible de lire le state reçu",
          Some(s"State reçu : $requestState"),
          Some(error)
        )
      case AuthResponseInvalidState(sessionState, requestState) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Authorization Response) - Le state reçu ne correspond pas au state en session",
          Some(s"State en session: $sessionState ; State reçu : $requestState"),
          None
        )
      case AuthResponseMissingErrorQueryParam =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Authorization Response) - Erreur reçue mais le champ 'error' est manquant",
          None,
          None
        )
      case AuthResponseMissingStateQueryParam =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Authorization Response) - Erreur reçue mais le champ 'state' est manquant",
          None,
          None
        )
      case AuthResponseEndpointError(errorCode, errorDescription, errorUri) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Authorization Response) - Erreur lors de l'authentification de l'utilisateur",
          Some(s"error: $errorCode ; error_description: $errorDescription ; error_uri: $errorUri"),
          None
        )
      case JwksRequestFailure(error) =>
        (
          EventType.ProConnectError,
          "ProConnect (jwks) - La connexion à l'url jwks a échouée",
          None,
          Some(error)
        )
      case JwksUnparsableResponse(status, body, error) =>
        (
          EventType.ProConnectError,
          s"ProConnect (jwks) - Impossible de lire le JWK Set reçu (status $status)",
          Some(s"Body: $body"),
          Some(error)
        )
      case TokenRequestFailure(error) =>
        (
          EventType.ProConnectError,
          "ProConnect (Token Request) - La connexion au token endpoint a échoué",
          None,
          Some(error)
        )
      case TokenResponseUnparsableJson(status, error) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Token Response) - Impossible de lire le JSON (status $status)",
          None,
          Some(error)
        )
      case TokenResponseInvalidJson(error) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Token Response) - Formatage inattendu du JSON (status 200): ${error.errors}",
          None,
          None
        )
      case TokenResponseErrorInvalidJson(error) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Token Response) - Formatage inattendu du JSON (status 4xx): ${error.errors}",
          None,
          None
        )
      case TokenResponseUnknown(status, body) =>
        (
          EventType.ProConnectError,
          s"ProConnect (Token Response) - Status inconnu $status",
          Some(s"Body: $body"),
          None
        )
      case TokenResponseError(error) =>
        (
          EventType.ProConnectError,
          "ProConnect (Token Response) - Le serveur a renvoyé un JSON décrivant une erreur",
          Some(s"Erreur: $error"),
          None
        )
      case TokenResponseInvalidTokenType(tokenType) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Token Response) - Le token_type est invalide",
          Some(s"token_type: $tokenType"),
          None
        )
      case InvalidIDToken(error, claimsNames) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Token Response) - Le IDToken est invalide, mauvaise signature ou claims invalides",
          Some(s"Claims: $claimsNames"),
          Some(error)
        )
      case IDTokenInvalidClaims(error, claimsNames) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (Token Response) - Le IDToken n'a pas de claim sub ou auth_time",
          Some(s"Claims: $claimsNames"),
          error
        )
      case UserInfoRequestFailure(error) =>
        (
          EventType.ProConnectError,
          "ProConnect (UserInfo Request) - La connexion a échoué",
          None,
          Some(error)
        )
      case UserInfoResponseUnsuccessfulStatus(status, wwwAuthenticateHeader, body) =>
        (
          EventType.ProConnectError,
          s"ProConnect (UserInfo Response) - ProConnect indique une erreur (status $status)",
          Some(s"WWW-Authenticate: $wwwAuthenticateHeader ; Body: $body"),
          None
        )
      case UserInfoResponseUnknownContentType(contentType) =>
        (
          EventType.ProConnectSecurityWarning,
          "ProConnect (UserInfo Response) - Le Content-Type reçu est inattendu",
          Some(s"Content-Type: $contentType"),
          None
        )
      case UserInfoInvalidClaims(error, claimsNames) =>
        (
          EventType.ProConnectError,
          "ProConnect (UserInfo Response) - Certaines claims sont manquantes (les claims nécessaires sont sub et email et celles nullables sont given_name, usual_name, uid, siret)",
          Some(s"Claims: $claimsNames"),
          error
        )
      case InvalidJwsAlgorithm(invalidAlgorithm) =>
        (
          EventType.ProConnectSecurityWarning,
          "L'algorithme de chiffrement utilisé par ProConnect est inattendu",
          Some(s"Algorithme: $invalidAlgorithm"),
          None
        )
    }

    val unsafeKeys = request.queryString.keys.mkString(", ")
    val unsafeData = additionalUnsafeData.map(_ + " ; " + unsafeKeys).getOrElse(unsafeKeys)
    eventService.logSystem(
      event = eventType,
      descriptionSanitized = description,
      additionalUnsafeData = Some(unsafeData),
      underlyingException = exception
    )
  }

}
