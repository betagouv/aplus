package filters

import akka.stream.Materializer
import io.sentry.{ITransaction, Sentry, SpanStatus}
import javax.inject.Inject
import play.api.mvc.{Filter, RequestHeader, Result}
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys

class SentryFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  val urisWeDoNotWantToTrace = List("/assets/", "/webjars/", "/jsRoutes")

  def apply(
      nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    val uri = requestHeader.uri
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
