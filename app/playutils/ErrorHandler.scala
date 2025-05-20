package playutils

import cats.Eq
import cats.syntax.all._
import helper.ScalatagsHelpers.writeableOf_Modifier
import javax.inject.{Inject, Provider, Singleton}
import play.api.{Configuration, Environment, Mode, OptionalSourceMapper, UsefulException}
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Results.{Forbidden, InternalServerError, NotFound, ServiceUnavailable}
import play.api.routing.Router
import scala.concurrent.Future

@Singleton
class ErrorHandler @Inject() (
    env: Environment,
    config: Configuration,
    sourceMapper: OptionalSourceMapper,
    router: Provider[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  implicit val modeEqInstance: Eq[Mode] = Eq.fromUniversalEquals

  /** This page is seen when there is no CSRF token with message = "No CSRF token found in body"
    */
  override def onForbidden(request: RequestHeader, message: String): Future[Result] =
    Future.successful(Forbidden(views.errors.public403()))

  override def onNotFound(request: RequestHeader, message: String): Future[Result] =
    Future.successful {
      if (env.mode === Mode.Dev) {
        // From https://github.com/playframework/playframework/blob/3.0.1/core/play/src/main/scala/play/api/http/HttpErrorHandler.scala#L230
        NotFound(
          views.html.defaultpages.devNotFound(request.method, request.uri, Some(router.get()))(
            request
          )
        )
      } else {
        NotFound(views.errors.public404())
      }
    }

  override def onProdServerError(
      request: RequestHeader,
      exception: UsefulException
  ): Future[Result] =
    Future.successful {
      serverError(request, exception)
    }

  private def serverError(request: RequestHeader, exception: UsefulException): Result = {
    val isTransient = exception.cause.isInstanceOf[java.sql.SQLTransientConnectionException]
    val result =
      if (isTransient)
        ServiceUnavailable(views.errors.public503())
      else
        InternalServerError(views.errors.public500WithCode(Some(exception.id)))
    result
  }

}
