package tasks

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import cats.syntax.all._
import helper.{TasksHelpers, Time}
import java.time.{Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import models.{Error, EventType, User, UserInactivityEvent}
import modules.AppConfig
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future
import scala.concurrent.duration._
import services.{EventService, NotificationService, ServicesDependencies, UserService}

class UserInactivityTask @Inject() (
    config: AppConfig,
    dependencies: ServicesDependencies,
    val eventService: EventService,
    lifecycle: ApplicationLifecycle,
    notificationService: NotificationService,
    userService: UserService,
) extends TasksHelpers {

  import dependencies.ioRuntime

  val inactivityReminder1DelayInDays = config.userInactivityReminder1DelayInDays
  val inactivityReminder2AdditionalDelayInDays = config.userInactivityReminder2AdditionalDelayInDays
  val deactivationAdditionalDelayInDays = config.userInactivityDeactivationAdditionalDelayInDays

  def durationUntilNextTick(now: Instant): IO[FiniteDuration] = IO {
    val nextInstant = now
      .atZone(ZoneOffset.UTC)
      .toLocalDate
      .atStartOfDay(ZoneOffset.UTC)
      .plusDays(1)
      .withHour(5)
      .withMinute(0)
      .toInstant
    now.until(nextInstant, ChronoUnit.MILLIS).millis
  }.flatTap(duration =>
    logMessage(
      EventType.UserInactivityCheck,
      s"Prochaine vérification des utilisateurs inactifs dans ${Time.readableDuration(duration)}"
    )
  )

  def checkUsersInactivity(): IO[Either[Error, Unit]] =
    (for {
      users <- EitherT(
        userService.usersWithLastActivityBefore(
          Instant.now().minus(inactivityReminder1DelayInDays, ChronoUnit.DAYS)
        )
      )
      _ <- users.traverse(runUserInactivityJob)
    } yield ()).value

  def runUserInactivityJob(user: User, lastActivity: Instant): EitherT[IO, Error, Unit] =
    EitherT(userService.userInactivityHistory(user.id)).flatMap { events =>
      EitherT {
        val now = Instant.now()
        val lastActivityDate = lastActivity.atZone(ZoneOffset.UTC).toLocalDate
        val relevantEvents = events.filter(event =>
          event.lastActivityReferenceDate.atZone(ZoneOffset.UTC).toLocalDate == lastActivityDate
        )
        val inactivityReminder1 = relevantEvents
          .find(event => event.eventType === UserInactivityEvent.EventType.InactivityReminder1)

        inactivityReminder1 match {
          case None =>
            val mustSendReminder1 = lastActivity
              .isBefore(now.minus(inactivityReminder1DelayInDays, ChronoUnit.DAYS))
            if (mustSendReminder1) {
              IO.blocking(notificationService.userInactivityReminder1(user.name, user.email))
                .flatMap { _ =>
                  userService.recordUserInactivityEvent(
                    UserInactivityEvent(
                      id = UUID.randomUUID(),
                      userId = user.id,
                      eventType = UserInactivityEvent.EventType.InactivityReminder1,
                      eventDate = now,
                      lastActivityReferenceDate = lastActivity
                    )
                  )
                }
            } else {
              // Note: this case in not supposed to happen
              IO.pure(Right(()))
            }
          case Some(reminder1) =>
            val inactivityReminder2 = relevantEvents
              .find(event => event.eventType === UserInactivityEvent.EventType.InactivityReminder2)

            inactivityReminder2 match {
              case None =>
                val mustSendReminder2 = reminder1.eventDate
                  .isBefore(now.minus(inactivityReminder2AdditionalDelayInDays, ChronoUnit.DAYS))
                if (mustSendReminder2) {
                  IO.blocking(notificationService.userInactivityReminder2(user.name, user.email))
                    .flatMap { _ =>
                      userService.recordUserInactivityEvent(
                        UserInactivityEvent(
                          id = UUID.randomUUID(),
                          userId = user.id,
                          eventType = UserInactivityEvent.EventType.InactivityReminder2,
                          eventDate = now,
                          lastActivityReferenceDate = lastActivity
                        )
                      )
                    }
                } else {
                  // Note: this case happens between first and second reminder
                  IO.pure(Right(()))
                }
              case Some(reminder2) =>
                val mustDeactivate = reminder2.eventDate
                  .isBefore(now.minus(deactivationAdditionalDelayInDays, ChronoUnit.DAYS))
                if (mustDeactivate) {
                  IO.fromFuture(IO(userService.disable(user.id))).flatMap {
                    case Left(error) =>
                      IO.pure(Left(error))
                    case Right(_) =>
                      userService.recordUserInactivityEvent(
                        UserInactivityEvent(
                          id = UUID.randomUUID(),
                          userId = user.id,
                          eventType = UserInactivityEvent.EventType.Deactivation,
                          eventDate = now,
                          lastActivityReferenceDate = lastActivity
                        )
                      )
                  }
                } else {
                  // Note: this case happens between second reminder and deactivation
                  IO.pure(Right(()))
                }
            }
        }

      }
    }

  val cancelCallback: () => Future[Unit] = repeatWithDelay(durationUntilNextTick)(
    loggingResult(
      checkUsersInactivity(),
      EventType.UserInactivityCheck,
      "Vérification des utilisateurs inactifs exécutée",
      EventType.UserInactivityError,
      "Erreur lors de la vérification des utilisateurs inactifs"
    )
  ).unsafeRunCancelable()

  lifecycle.addStopHook { () =>
    cancelCallback()
  }

}
