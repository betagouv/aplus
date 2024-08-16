package actions

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import constants.Constants
import controllers.routes
import helper.ScalatagsHelpers.writeableOf_Modifier
import helper.UUIDHelper
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Area, Authorization, Error, EventType, LoginToken, User, UserSession}
import models.EventType.{AuthWithDifferentIp, ExpiredToken, ToCGURedirected, TryLoginByKey}
import modules.AppConfig
import play.api.Logger
import play.api.mvc._
import play.api.mvc.Results.{InternalServerError, TemporaryRedirect}
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys
import services.{EventService, ServicesDependencies, SignupService, TokenService, UserService}

class RequestWithUserData[A](
    val currentUser: User,
    // Note: accessible here because we will need to make DB calls to create it (areas)
    val rights: Authorization.UserRights,
    val userSession: Option[UserSession],
    request: Request[A]
) extends WrappedRequest[A](request)

object LoginAction {

  /** Will take services as parameters and make DB calls. */
  def readUserRights(user: User)(implicit ec: ExecutionContext): Future[Authorization.UserRights] =
    Future(
      Authorization.readUserRights(user)
    )

  def signupSessionKeys: List[String] =
    List(Keys.Session.signupId, Keys.Session.signupLoginExpiresAt)

}

//TODO : this class is complicated. Maybe we can split the logic.

@Singleton
class LoginAction @Inject() (
    config: AppConfig,
    dependencies: ServicesDependencies,
    eventService: EventService,
    parser: BodyParsers.Default,
    signupService: SignupService,
    tokenService: TokenService,
    userService: UserService,
)(implicit ec: ExecutionContext)
    extends BaseLoginAction(
      config,
      dependencies,
      eventService,
      ec,
      parser,
      signupService,
      tokenService,
      userService,
    ) {

  def withPublicPage(publicPage: Result): BaseLoginAction =
    new BaseLoginAction(
      config,
      dependencies,
      eventService,
      ec,
      parser,
      signupService,
      tokenService,
      userService,
      publicPage.some
    )

}

