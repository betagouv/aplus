package actions

import constants.Constants
import javax.inject.{Inject, Singleton}
import controllers.routes
import helper.UUIDHelper
import models._
import play.api.mvc._
import play.api.mvc.Results.TemporaryRedirect
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
    @deprecated val currentArea: Area,
    request: Request[A]
) extends WrappedRequest[A](request)

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

  override def refine[A](request: Request[A]) =
    Future.successful {
      implicit val req = request
      val path = request.path + queryToString(request.queryString - "key" - "token")
      val url = "http" + (if (request.secure) "s" else "") + "://" + request.host + path
      (userBySession, userByKey, tokenById) match {
        case (Some(userSession), Some(userKey), None) if userSession.id == userKey.id =>
          Left(TemporaryRedirect(Call(request.method, url).url))
        case (_, Some(user), None) =>
          val area = areaFromContext(user)
          implicit val requestWithUserData = new RequestWithUserData(user, area, request)
          if (areasWithLoginByKey.contains(area.id) && !user.admin) {
            // areasWithLoginByKey is an insecure setting for demo usage and transition only
            eventService.log(
              LoginByKey,
              s"Connexion par clé réussi (Transition ne doit pas être maintenu en prod)"
            )
            Left(
              TemporaryRedirect(Call(request.method, url).url)
                .withSession(request.session - "userId" + ("userId" -> user.id.toString))
            )
          } else {
            eventService.log(TryLoginByKey, "Clé dans l'url, redirige vers la page de connexion")
            Left(
              TemporaryRedirect(routes.LoginController.login().url)
                .flashing("email" -> user.email, "path" -> path)
            )
          }
        case (_, None, Some(token)) =>
          manageToken(token)
        case (Some(user), None, None) if user.disabled == false =>
          manageUserLogged(user)
        case (Some(user), None, None) if user.disabled == true =>
          val area = areaFromContext(user)
          implicit val requestWithUserData = new RequestWithUserData(user, area, request)
          eventService.log(
            UserAccessDisabled,
            s"Utilisateur désactivé essaye d'accèder à la page ${request.path}}"
          )
          userNotLogged(
            s"Votre compte a été désactivé. Contactez votre référent ou l'équipe d'Administration+ sur ${Constants.supportEmail} en cas de problème."
          )
        case _ =>
          request.getQueryString("token") match {
            case Some(token) =>
              eventService.info(
                User.systemUser,
                Area.notApplicable,
                request.remoteAddress,
                "UNKNOWN_TOKEN",
                s"Token $token est inconnue",
                None,
                None
              )
              userNotLogged(
                "Le lien que vous avez utilisé n'est plus valide, il a déjà été utilisé. Si cette erreur se répète, contactez l'équipe Administration+"
              )
            case None if path != routes.HomeController.index().url =>
              userNotLoggedOnLoginPage
            case None =>
              Logger.warn(s"Accès à la ${request.path} non autorisé")
              userNotLogged("Vous devez vous identifier pour accèder à cette page.")
          }
      }
    }

  private def manageUserLogged[A](user: User)(implicit request: Request[A]) = {
    val area = areaFromContext(user)
    implicit val requestWithUserData = new RequestWithUserData(user, area, request)
    if (user.cguAcceptationDate.nonEmpty || request.path.contains("cgu")) {
      Right(requestWithUserData)
    } else {
      eventService.log(ToCGURedirected, "Redirection vers les CGUs")
      Left(
        TemporaryRedirect(routes.UserController.showCGU().url).flashing("redirect" -> request.path)
      )
    }
  }

  private def areaFromContext[A](user: User)(implicit request: Request[A]) =
    request.session
      .get("areaId")
      .flatMap(UUIDHelper.fromString)
      .orElse(user.areas.headOption)
      .flatMap(id => Area.all.find(_.id == id))
      .getOrElse(Area.all.head)

  private def manageToken[A](token: LoginToken)(implicit request: Request[A]) = {
    val userOption = userService.byId(token.userId)
    userOption match {
      case None =>
        Logger.error(s"Try to login by token ${token.token} for an unknown user : ${token.userId}")
        userNotLogged("Une erreur s'est produite, votre utilisateur n'existe plus")
      case Some(user) =>
        //hack: we need RequestWithUserData to call the logger
        val area = areaFromContext(user)
        implicit val requestWithUserData = new RequestWithUserData(user, area, request)

        if (token.ipAddress != request.remoteAddress) {
          eventService.log(
            AuthWithDifferentIp,
            s"Utilisateur ${token.userId} à une adresse ip différente pour l'essai de connexion"
          )
        }
        if (token.isActive) {
          val url = request.path + queryToString(request.queryString - "key" - "token")
          eventService.log(AuthByKey, s"Identification par token")
          Left(
            TemporaryRedirect(Call(request.method, url).url)
              .withSession(request.session - "userId" + ("userId" -> user.id.toString))
          )
        } else {
          eventService.log(ExpiredToken, s"Token expiré pour ${token.userId}")
          userNotLogged(
            s"Le lien que vous avez utilisez a expiré (il expire après $tokenExpirationInMinutes minutes), saisissez votre email pour vous reconnecter"
          )
        }
    }
  }

  private def userNotLogged[A](message: String)(implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.LoginController.login().url)
        .withSession(request.session - "userId")
        .flashing("error" -> message)
    )

  private def userNotLoggedOnLoginPage[A](implicit request: Request[A]) =
    Left(
      TemporaryRedirect(routes.LoginController.login().url).withSession(request.session - "userId")
    )

  private def tokenById[A](implicit request: Request[A]) =
    request.getQueryString("token").flatMap(tokenService.byToken)

  private def userByKey[A](implicit request: Request[A]) =
    request.getQueryString("key").flatMap(userService.byKey)

  private def userBySession[A](implicit request: Request[A]) =
    request.session
      .get("userId")
      .flatMap(UUIDHelper.fromString)
      .flatMap(id => userService.byId(id, true))
}
