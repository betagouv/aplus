package actions

import javax.inject.{Inject, Singleton}

import controllers.routes
import models._
import play.api.mvc._
import play.api.mvc.Results.{Redirect, _}
import services.UserService
import utils.{Data, UUIDHelper}

import scala.concurrent.{ExecutionContext, Future}

class RequestWithUserData[A](val currentUser: User, val currentArea: Area, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class LoginAction @Inject()(val parser: BodyParsers.Default, userService: UserService)(implicit ec: ExecutionContext) extends ActionBuilder[RequestWithUserData, AnyContent] with ActionRefiner[Request, RequestWithUserData] {
  def executionContext = ec

  private def queryToString(qs: Map[String, Seq[String]]) = {
    val queryString = qs.map { case (key, value) => key + "=" + value.sorted.mkString("|,|") }.mkString("&")
    if (queryString.nonEmpty) "?" + queryString else ""
  }

  override def refine[A](request: Request[A]) =
    Future.successful {
      implicit val req = request
      (loginByKeyVerification, loginBySession) match {
        case (Some(user), _)  =>
            val url = request.path + queryToString(request.queryString - "key")
            Left(Redirect(Call(request.method, url)).withSession(request.session - "userId" + ("userId" -> user.id.toString)))
        case (None, Some(user)) =>
            val area = request.session.get("areaId").flatMap(UUIDHelper.fromString).orElse(user.areas.headOption).flatMap(id => Data.areas.find(_.id == id)).getOrElse(Data.areas.head)
            Right(new RequestWithUserData(user, area, request))
        case _ =>
            Left(Redirect(routes.LoginController.home())
              .withSession(request.session - "userId").flashing("error" -> "Vous devez vous identifier pour accèder à cette page."))
      }
    }

  private def loginByKeyVerification[A](implicit request: Request[A]) = request.getQueryString("key").flatMap(userService.byKey)
  private def loginBySession[A](implicit request: Request[A]) = request.session.get("userId").flatMap(UUIDHelper.fromString).flatMap(userService.byId)
}

