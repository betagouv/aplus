package tasks

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import helper.{TasksHelpers, Time}
import java.time.Instant
import javax.inject.Inject
import models.{Error, EventType}
import modules.AppConfig
import play.api.inject.ApplicationLifecycle
import scala.concurrent.duration._
import services.{AccountCreationService, EventService, NotificationService, ServicesDependencies}

class AccountCreationTask @Inject() (
    accountCreationService: AccountCreationService,
    config: AppConfig,
    dependencies: ServicesDependencies,
    val eventService: EventService,
    lifecycle: ApplicationLifecycle,
    notificationService: NotificationService,
) extends TasksHelpers {

  import dependencies.ioRuntime

  def durationUntilNextTick(now: Instant): IO[FiniteDuration] =
    untilNextDayAt(2, 35)(now).flatTap(duration =>
      logMessage(
        EventType.SignupsStatistics,
        s"Prochaine statistiques des demandes de création de compte dans " +
          Time.formatFiniteDuration(duration)
      )
    )

  def dailyAccountCreationChecks: IO[Either[Error, Unit]] =
    EitherT(accountCreationService.stats()).flatMap { stats =>
      val (abuseThreshold, formsByIp, abuseByIp) = accountCreationService.currentAbuseCounts()
      EitherT(
        config.accountCreationAdminEmails
          .map(email =>
            notificationService
              .formCreationStatsForAdmins(email, stats, abuseThreshold, formsByIp, abuseByIp)
              .attempt
              .map(result => (email, result))
          )
          .sequence
          .map { results =>
            val failures = results.collect { case (email, Left(e)) => (email, e) }
            if (failures.isEmpty)
              ().asRight[Error]
            else
              Error
                .MiscException(
                  EventType.SignupsError,
                  (
                    "Erreurs lors de la tentative d'envoi " +
                      "des statistiques des demandes de création de compte : " +
                      failures
                        .map { case (email, e) => s"<$email> : ${e.getMessage}" }
                        .mkString(" ; ")
                  ),
                  failures.last._2,
                  none
                )
                .asLeft[Unit]
          }
      )
    }.value

  val cancelCallback =
    (loggingResult(
      accountCreationService.setupRateLimits(),
      EventType.SignupsStatistics,
      "Les rate-limits de demande de création de compte ont été initialisés",
      EventType.SignupsError,
      "Erreur dans le mise en place des rate-limits de demande de création de compte",
    ) >>
      repeatWithDelay(durationUntilNextTick)(
        loggingResult(
          dailyAccountCreationChecks,
          EventType.SignupsStatistics,
          "Statistiques des demandes de création de compte faites",
          EventType.SignupsError,
          "Erreur dans la création des statistiques des demandes de création de compte",
        )
      )).unsafeRunCancelable()

  lifecycle.addStopHook { () =>
    cancelCallback()
  }

}
