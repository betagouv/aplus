package filters

import akka.stream.Materializer
import cats.syntax.all._
import io.sentry.{ITransaction, Sentry, SpanStatus}
import javax.inject.Inject
import play.api.mvc.{Filter, RequestHeader, Result}
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys

class SentryFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  val urisWeDoNotWantToTrace = List(
    "/favicon.ico",
    "/assets/",
    "/webjars/",
    "/jsRoutes"
  )

  val queryParamsWeDoWantToTrace = List(
    Keys.QueryParam.vue,
    Keys.QueryParam.uniquementFs,
    Keys.QueryParam.numOfMonthsDisplayed,
    Keys.QueryParam.filterIsOpen
  )

  def apply(
      nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    val target = requestHeader.target
    val filteredQuery = target.queryMap
      .filterNot { case (key, _) => queryParamsWeDoWantToTrace.exists(_ === key) }
    val uri = target.withQueryString(filteredQuery).uriString
    val transactionOpt: Option[ITransaction] =
      if (urisWeDoNotWantToTrace.exists(prefix => uri.startsWith(prefix))) {
        None
      } else {
        val transactionName = s"${requestHeader.method} $uri"
        val transaction = Sentry.startTransaction(transactionName, "task")
        Some(transaction)
      }

    nextFilter(requestHeader).map { result =>
      transactionOpt.foreach { transaction =>
        result.session(requestHeader).get(Keys.Session.userId).foreach { userId =>
          transaction.setTag("user_id", userId)
        }
        transaction.setStatus(SpanStatus.fromHttpStatusCode(result.header.status))
        transaction.finish()
      }
      result
    }
  }

}
