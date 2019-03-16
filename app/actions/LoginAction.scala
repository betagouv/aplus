package actions

import javax.inject.{Inject, Singleton}
import controllers.routes
import models._
import play.api.mvc._
import play.api.mvc.Results.{Redirect, _}
import services.{EventService, TokenService, UserService}
import extentions.UUIDHelper

import scala.concurrent.{ExecutionContext, Future}

class RequestWithUserData[A](val currentUser: User, val currentArea: Area, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class LoginAction @Inject()(val parser: BodyParsers.Default,
                            userService: UserService,
                            eventService: EventService,
                            tokenService: TokenService)(implicit ec: ExecutionContext) extends ActionBuilder[RequestWithUserData, AnyContent] with ActionRefiner[Request, RequestWithUserData] {
  def executionContext = ec

  private def queryToString(qs: Map[String, Seq[String]]) = {
    val queryString = qs.map { case (key, value) => key + "=" + value.sorted.mkString("|,|") }.mkString("&")
    if (queryString.nonEmpty) "?" + queryString else ""
  }

  override def refine[A](request: Request[A]) =
    Future.successful {
      implicit val req = request
      (loginByTokenVerification.orElse(loginByKeyVerification), loginBySession) match {
        case (Some(user), _)  =>
          val url = request.path + queryToString(request.queryString - "key")
          val area = request.session.get("areaId").flatMap(UUIDHelper.fromString).orElse(user.areas.headOption).flatMap(id => Area.all.find(_.id == id)).getOrElse(Area.all.head)
          implicit val requestWithUserData = new RequestWithUserData(user, area, request)
          eventService.info("AUTH_BY_KEY", s"Identification par clé")
          Left(Redirect(Call(request.method, url)).withSession(request.session - "userId" + ("userId" -> user.id.toString)))
        case (None, Some(user)) =>
            val area = request.session.get("areaId").flatMap(UUIDHelper.fromString).orElse(user.areas.headOption).flatMap(id => Area.all.find(_.id == id)).getOrElse(Area.all.head)
            if(user.hasAcceptedCharte || request.path.contains("charte")) {
              Right(new RequestWithUserData(user, area, request))
            } else {
              Left(Redirect(routes.UserController.showCharte()).flashing("redirect" -> request.path))
            }
        case _ =>
            Left(Redirect(routes.LoginController.home())
              .withSession(request.session - "userId").flashing("error" -> "Vous devez vous identifier pour accèder à cette page."))
      }
    }

  private def loginByTokenVerification[A](implicit request: Request[A]) = request.getQueryString("token").flatMap(tokenService.byToken).flatMap { token =>
    if(token.isActive){
      userService.byId(token.userId)
    } else {
      None
    }
  }
  private def loginByKeyVerification[A](implicit request: Request[A]) = request.getQueryString("key").flatMap(userService.byKey)
  private def loginBySession[A](implicit request: Request[A]) = request.session.get("userId").flatMap(UUIDHelper.fromString).flatMap(userService.byId)
}

