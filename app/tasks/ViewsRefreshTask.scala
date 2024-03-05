package tasks

import cats.effect.IO
import helper.TasksHelpers
import java.time.{Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import models.EventType
import modules.AppConfig
import play.api.inject.ApplicationLifecycle
import scala.concurrent.duration._
import services.{DbMaintenanceService, EventService, ServicesDependencies}

class ViewsRefreshTask @Inject() (
    config: AppConfig,
    dependencies: ServicesDependencies,
    val eventService: EventService,
    dbService: DbMaintenanceService,
    lifecycle: ApplicationLifecycle,
) extends TasksHelpers {

  import dependencies.ioRuntime

  def durationUntilNextTick(now: Instant): IO[FiniteDuration] = IO {
    val nextInstant = now
      .atZone(ZoneOffset.UTC)
      .toLocalDate
      .atStartOfDay(ZoneOffset.UTC)
      .plusDays(1)
      .withHour(2)
      .withMinute(10)
      .toInstant
    now.until(nextInstant, ChronoUnit.MILLIS).millis
  }.flatTap(duration =>
    logMessage(EventType.ViewsRefreshMessage, s"Prochains REFRESH MATERIALIZED VIEW dans $duration")
  )

  val cancelCallback = repeatWithDelay(durationUntilNextTick)(
    loggingResult(
      dbService.refreshViews(),
      EventType.ViewsRefreshMessage,
      "Commande REFRESH MATERIALIZED VIEW exécutée",
      EventType.ViewsRefreshError,
      "Erreur lors de l'exécution des commandes REFRESH MATERIALIZED VIEW"
    )
  ).unsafeRunCancelable()

  lifecycle.addStopHook { () =>
    cancelCallback()
  }

}
