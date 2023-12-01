package filters

import cats.syntax.all._
import io.sentry.{ITransaction, Sentry, SpanStatus}
import javax.inject.Inject
import org.apache.pekko.stream.Materializer
import play.api.mvc.{Filter, RequestHeader, Result}
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys

class SentryFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  val urisBlacklist = List(
    "/favicon.ico",
    "/assets/",
    "/webjars/",
    "/jsRoutes"
  )

  val queryParamsWhitelist = List(
    Keys.QueryParam.vue,
    Keys.QueryParam.uniquementFs,
    Keys.QueryParam.numOfMonthsDisplayed
  )

  def apply(
      nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    val filteredQuery = requestHeader.target.queryMap
      .filter { case (key, _) => queryParamsWhitelist.exists(_ === key) }
      .map { case (key, values) => values.map(value => key + "=" + value).mkString("&") }
      .mkString("&")
    // Note that target.withQueryString does not work for our use case (see Play source)
    val uri =
      if (filteredQuery.isEmpty) requestHeader.target.path
      else requestHeader.target.path + "?" + filteredQuery
    val transactionOpt: Option[ITransaction] =
      if (urisBlacklist.exists(prefix => uri.startsWith(prefix))) {
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
