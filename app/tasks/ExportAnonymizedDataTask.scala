package tasks

import cats.effect.IO
import cats.syntax.all._
import helper.TasksHelpers
import java.time.{Instant, ZoneOffset, ZonedDateTime}
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import models.{Error, EventType}
import modules.AppConfig
import play.api.inject.ApplicationLifecycle
import scala.concurrent.duration._
import services.{AnonymizedDataService, EventService, ServicesDependencies}

class ExportAnonymizedDataTask @Inject() (
    anonymizedDataService: AnonymizedDataService,
    config: AppConfig,
    dependencies: ServicesDependencies,
    val eventService: EventService,
    lifecycle: ApplicationLifecycle,
) extends TasksHelpers {

  import dependencies.ioRuntime

  def durationUntilNextTick(now: Instant): IO[FiniteDuration] =
    untilNextDayAt(4, 15)(now).flatTap(duration =>
      logMessage(
        EventType.AnonymizedDataExportMessage,
        s"Prochain export anonymisé de la BDD dans $duration"
      )
    )

  val cancelCallback = repeatWithDelay(durationUntilNextTick)(
    if (config.anonymizedExportEnabled)
      loggingResult(
        IO.blocking(anonymizedDataService.transferData().asRight[Error]),
        EventType.AnonymizedDataExportMessage,
        "Export anonymisé de la BDD terminé",
        EventType.AnonymizedDataExportError,
        "Erreur lors de l'export anonymisé de la BDD",
      )
    else
      logMessage(
        EventType.AnonymizedDataExportMessage,
        "Export anonymisé de la BDD non activé (aucune action effectuée)"
      )
  ).unsafeRunCancelable()

  lifecycle.addStopHook { () =>
    cancelCallback()
  }

}
