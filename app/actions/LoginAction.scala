package actions

import javax.inject.{Inject, Singleton}

import controllers.routes
import models._
import play.api.mvc._
import play.api.mvc.Results.{Redirect, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class RequestWithUser[A](val currentUser: User, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class LoginAction @Inject()(val parser: BodyParsers.Default)(implicit ec: ExecutionContext) extends ActionBuilder[RequestWithUser, AnyContent] with ActionRefiner[Request, RequestWithUser] {
  def executionContext = ec

  private def queryToString(qs: Map[String, Seq[String]]) = {
    val queryString = qs.map { case (key, value) => key + "=" + value.sorted.mkString("|,|") }.mkString("&")
    if (queryString.nonEmpty) "?" + queryString else ""
  }

  override def refine[A](request: Request[A]) =
    Future.successful {
      request.getQueryString("user").flatMap(User.get) match {
        case Some(user) =>
           val url = request.path + queryToString(request.queryString - "user")
           Left(Redirect(Call(request.method, url)).withSession(request.session - "userId" + ("userId" -> user.id)))
        case None =>
           val user = request.session.get("userId").flatMap(User.get).getOrElse(User.all.head)
           Right(new RequestWithUser(user, request))
      }
    }
}