class BaseLoginAction(
    config: AppConfig,
    dependencies: ServicesDependencies,
    eventService: EventService,
    implicit val executionContext: ExecutionContext,
    val parser: BodyParsers.Default,
    signupService: SignupService,
    tokenService: TokenService,
    userService: UserService,
    publicPage: Option[Result] = none,
) extends ActionBuilder[RequestWithUserData, AnyContent]
    with ActionRefiner[Request, RequestWithUserData] {

  import dependencies.ioRuntime

  private val log = Logger(classOf[LoginAction])

  private def queryToString(qs: Map[String, Seq[String]]) = {
    val queryString =
      qs.map { case (key, value) => key + "=" + value.sorted.mkString("|,|") }.mkString("&")
    if (queryString.nonEmpty) "?" + queryString else ""
  }

  override def refine[A](request: Request[A]): Future[Either[Result, RequestWithUserData[A]]] = {
    implicit val req = request
    val path =
      request.path + queryToString(
        request.queryString - Keys.QueryParam.key - Keys.QueryParam.token
      )
    val url = "http" + (if (request.secure) "s" else "") + "://" + request.host + path
    val signupOpt = request.session.get(Keys.Session.signupId).flatMap(UUIDHelper.fromString)
    val tokenOpt = request.getQueryString(Keys.QueryParam.token)

    val userBySession: Option[UUID] =
      request.session
        .get(Keys.Session.userId)
        .flatMap(UUIDHelper.fromString)

    val userByKey: Option[User] =
      request.getQueryString(Keys.QueryParam.key).flatMap(userService.byKey)

    (userBySession, userByKey, tokenOpt, signupOpt) match {
      // Note: this case is deliberately put here for failing fast, if the token is invalid,
      //       we don't want to continue doing sensitive operations
      case (_, _, Some(rawToken), _) =>
        tryAuthByToken(rawToken)
      // Note: user.key is used here as a way to check if the user comes from an email.
      //       With that key, the user won't see the warn in the last case.
      //       Consequently, if there is no key, the user will see the case
      //       userNotLogged("Vous devez vous identifier pour accéder à cette page.")
      //       It is not clear this is a good thing and the code is definitely confusing.
      case (Some(userId), Some(userKey), None, None) if userId === userKey.id =>
        // This essentially removes query parameters but keeps the session
        // Next `GET url` will go to the case (Some(userId), None, _, _)
        Future(Left(TemporaryRedirect(Call(request.method, url).url)))
      case (_, Some(user), None, None) =>
        tryInsecureAuthByKey(user, url, path).map(_.asLeft)
      case (Some(userId), None, None, None) =>
        val sessionId = request.session.get(Keys.Session.sessionId)
        userService
          .userWithSessionLoggingActivity(userId, sessionId)
          .unsafeToFuture()
          .flatMap(
            _.fold(
              e => {
                eventService.logErrorNoUser(e)
                Future.successful(InternalServerError(views.errors.public500(None)).asLeft)
              },
              {
                case (None, _) =>
                  eventService.logSystem(
                    EventType.LoggedInUserAccountDeleted,
                    s"Utilisateur connecté mais le compte n'existe pas",
                    s"Path ${request.path}".some
                  )
                  Future(
                    userNotLogged(
                      s"Votre compte a été supprimé. Contactez votre référent ou l'équipe d'Administration+ sur ${Constants.supportEmail} en cas de problème."
                    )
                  )
                case (Some(user), userSession) =>
                  if (user.disabled) {
                    LoginAction.readUserRights(user).map { userRights =>
                      implicit val requestWithUserData =
                        new RequestWithUserData(user, userRights, userSession, request)
                      eventService.log(
                        EventType.UserAccessDisabled,
                        s"Utilisateur désactivé essaye d'accéder à une page",
                        s"Path ${request.path}".some
                      )
                      userNotLogged(
                        s"Votre compte a été désactivé. Contactez votre référent ou l'équipe d'Administration+ sur ${Constants.supportEmail} en cas de problème."
                      )
                    }
                  } else {
                    // The None case is legacy to avoid disconnecting everybody
                    val sessionIsValid = userSession.map(_.isValid(Instant.now())).getOrElse(true)
                    if (sessionIsValid) {
                      manageUserLogged(user, userSession)
                    } else {
                      Future.successful(
                        userNotLogged("Votre session a expiré. Veuillez vous reconnecter.")
                      )
                    }
                  }
              }
            )
          )
      case (_, _, _, Some(signupId)) =>
        // Note: the exchange between signupId and userId is logged by EventType.SignupFormSuccessful
        manageSignup(signupId)
      case _ =>
        if (routes.HomeController.index.url.contains(path)) {
          Future(userNotLoggedOnLoginPage)
        } else {
          publicPage match {
            case None =>
              // Here request.path is supposed to be safe, because it was previously
              // validated by the router (this class is a Play Action and not a Play Filter)
              log.warn(s"Accès à la page ${request.path} non autorisé")
              Future(userNotLogged("Vous devez vous identifier pour accéder à cette page."))
            case Some(page) =>
              Future.successful(page.asLeft)
          }
        }
    }
  }

  private def tryInsecureAuthByKey[A](
      user: User,
      redirectUrl: String,
      redirectPath: String
  )(implicit request: Request[A]): Future[Result] =
    LoginAction.readUserRights(user).flatMap { userRights =>
      val area = user.areas.headOption
        .flatMap(Area.fromId)
        .getOrElse(Area.all.head)
      if (config.insecureAreasWithLoginByKey.contains(area.id) && !user.admin) {
        // areasWithLoginByKey is an insecure setting for demo usage
        val loginExpiresAt =
          Instant.now().plusSeconds(config.magicLinkSessionDurationInSeconds)
        (
          for {
            userSession <- userService
              .createNewUserSession(
                user.id,
                UserSession.LoginType.InsecureDemoKey,
                loginExpiresAt,
                request.remoteAddress,
              )
            _ <- EitherT
              .right[Error](IO.blocking(userService.recordLogin(user.id)))
            _ <- EitherT.right[Error](
              IO.blocking(
                eventService.log(
                  EventType.LoginByKey,
                  "Connexion par clé réussie (seulement pour la demo / " +
                    "CE LOG NE DOIT PAS APPARAITRE EN PROD !!! Si c'est le cas, " +
                    "il faut vider la variable d'environnement correspondant à areasWithLoginByKey)"
                )(
                  new RequestWithUserData(user, userRights, userSession.some, request)
                )
              )
            )
          } yield TemporaryRedirect(Call(request.method, redirectUrl).url)
            .removingFromSession(LoginAction.signupSessionKeys: _*)
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
      } else {
        eventService.log(TryLoginByKey, "Clé dans l'url, redirige vers la page de connexion")(
          new RequestWithUserData(user, userRights, none, request)
        )
        Future.successful(
          TemporaryRedirect(routes.LoginController.login.url)
            .flashing("email" -> user.email, "path" -> redirectPath)
        )
      }
    }

  private def tryAuthByToken[A](
      rawToken: String
  )(implicit request: Request[A]): Future[Either[Result, RequestWithUserData[A]]] = {
    def unknownTokenResponse = Future.successful(
      userNotLogged(
        "Le lien que vous avez utilisé n'est plus valide, il a déjà été utilisé. " +
          "Vous pouvez générer un nouveau lien en saisissant votre email dans le champ " +
          "prévu à cet effet."
      )
    )
    if (LoginToken.isValid(rawToken))
      tokenService
        .byTokenThenDelete(rawToken)
        .flatMap(
          _.fold(
            e => {
              eventService.logErrorNoUser(e)
              if (e.eventType === EventType.TokenDoubleUsage)
                // Note: a few users are confused by login errors, we provide here
                //       a better explanation than the default Play error page.
                //       A nice HTML page might be better though.
                Future(
                  userNotLogged(
                    "Le même lien semble avoir été utilisé 2 fois. " +
                      "Nous vous invitons à aller sur vos demandes " +
                      "afin de vérifier si votre première tentative est valide. " +
                      "Si vous n’accédez pas à vos demandes automatiquement, " +
                      "un nouveau lien de connexion doit vous être envoyé. " +
                      "Dans ce cas il vous suffit de saisir votre email dans le champ prévu " +
                      "à cet effet sur la page d’accueil."
                  )
                )
              else
                Future(generic500.asLeft)
            },
            {
              case None =>
                eventService.info(
                  User.systemUser,
                  request.remoteAddress,
                  "UNKNOWN_TOKEN",
                  s"Token inconnu",
                  s"Token '$rawToken'".some,
                  none,
                  none,
                  none
                )
                unknownTokenResponse
              case Some(token) =>
                token.origin match {
                  case LoginToken.Origin.User(userId) => manageTokenWithUserId(token, userId)
                  case LoginToken.Origin.Signup(signupId) =>
                    manageTokenWithSignupId(token, signupId)
                }
            }
          )
        )
    else {
      eventService.logSystem(
        EventType.InvalidToken,
        "Token invalide",
        s"Token '$rawToken'".some
      )
      unknownTokenResponse
    }
  }

  private def manageUserLogged[A](user: User, userSession: Option[UserSession])(implicit
      request: Request[A]
  ) =
    LoginAction.readUserRights(user).map { userRights =>
      implicit val requestWithUserData =
        new RequestWithUserData(user, userRights, userSession, request)
      if (user.cguAcceptationDate.nonEmpty || request.path.contains("cgu")) {
        Right(requestWithUserData)
      } else {
        eventService.log(ToCGURedirected, "Redirection vers les CGUs")
        Left(
          TemporaryRedirect(routes.UserController.showValidateAccount.url)
            .flashing("redirect" -> request.path)
        )
      }
    }

  private def manageSignup[A](signupId: UUID)(implicit request: Request[A]) = {
    eventService.logSystem(
      ToCGURedirected,
      s"Redirection vers le formulaire d'inscription (préinscription $signupId)"
    )
    // Not an infinite redirect because `signupForm` does not use `LoginAction`
    Future(
      Left(
        TemporaryRedirect(routes.SignupController.signupForm.url)
          .flashing("redirect" -> request.path)
      )
    )
  }

  private def manageTokenWithUserId[A](token: LoginToken, userId: UUID)(implicit
      request: Request[A]
  ): Future[Either[Result, RequestWithUserData[A]]] = {
    val userOption: Option[User] = userService.byId(userId)
    userOption match {
      case None =>
        eventService.logSystem(
          EventType.UserNotFound,
          s"Tentative de connexion par token valide ${token.token} " +
            s"mais l'utilisateur $userId n'existe pas (peut-être supprimé ?)"
        )
        Future(userNotLogged("Une erreur s'est produite, votre utilisateur n'existe plus"))
      case Some(user) =>
        LoginAction.readUserRights(user).flatMap { userRights =>
          if (token.ipAddress =!= request.remoteAddress) {
            eventService.log(
              AuthWithDifferentIp,
              s"Utilisateur $userId à une adresse ip différente pour l'essai de connexion"
            )(new RequestWithUserData(user, userRights, none, request))
          }

          if (token.isActive) {
            val url = request.path + queryToString(
              request.queryString - Keys.QueryParam.key - Keys.QueryParam.token
            )
            val loginExpiresAt = Instant.now().plusSeconds(config.magicLinkSessionDurationInSeconds)
            (
              for {
                userSession <- userService
                  .createNewUserSession(
                    user.id,
                    UserSession.LoginType.MagicLink,
                    loginExpiresAt,
                    request.remoteAddress,
                  )
                _ <- EitherT
                  .right[Error](IO.blocking(userService.recordLogin(user.id)))
                _ <- EitherT.right[Error](
                  IO.blocking(
                    eventService.log(
                      EventType.AuthByKey,
                      "Identification par token"
                    )(
                      new RequestWithUserData(user, userRights, userSession.some, request)
                    )
                  )
                )
              } yield TemporaryRedirect(Call(request.method, url).url)
                .removingFromSession(LoginAction.signupSessionKeys: _*)
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
              .map(_.asLeft)
          } else {
            eventService.log(ExpiredToken, s"Token expiré pour $userId")(
              new RequestWithUserData(user, userRights, none, request)
            )
            Future(
              redirectToHomeWithEmailSendbackButton(
                user.email,
                s"Votre lien de connexion a expiré, il est valable ${config.tokenExpirationInMinutes} minutes à réception."
              )
            )
          }
        }
    }
  }

  private def manageTokenWithSignupId[A](token: LoginToken, signupId: UUID)(implicit
      request: Request[A]
  ): Future[Either[Result, RequestWithUserData[A]]] =
    signupService
      .byId(signupId)
      .map(
        _.fold(
          e => {
            eventService.logErrorNoUser(e)
            generic500.asLeft
          },
          {
            case None =>
              eventService.logSystem(
                EventType.MissingSignup,
                s"Tentative de connexion par token valide ${token.token} " +
                  s"avec une préinscription inconnue : $signupId"
              )
              userNotLogged(
                "Une erreur s'est produite, les données sur votre inscription n'existent plus"
              )
            case Some(signupRequest) =>
              if (token.isActive) {
                val url = request.path + queryToString(
                  request.queryString - Keys.QueryParam.key - Keys.QueryParam.token
                )
                val loginExpiresAt =
                  Instant.now().plusSeconds(config.magicLinkSessionDurationInSeconds)
                eventService.logSystem(
                  EventType.AuthBySignupToken,
                  s"Identification par token avec la préinscription ${signupRequest.id} (expiration : $loginExpiresAt)"
                )
                Left(
                  TemporaryRedirect(Call(request.method, url).url)
                    .addingToSession(
                      Keys.Session.signupId -> signupRequest.id.toString,
                      Keys.Session.signupLoginExpiresAt -> loginExpiresAt.getEpochSecond.toString,
                    )
                )
              } else {
                eventService.logSystem(
                  ExpiredToken,
                  s"Token expiré pour la préinscription ${signupRequest.id}"
                )
                redirectToHomeWithEmailSendbackButton(
                  signupRequest.email,
                  s"Votre lien de connexion a expiré, il est valable ${config.tokenExpirationInMinutes} minutes à réception."
                )
              }
          }
        )
      )

  private def userNotLogged[A](message: String)(implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.LoginController.login.url)
        .removingFromSession(
          (Keys.Session.userId :: Keys.Session.sessionId :: LoginAction.signupSessionKeys): _*
        )
        .flashing("error" -> message)
    )

  private def redirectToHomeWithEmailSendbackButton[A](email: String, message: String)(implicit
      request: Request[A]
  ) =
    Left(
      TemporaryRedirect(routes.HomeController.index.url)
        .removingFromSession(
          (Keys.Session.userId :: Keys.Session.sessionId :: LoginAction.signupSessionKeys): _*
        )
        .flashing("email" -> email, "error" -> message)
    )

  private def userNotLoggedOnLoginPage[A](implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.HomeController.index.url)
        .removingFromSession(
          (Keys.Session.userId :: Keys.Session.sessionId :: LoginAction.signupSessionKeys): _*
        )
    )

  // Note: Instead of a blank page with a message, sending back to the home page
  //       similar to `redirectToHomeWithEmailSendbackButton` might be better
  private def generic500 =
    InternalServerError(
      "Une erreur s’est produite sur le serveur. " +
        "Celle-ci est possiblement temporaire, " +
        "nous vous invitons à réessayer plus tard."
    )

}
