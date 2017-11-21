package actions

import javax.inject.{Inject, Singleton}

import controllers.routes
import models._
import play.api.mvc._
import play.api.mvc.Results.{Redirect, _}
import services.UserService

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class RequestWithUser[A](val currentUser: User, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class LoginAction @Inject()(val parser: BodyParsers.Default, userService: UserService)(implicit ec: ExecutionContext) extends ActionBuilder[RequestWithUser, AnyContent] with ActionRefiner[Request, RequestWithUser] {
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
            val url = request.path + queryToString(request.queryString - "key" - "city")
            Left(Redirect(Call(request.method, url)).withSession(request.session - "userId" + ("userId" -> user.id)))
        case (None, Some(user)) =>
           val url = request.path + queryToString(request.queryString - "userId")
            Right(new RequestWithUser(user, request))
        case _ =>
            Left(Redirect(routes.LoginController.home())
              .withSession(request.session - "userId").flashing("error" -> "Vous devez vous identifier pour accèder à cette page."))
      }
    }

  private def loginByKeyVerification[A](implicit request: Request[A]) = request.getQueryString("key").flatMap(userService.byKey)
  private def loginBySession[A](implicit request: Request[A]) = request.session.get("userId").flatMap(userService.byId)
}

