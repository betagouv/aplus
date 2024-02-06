package helper

import cats.effect.IO
import cats.syntax.all._
import java.time.{Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import models.{Error, EventType}
import scala.concurrent.duration._
import services.EventService

trait TasksHelpers {

  def eventService: EventService

  // Using `.attempt.void` here because we don't want to unwind streams if logging fails
  def logMessage(eventType: EventType, message: String): IO[Unit] =
    IO.blocking(eventService.logNoRequest(eventType, message)).attempt.void

  def logException(eventType: EventType, message: String)(e: Throwable): IO[Unit] =
    IO.blocking(eventService.logErrorNoRequest(Error.MiscException(eventType, message, e, none)))
      .attempt
      .void

  def loggingResult(
      task: IO[Either[Error, Unit]],
      successEventType: EventType,
      successMessage: String,
      errorEventType: EventType,
      errorMessage: String
  ): IO[Unit] =
    task.attempt
      .flatMap(
        _.fold(
          logException(errorEventType, errorMessage),
          _.fold(
            e => IO.blocking(eventService.logErrorNoRequest(e)),
            _ => logMessage(successEventType, successMessage)
          )
        )
      )

  def loggingErrors(
      task: IO[Either[Error, Unit]],
      errorEventType: EventType,
      errorMessage: String
  ): IO[Unit] =
    task.attempt
      .flatMap(
        _.fold(
          logException(errorEventType, errorMessage),
          _.fold(e => IO.blocking(eventService.logErrorNoRequest(e)), _ => IO.pure(()))
        )
      )

  private def nextTick(durationUntilNextTick: Instant => IO[FiniteDuration]): IO[Unit] =
    IO.realTimeInstant
      .flatMap(durationUntilNextTick)
      .flatMap(duration => IO.sleep(duration))

  def repeatWithDelay(delayUntilNextTick: Instant => IO[FiniteDuration])(task: IO[Unit]): IO[Unit] =
    nextTick(delayUntilNextTick) >> task >> repeatWithDelay(delayUntilNextTick)(task)

  def untilNextDayAt(hour: Int, minute: Int)(now: Instant): IO[FiniteDuration] = IO {
    val nextInstant = now
      .atZone(ZoneOffset.UTC)
      .toLocalDate
      .atStartOfDay(ZoneOffset.UTC)
      .plusDays(1)
      .withHour(hour)
      .withMinute(minute)
      .toInstant
    now.until(nextInstant, ChronoUnit.MILLIS).millis
  }

}
