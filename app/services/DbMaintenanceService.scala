package services

import anorm._
import cats.effect.IO
import cats.syntax.all._
import javax.inject.Inject
import models.{Error, EventType}
import play.api.db.Database

@javax.inject.Singleton
class DbMaintenanceService @Inject() (db: Database, dependencies: ServicesDependencies) {
  import dependencies.databaseExecutionContext

  def refreshViews(): IO[Either[Error, Unit]] =
    IO.blocking {
      db.withTransaction { implicit connection =>
        val _ = SQL("""REFRESH MATERIALIZED VIEW answer_metadata""").execute()
        val _ = SQL("""REFRESH MATERIALIZED VIEW application_metadata""").execute()
        val _ = SQL("""REFRESH MATERIALIZED VIEW application_seen_by_user""").execute()
        val _ = SQL("""REFRESH MATERIALIZED VIEW user_group_is_in_area""").execute()
        val _ = SQL("""REFRESH MATERIALIZED VIEW user_group_is_invited_on_application""").execute()
        val _ = SQL("""REFRESH MATERIALIZED VIEW user_is_in_user_group""").execute()
        val _ = SQL("""REFRESH MATERIALIZED VIEW user_is_invited_on_application""").execute()
      }
    }.attempt
      .map(
        _.left.map(e =>
          Error.SqlException(
            EventType.ViewsRefreshError,
            s"Impossible d'exécuter REFRESH MATERIALIZED VIEW",
            e,
            none
          )
        )
      )

}
