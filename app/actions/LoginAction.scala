package actions

import cats.implicits.catsSyntaxEq
import constants.Constants
import javax.inject.{Inject, Singleton}
import controllers.routes
import helper.BooleanHelper.not
import helper.UUIDHelper
import models._
import play.api.mvc._
import play.api.mvc.Results.TemporaryRedirect
import serializers.Keys
import services.{EventService, TokenService, UserService}
import models.EventType.{
  AuthByKey,
  AuthWithDifferentIp,
  ExpiredToken,
  LoginByKey,
  ToCGURedirected,
  TryLoginByKey,
  UserAccessDisabled
}
import play.api.Logger

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
    configuration: play.api.Configuration
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
    (userBySession, userByKey, tokenById) match {
      case (Some(userSession), Some(userKey), None) if userSession.id === userKey.id =>
        Future(Left(TemporaryRedirect(Call(request.method, url).url)))
      case (_, Some(user), None) =>
        LoginAction.readUserRights(user).map { userRights =>
          val area = user.areas.headOption
            .flatMap(Area.fromId)
            .getOrElse(Area.all.head)
          implicit val requestWithUserData =
            new RequestWithUserData(user, userRights, request)
          if (areasWithLoginByKey.contains(area.id) && !user.admin) {
            // areasWithLoginByKey is an insecure setting for demo usage and transition only
            eventService.log(
              LoginByKey,
              s"Connexion par clé réussie (Transition ne doit pas être maintenu en prod)"
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
              TemporaryRedirect(routes.LoginController.login().url)
                .flashing("email" -> user.email, "path" -> path)
            )
          }
        }
      case (_, None, Some(token)) =>
        manageToken(token)
      case (Some(user), None, None) if not(user.disabled) =>
        manageUserLogged(user)
      case (Some(user), None, None) if user.disabled =>
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
      case _ =>
        request.getQueryString(Keys.QueryParam.token) match {
          case Some(token) =>
            eventService.info(
              User.systemUser,
              request.remoteAddress,
              "UNKNOWN_TOKEN",
              s"Token $token est inconnu",
              None,
              None,
              None
            )
            Future(
              userNotLogged(
                "Le lien que vous avez utilisé n'est plus valide, il a déjà été utilisé. Si cette erreur se répète, contactez l'équipe Administration+"
              )
            )
          case None if routes.HomeController.index().url.contains(path) =>
            Future(userNotLoggedOnLoginPage)
          case None =>
            log.warn(s"Accès à la ${request.path} non autorisé")
            Future(userNotLogged("Vous devez vous identifier pour accéder à cette page."))
        }
    }
  }

  private def manageUserLogged[A](user: User)(implicit request: Request[A]) =
    LoginAction.readUserRights(user).map { userRights =>
      implicit val requestWithUserData = new RequestWithUserData(user, userRights, request)
      if (user.cguAcceptationDate.nonEmpty || request.path.contains("cgu")) {
        Right(requestWithUserData)
      } else {
        eventService.log(ToCGURedirected, "Redirection vers les CGUs")
        Left(
          TemporaryRedirect(routes.UserController.showCGU().url)
            .flashing("redirect" -> request.path)
        )
      }
    }

  private def manageToken[A](token: LoginToken)(implicit request: Request[A]) = {
    val userOption = userService.byId(token.userId)
    userOption match {
      case None =>
        log.error(s"Try to login by token ${token.token} for an unknown user : ${token.userId}")
        Future(userNotLogged("Une erreur s'est produite, votre utilisateur n'existe plus"))
      case Some(user) =>
        LoginAction.readUserRights(user).map { userRights =>
          //hack: we need RequestWithUserData to call the logger
          implicit val requestWithUserData =
            new RequestWithUserData(user, userRights, request)

          if (token.ipAddress != request.remoteAddress) {
            eventService.log(
              AuthWithDifferentIp,
              s"Utilisateur ${token.userId} à une adresse ip différente pour l'essai de connexion"
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
                  request.session - Keys.Session.userId + (Keys.Session.userId -> user.id.toString)
                )
            )
          } else {
            eventService.log(ExpiredToken, s"Token expiré pour ${token.userId}")
            redirectToHomeWithEmailSendbackButton(
              user.email,
              s"Votre lien de connexion a expiré, il est valable $tokenExpirationInMinutes minutes à réception."
            )
          }
        }
    }
  }

  private def userNotLogged[A](message: String)(implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.LoginController.login().url)
        .withSession(request.session - Keys.Session.userId)
        .flashing("error" -> message)
    )

  private def redirectToHomeWithEmailSendbackButton[A](email: String, message: String)(implicit
      request: Request[A]
  ) =
    Left(
      TemporaryRedirect(routes.HomeController.index().url)
        .withSession(request.session - Keys.Session.userId)
        .flashing("email" -> email, "error" -> message)
    )

  private def userNotLoggedOnLoginPage[A](implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.HomeController.index().url)
        .withSession(request.session - Keys.Session.userId)
    )

  private def tokenById[A](implicit request: Request[A]): Option[LoginToken] =
    request.getQueryString(Keys.QueryParam.token).flatMap(tokenService.byToken)

  private def userByKey[A](implicit request: Request[A]): Option[User] =
    request.getQueryString(Keys.QueryParam.key).flatMap(userService.byKey)

  private def userBySession[A](implicit request: Request[A]): Option[User] =
    request.session
      .get(Keys.Session.userId)
      .flatMap(UUIDHelper.fromString)
      .flatMap(id => userService.byId(id, true))

}
