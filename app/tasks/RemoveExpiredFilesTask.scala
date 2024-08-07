package tasks

import cats.effect.IO
import helper.{TasksHelpers, Time}
import java.time.{Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import models.EventType
import modules.AppConfig
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future
import scala.concurrent.duration._
import services.{EventService, FileService, ServicesDependencies}

class RemoveExpiredFilesTask @Inject() (
    config: AppConfig,
    dependencies: ServicesDependencies,
    val eventService: EventService,
    fileService: FileService,
    lifecycle: ApplicationLifecycle,
) extends TasksHelpers {

  import dependencies.ioRuntime

  def durationUntilNextTick(now: Instant): IO[FiniteDuration] = IO {
    val nextInstant = now
      .atZone(ZoneOffset.UTC)
      .toLocalDate
      .atStartOfDay(ZoneOffset.UTC)
      .plusDays(1)
      .withHour(5)
      .toInstant
    now.until(nextInstant, ChronoUnit.MILLIS).millis
  }.flatTap(duration =>
    logMessage(
      EventType.FilesDeletion,
      s"Prochaine suppression des fichiers expirés dans ${Time.readableDuration(duration)}"
    )
  )

  val cancelCallback: () => Future[Unit] = repeatWithDelay(durationUntilNextTick)(
    loggingResult(
      fileService.deleteExpiredFiles(),
      EventType.FilesDeletion,
      "Fin de la suppression des fichiers expirés",
      EventType.FileDeletionError,
      "Erreur lors de la suppression des fichiers expirés",
    )
  ).unsafeRunCancelable()

  lifecycle.addStopHook { () =>
    cancelCallback()
  }

}
