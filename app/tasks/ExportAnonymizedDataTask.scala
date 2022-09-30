package tasks

import cats.effect.IO
import cats.syntax.all._
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
    eventService: EventService,
    lifecycle: ApplicationLifecycle,
) {

  import dependencies.ioRuntime

  def durationUntilNextTick(now: Instant): FiniteDuration = {
    val nextInstant = now
      .atZone(ZoneOffset.UTC)
      .toLocalDate
      .atStartOfDay(ZoneOffset.UTC)
      .plusDays(1)
      .withHour(4)
      .withMinute(15)
      .toInstant
    now.until(nextInstant, ChronoUnit.MILLIS).millis
  }

  // Using `.attempt.void` here because we don't want to unwind streams if logging fails
  def logMessage(message: String): IO[Unit] =
    IO.blocking(eventService.logNoRequest(EventType.AnonymizedDataExportMessage, message))
      .attempt
      .void

  def logError(e: Throwable): IO[Unit] = IO
    .blocking(
      eventService.logErrorNoRequest(
        Error.MiscException(
          EventType.AnonymizedDataExportError,
          "Erreur lors de l'export anonymisé de la BDD",
          e,
          none
        )
      )
    )
    .attempt
    .void

  val nextTick: IO[Unit] =
    IO.realTimeInstant
      .map(durationUntilNextTick)
      .flatMap(duration =>
        logMessage(s"Prochain export anonymisé de la BDD dans $duration") &>
          IO.sleep(duration)
      )

  val task: IO[Unit] =
    if (config.anonymizedExportEnabled)
      IO.blocking(anonymizedDataService.transferData())
        .attempt
        .flatMap(_.fold(logError, _ => logMessage("Export anonymisé de la BDD terminé")))
    else
      logMessage("Export anonymisé de la BDD non activé (aucune action effectuée)")

  val taskStream: IO[Unit] =
    nextTick >> task >> taskStream

  val cancelCallback = taskStream.unsafeRunCancelable()

  lifecycle.addStopHook { () =>
    cancelCallback()
  }

}
