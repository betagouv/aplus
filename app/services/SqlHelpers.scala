package services

import cats.syntax.all._
import java.sql.Connection
import models.{Error, EventType}
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try

/** We use `Future()` here, in order to send the blocking transaction to
  * `dependencies.databaseExecutionContext`.
  */
trait SqlHelpers {

  val db: Database
  val dependencies: ServicesDependencies

  import dependencies.databaseExecutionContext

  def withDbConnection[A](
      errorType: EventType,
      errorMessage: String,
      unsafeData: Option[String] = none,
  )(inner: Connection => A): Future[Either[Error, A]] =
    Future(
      Try(db.withConnection(inner)).toEither.left
        .map(error =>
          Error.SqlException(
            errorType,
            errorMessage,
            error,
            unsafeData
          )
        )
    )

  def withDbTransaction[A](
      errorType: EventType,
      errorMessage: String,
      unsafeData: Option[String] = none,
  )(inner: Connection => A): Future[Either[Error, A]] =
    Future(
      Try(db.withTransaction(inner)).toEither.left
        .map(error =>
          Error.SqlException(
            errorType,
            errorMessage,
            error,
            unsafeData
          )
        )
    )

  def withDbTransactionE[A](
      errorType: EventType,
      errorMessage: String,
      unsafeData: Option[String] = none,
  )(inner: Connection => Either[Error, A]): Future[Either[Error, A]] =
    Future(
      Try(db.withTransaction(inner)).toEither.left
        .map(error =>
          Error.SqlException(
            errorType,
            errorMessage,
            error,
            unsafeData
          )
        )
        .flatten
    )

}
