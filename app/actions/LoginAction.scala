package actions

import cats.syntax.all._
import constants.Constants
import controllers.routes
import helper.BooleanHelper.not
import helper.UUIDHelper
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.EventType.{
  AuthByKey,
  AuthWithDifferentIp,
  ExpiredToken,
  LoginByKey,
  ToCGURedirected,
  TryLoginByKey,
  UserAccessDisabled
}
import models._
import play.api.{Configuration, Logger}
import play.api.mvc.Results.{InternalServerError, TemporaryRedirect}
import play.api.mvc._
import serializers.Keys
import services.{EventService, SignupService, TokenService, UserService}

import scala.concurrent.{ExecutionContext, Future}

class RequestWithUserData[A](
    val currentUser: User,
    // Note: accessible here because we will need to make DB calls to create it (areas)
    val rights: Authorization.UserRights,
    request: Request[A]
) extends WrappedRequest[A](request)

object LoginAction {

  /** Will take services as parameters and make DB calls. */
  def readUserRights(user: User)(implicit ec: ExecutionContext): Future[Authorization.UserRights] =
    Future(
      Authorization.readUserRights(user)
    )

}

//TODO : this class is complicated. Maybe we can split the logic.

@Singleton
class LoginAction @Inject() (
    val parser: BodyParsers.Default,
    userService: UserService,
    eventService: EventService,
    tokenService: TokenService,
    configuration: Configuration,
    signupService: SignupService
)(implicit ec: ExecutionContext)
    extends ActionBuilder[RequestWithUserData, AnyContent]
    with ActionRefiner[Request, RequestWithUserData] {
  def executionContext = ec

  private val log = Logger(classOf[LoginAction])

  private lazy val areasWithLoginByKey = configuration.underlying
    .getString("app.areasWithLoginByKey")
    .split(",")
    .flatMap(UUIDHelper.fromString)

  private lazy val tokenExpirationInMinutes =
    configuration.underlying.getInt("app.tokenExpirationInMinutes")

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
      case (Some(userSession), Some(userKey), None, None) if userSession.id === userKey.id =>
        Future(Left(TemporaryRedirect(Call(request.method, url).url)))
      case (_, Some(user), None, None) =>
        LoginAction.readUserRights(user).map { userRights =>
          val area = user.areas.headOption
            .flatMap(Area.fromId)
            .getOrElse(Area.all.head)
          implicit val requestWithUserData =
            new RequestWithUserData(user, userRights, request)
          if (areasWithLoginByKey.contains(area.id) && !user.admin) {
            // areasWithLoginByKey is an insecure setting for demo usage
            eventService.log(
              LoginByKey,
              "Connexion par clé réussie (seulement pour la demo / " +
                "CE LOG NE DOIT PAS APPARAITRE EN PROD !!! Si c'est le cas, " +
                "il faut vider la variable d'environnement correspondant à areasWithLoginByKey)"
            )
            Left(
              TemporaryRedirect(Call(request.method, url).url)
                .withSession(
                  request.session - Keys.Session.userId + (Keys.Session.userId -> user.id.toString)
                )
            )
          } else {
            eventService.log(TryLoginByKey, "Clé dans l'url, redirige vers la page de connexion")
            Left(
              TemporaryRedirect(routes.LoginController.login.url)
                .flashing("email" -> user.email, "path" -> path)
            )
          }
        }
      case (Some(user), None, None, None) if not(user.disabled) =>
        manageUserLogged(user)
      case (Some(user), None, None, None) if user.disabled =>
        LoginAction.readUserRights(user).map { userRights =>
          implicit val requestWithUserData =
            new RequestWithUserData(user, userRights, request)
          eventService.log(
            UserAccessDisabled,
            s"Utilisateur désactivé essaye d'accéder à la page ${request.path}}"
          )
          userNotLogged(
            s"Votre compte a été désactivé. Contactez votre référent ou l'équipe d'Administration+ sur ${Constants.supportEmail} en cas de problème."
          )
        }
      case (_, _, _, Some(signupId)) =>
        manageSignup(signupId)
      case _ =>
        if (routes.HomeController.index.url.contains(path)) {
          Future(userNotLoggedOnLoginPage)
        } else {
          log.warn(s"Accès à la ${request.path} non autorisé")
          Future(userNotLogged("Vous devez vous identifier pour accéder à cette page."))
        }
    }
  }

  private def tryAuthByToken[A](
      rawToken: String
  )(implicit request: Request[A]): Future[Either[Result, RequestWithUserData[A]]] =
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
                s"Token $rawToken est inconnu",
                None,
                None,
                None
              )
              Future(
                userNotLogged(
                  "Le lien que vous avez utilisé n'est plus valide, il a déjà été utilisé. " +
                    "Vous pouvez générer un nouveau lien en saisissant votre email dans le champ " +
                    "prévu à cet effet."
                )
              )
            case Some(token) =>
              token.origin match {
                case LoginToken.Origin.User(userId)     => manageTokenWithUserId(token, userId)
                case LoginToken.Origin.Signup(signupId) => manageTokenWithSignupId(token, signupId)
              }
          }
        )
      )

  private def manageUserLogged[A](user: User)(implicit request: Request[A]) =
    LoginAction.readUserRights(user).map { userRights =>
      implicit val requestWithUserData = new RequestWithUserData(user, userRights, request)
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
    // Not an infinite redirect because `signupForm` does not uses `LoginAction`
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
        log.error(s"Try to login by token ${token.token} for an unknown user : $userId")
        Future(userNotLogged("Une erreur s'est produite, votre utilisateur n'existe plus"))
      case Some(user) =>
        LoginAction.readUserRights(user).map { userRights =>
          // hack: we need RequestWithUserData to call the logger
          implicit val requestWithUserData =
            new RequestWithUserData(user, userRights, request)

          if (token.ipAddress =!= request.remoteAddress) {
            eventService.log(
              AuthWithDifferentIp,
              s"Utilisateur $userId à une adresse ip différente pour l'essai de connexion"
            )
          }
          if (token.isActive) {
            val url = request.path + queryToString(
              request.queryString - Keys.QueryParam.key - Keys.QueryParam.token
            )
            eventService.log(AuthByKey, s"Identification par token")
            Left(
              TemporaryRedirect(Call(request.method, url).url)
                .withSession(
                  request.session - Keys.Session.userId - Keys.Session.signupId +
                    (Keys.Session.userId -> user.id.toString)
                )
            )
          } else {
            eventService.log(ExpiredToken, s"Token expiré pour $userId")
            redirectToHomeWithEmailSendbackButton(
              user.email,
              s"Votre lien de connexion a expiré, il est valable $tokenExpirationInMinutes minutes à réception."
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
                s"Tentative de connexion par token ${token.token} " +
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
                eventService.logSystem(
                  EventType.AuthBySignupToken,
                  s"Identification par token avec la préinscription ${signupRequest.id}"
                )
                Left(
                  TemporaryRedirect(Call(request.method, url).url)
                    .withSession(
                      request.session - Keys.Session.signupId + (Keys.Session.signupId -> signupRequest.id.toString)
                    )
                )
              } else {
                eventService.logSystem(
                  ExpiredToken,
                  s"Token expiré pour la préinscription ${signupRequest.id}"
                )
                redirectToHomeWithEmailSendbackButton(
                  signupRequest.email,
                  s"Votre lien de connexion a expiré, il est valable $tokenExpirationInMinutes minutes à réception."
                )
              }
          }
        )
      )

  private def userNotLogged[A](message: String)(implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.LoginController.login.url)
        .withSession(request.session - Keys.Session.userId - Keys.Session.signupId)
        .flashing("error" -> message)
    )

  private def redirectToHomeWithEmailSendbackButton[A](email: String, message: String)(implicit
      request: Request[A]
  ) =
    Left(
      TemporaryRedirect(routes.HomeController.index.url)
        .withSession(request.session - Keys.Session.userId - Keys.Session.signupId)
        .flashing("email" -> email, "error" -> message)
    )

  private def userNotLoggedOnLoginPage[A](implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.HomeController.index.url)
        .withSession(request.session - Keys.Session.userId)
    )

  // Note: Instead of a blank page with a message, sending back to the home page
  //       similar to `redirectToHomeWithEmailSendbackButton` might be better
  private def generic500 =
    InternalServerError(
      "Une erreur s’est produite sur le serveur. " +
        "Celle-ci est possiblement temporaire, " +
        "nous vous invitons à réessayer plus tard."
    )

  private def userByKey[A](implicit request: Request[A]): Option[User] =
    request.getQueryString(Keys.QueryParam.key).flatMap(userService.byKey)

  private def userBySession[A](implicit request: Request[A]): Option[User] =
    request.session
      .get(Keys.Session.userId)
      .flatMap(UUIDHelper.fromString)
      .flatMap(id => userService.byId(id, true))

}
