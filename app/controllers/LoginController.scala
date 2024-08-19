package controllers

import actions.{LoginAction, RequestWithUserData}
import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import constants.Constants
import helper.ScalatagsHelpers.writeableOf_Modifier
import helper.Time
import java.time.Instant
import javax.inject.{Inject, Singleton}
import models.{
  AgentConnectClaims,
  Authorization,
  Error,
  EventType,
  LoginToken,
  SignupRequest,
  User,
  UserSession
}
import models.EventType.{GenerateToken, UnknownEmail}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Action, AnyContent, InjectedController, Request, Result}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try
import serializers.Keys
import services.{
  AgentConnectService,
  EventService,
  NotificationService,
  ServicesDependencies,
  SignupService,
  TokenService,
  UserService
}
import views.home.LoginPanel

@Singleton
class LoginController @Inject() (
    agentConnectService: AgentConnectService,
    val config: AppConfig,
    dependencies: ServicesDependencies,
    userService: UserService,
    notificationService: NotificationService,
    tokenService: TokenService,
    eventService: EventService,
    signupService: SignupService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
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

  def loginPage: Action[AnyContent] = Action { request =>
    val agentConnectErrorMessage = (
      request.flash.get(agentConnectErrorTitleFlashKey),
      request.flash.get(agentConnectErrorDescriptionFlashKey)
    ) match {
      case (Some(title), Some(description)) => Some((title, description))
      case (Some(title), None)              => Some((title, ""))
      case (None, Some(description))        => Some(("", description))
      case (None, None)                     => None
    }
    Ok(
      views.login.page(
        featureAgentConnectEnabled = config.featureAgentConnectEnabled,
        agentConnectErrorMessage = agentConnectErrorMessage
      )
    )
  }

  /** Will send back a Redirect with the states in the session */
  def agentConnectLoginRedirection: Action[AnyContent] = Action.async { implicit request =>
    if (config.featureAgentConnectEnabled)
      agentConnectService.authenticationRequestUrl.value
        .flatMap(
          _.fold(
            error =>
              logAgentConnectError(error) >>
                IO(agentConnectService.resetSessionKeys(agentConnectErrorRedirect(error))),
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
  def agentConnectAuthenticationResponseCallback: Action[AnyContent] = Action.async {
    implicit request =>
      def noAccountError(userInfo: AgentConnectService.UserInfo) = EitherT.right[Error](
        IO.blocking(
          eventService.logSystem(
            EventType.AgentConnectUnknownEmail,
            s"Connexion AgentConnect réussie avec l'email ${userInfo.email}, mais aucun compte actif à cette adresse [subject: ${userInfo.subject}]",
          )
        ) >>
          IO(
            agentConnectErrorRedirectResult(
              errorTitle = s"Aucun compte à l’adresse « ${userInfo.email} »",
              errorDescription =
                "Aucun compte actif n’est associé à cette adresse email. Veuillez noter que la création de compte doit être effectuée par votre responsable de structure ou départemental.",
            )
          )
      )
      def logSignupIn(
          signup: SignupRequest,
          userInfo: AgentConnectService.UserInfo
      ): EitherT[IO, Error, Result] =
        EitherT.right[Error](
          IO.blocking(
            eventService.logSystem(
              EventType.AgentConnectSignupLoginSuccessful,
              s"Identification via AgentConnect, préinscription ${signup.id} [subject: ${userInfo.subject}]"
            )
          ) >>
            IO.realTimeInstant
              .flatMap(AgentConnectService.calculateExpiresAt)
              .map(expiresAt =>
                Redirect(routes.SignupController.signupForm)
                  .addingToSession(
                    Keys.Session.signupId -> signup.id.toString,
                    Keys.Session.signupAgentConnectSubject -> userInfo.subject,
                    Keys.Session.signupLoginExpiresAt -> expiresAt.getEpochSecond.toString,
                  )
              )
        )

      def logUserIn(
          user: User,
          idToken: AgentConnectService.IDToken,
          userInfo: AgentConnectService.UserInfo
      ): EitherT[IO, Error, Result] = {
        val userRights = Authorization.readUserRights(user)
        if (user.disabled) {
          val requestWithUserData =
            new RequestWithUserData(user, userRights, none, request)
          EitherT.right[Error](
            IO.blocking(
              eventService.log(
                EventType.AgentConnectLoginDeactivatedUser,
                s"Identification via AgentConnect de l'utilisateur désactivé ${user.id} [subject: ${userInfo.subject}]"
              )(requestWithUserData)
            ) >>
              IO(
                agentConnectErrorRedirectResult(
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
              case None            => AgentConnectService.calculateExpiresAt(now)
              case Some(expiresAt) => IO.pure(expiresAt)
            }
          )
          for {
            expiresAt <- EitherT.right[Error](expiresAtIO)
            _ <- EitherT.right[Error](IO.blocking(userService.recordLogin(user.id)))
            session <- userService.createNewUserSession(
              user.id,
              UserSession.LoginType.AgentConnect,
              expiresAt,
              request.remoteAddress
            )
            _ <- EitherT.right[Error] {
              val requestWithUserData =
                new RequestWithUserData(user, userRights, session.some, request)
              val idTokenClaimsNames = idToken.signedToken.getPayload.keySet.asScala.toSet
              val userInfoClaimsNames = userInfo.signedToken.getPayload.keySet.asScala.toSet
              val agentConnectInfos = s"subject: ${userInfo.subject} ; " +
                s"IDToken claims: $idTokenClaimsNames ; " +
                s"UserInfo claims: $userInfoClaimsNames"
              IO.blocking(
                eventService.log(
                  EventType.AgentConnectUserLoginSuccessful,
                  s"Identification via AgentConnect, utilisateur ${user.id} [$agentConnectInfos]"
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

      agentConnectService
        .handleAuthenticationResponse(request) { error =>
          logAgentConnectError(error) >>
            IO(agentConnectErrorRedirect(error))
        } { case (idToken, userInfo) =>
          IO.realTimeInstant.flatMap { now =>
            IO.blocking(userService.byEmail(userInfo.email, includeDisabled = true)).flatMap {
              user =>
                val claims = AgentConnectClaims(
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
                EitherT(userService.saveAgentConnectClaims(claims))
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
                        agentConnectErrorRedirectResult(
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

  private val agentConnectErrorTitleFlashKey = "agentConnectErrorTitle"
  private val agentConnectErrorDescriptionFlashKey = "agentConnectErrorDescription"

  private def agentConnectErrorRedirect(error: AgentConnectService.Error): Result = {
    val (title, description) = agentConnectErrorMessage(error)
    agentConnectErrorRedirectResult(title, description)
  }

  private def agentConnectErrorRedirectResult(
      errorTitle: String,
      errorDescription: String
  ): Result =
    Redirect(routes.LoginController.loginPage)
      .flashing(
        agentConnectErrorTitleFlashKey -> errorTitle,
        agentConnectErrorDescriptionFlashKey -> errorDescription
      )

  private val agentConnectErrorMessage =
    s"Une erreur s’est produite lors de notre communication avec AgentConnect. Il n’est donc pas possible de vous connecter via AgentConnect. L’erreur étant probablement temporaire, vous pouvez réessayer plus tard, ou utiliser la connexion par lien à usage unique disponible sur notre page d’accueil. Si l’erreur venait à persister, vous pouvez contacter le support d’Administration+."

  private def agentConnectErrorMessage(error: AgentConnectService.Error): (String, String) = {
    import AgentConnectService.Error._

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
          "Votre connexion via AgentConnect a échoué, vous pouvez réessayer ou utiliser la connexion par lien à usage unique disponible sur notre page d’accueil"
        )
      // Note: TokenResponseError error codes:
      // - https://www.rfc-editor.org/rfc/rfc6749.html#section-5.2
      case _ => ("Erreur : impossible de communiquer avec AgentConnect", agentConnectErrorMessage)
    }
  }

  private def logAgentConnectError(
      error: AgentConnectService.Error
  )(implicit request: Request[_]): IO[Unit] = IO.blocking {
    import AgentConnectService.Error._

    val (eventType, description, additionalUnsafeData, exception)
        : (EventType, String, Option[String], Option[Throwable]) = error match {
      case FailedGeneratingFromSecureRandom(error) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect - Impossible de générer des données aléatoire avec le CSPRNG",
          None,
          Some(error)
        )
      case ProviderConfigurationRequestFailure(error) =>
        (
          EventType.AgentConnectError,
          "AgentConnect (Configuration) - La connexion à l'url de configuration AgentConnect a échouée",
          None,
          Some(error)
        )
      case ProviderConfigurationErrorResponse(status, body) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Configuration) - L'url de configuration AgentConnect a renvoyé une erreur, status $status",
          Some(s"Body : $body"),
          None
        )
      case ProviderConfigurationUnparsableJson(status, error) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Configuration) - Impossible de lire le JSON reçu de l'url de configuration AgentConnect (status $status)",
          None,
          Some(error)
        )
      case ProviderConfigurationInvalidJson(error) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Configuration) - Formatage inattendu du JSON de configuration AgentConnect: ${error.errors}",
          None,
          None
        )
      case ProviderConfigurationInvalidIssuer(wantedIssuer, providedIssuer) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Configuration) - Le champ iss de l'url de configuration AgentConnect ne correspond pas au notre",
          Some(s"Issuer attendu : '$wantedIssuer' ; Issuer reçu : '$providedIssuer'"),
          None
        )
      case NotEnoughElapsedTimeBetweenDiscoveryCalls(lastFetchTime, now) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect - Demande trop récente de rafraîchissement du cache de la configuration AgentConnect, dernière demande $lastFetchTime, date présente $now",
          None,
          None
        )
      case AuthResponseMissingStateInSession =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Authorization Response) - La session de l'utilisateur n'a pas le state posé avant l'appel à AgentConnect",
          None,
          None
        )
      case AuthResponseMissingNonceInSession =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Authorization Response) - La session de l'utilisateur n'a pas le nonce posé avant l'appel à AgentConnect",
          None,
          None
        )
      case AuthResponseUnparseableState(requestState, error) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Authorization Response) - Impossible de lire le state reçu",
          Some(s"State reçu : $requestState"),
          Some(error)
        )
      case AuthResponseInvalidState(sessionState, requestState) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Authorization Response) - Le state reçu ne correspond pas au state en session",
          Some(s"State en session: $sessionState ; State reçu : $requestState"),
          None
        )
      case AuthResponseMissingErrorQueryParam =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Authorization Response) - Erreur reçue mais le champ 'error' est manquant",
          None,
          None
        )
      case AuthResponseMissingStateQueryParam =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Authorization Response) - Erreur reçue mais le champ 'state' est manquant",
          None,
          None
        )
      case AuthResponseEndpointError(errorCode, errorDescription, errorUri) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Authorization Response) - Erreur lors de l'authentification de l'utilisateur",
          Some(s"error: $errorCode ; error_description: $errorDescription ; error_uri: $errorUri"),
          None
        )
      case JwksRequestFailure(error) =>
        (
          EventType.AgentConnectError,
          "AgentConnect (jwks) - La connexion à l'url jwks a échouée",
          None,
          Some(error)
        )
      case JwksUnparsableResponse(status, body, error) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (jwks) - Impossible de lire le JWK Set reçu (status $status)",
          Some(s"Body: $body"),
          Some(error)
        )
      case TokenRequestFailure(error) =>
        (
          EventType.AgentConnectError,
          "AgentConnect (Token Request) - La connexion au token endpoint a échoué",
          None,
          Some(error)
        )
      case TokenResponseUnparsableJson(status, error) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Token Response) - Impossible de lire le JSON (status $status)",
          None,
          Some(error)
        )
      case TokenResponseInvalidJson(error) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Token Response) - Formatage inattendu du JSON (status 200): ${error.errors}",
          None,
          None
        )
      case TokenResponseErrorInvalidJson(error) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Token Response) - Formatage inattendu du JSON (status 4xx): ${error.errors}",
          None,
          None
        )
      case TokenResponseUnknown(status, body) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (Token Response) - Status inconnu $status",
          Some(s"Body: $body"),
          None
        )
      case TokenResponseError(error) =>
        (
          EventType.AgentConnectError,
          "AgentConnect (Token Response) - Le serveur a renvoyé un JSON décrivant une erreur",
          Some(s"Erreur: $error"),
          None
        )
      case TokenResponseInvalidTokenType(tokenType) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Token Response) - Le token_type est invalide",
          Some(s"token_type: $tokenType"),
          None
        )
      case InvalidIDToken(error, claimsNames) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Token Response) - Le IDToken est invalide, mauvaise signature ou claims invalides",
          Some(s"Claims: $claimsNames"),
          Some(error)
        )
      case IDTokenInvalidClaims(error, claimsNames) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (Token Response) - Le IDToken n'a pas de claim sub ou auth_time",
          Some(s"Claims: $claimsNames"),
          error
        )
      case UserInfoRequestFailure(error) =>
        (
          EventType.AgentConnectError,
          "AgentConnect (UserInfo Request) - La connexion a échoué",
          None,
          Some(error)
        )
      case UserInfoResponseUnsuccessfulStatus(status, wwwAuthenticateHeader, body) =>
        (
          EventType.AgentConnectError,
          s"AgentConnect (UserInfo Response) - AgentConnect indique une erreur (status $status)",
          Some(s"WWW-Authenticate: $wwwAuthenticateHeader ; Body: $body"),
          None
        )
      case UserInfoResponseUnknownContentType(contentType) =>
        (
          EventType.AgentConnectSecurityWarning,
          "AgentConnect (UserInfo Response) - Le Content-Type reçu est inattendu",
          Some(s"Content-Type: $contentType"),
          None
        )
      case UserInfoInvalidClaims(error, claimsNames) =>
        (
          EventType.AgentConnectError,
          "AgentConnect (UserInfo Response) - Certaines claims sont manquantes (les claims nécessaires sont sub et email et celles nullables sont given_name, usual_name, uid, siret)",
          Some(s"Claims: $claimsNames"),
          error
        )
      case InvalidJwsAlgorithm(invalidAlgorithm) =>
        (
          EventType.AgentConnectSecurityWarning,
          "L'algorithme de chiffrement utilisé par AgentConnect est inattendu",
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
