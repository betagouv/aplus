package tasks

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, ZonedDateTime}

import cats.Eq
import cats.syntax.all._
import helper.Time
import javax.inject.Inject
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import services.NotificationService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class WeeklyEmailsTask @Inject() (
    actorSystem: ActorSystem,
    configuration: Configuration,
    notificationService: NotificationService
)(implicit executionContext: ExecutionContext) {

  val initialDelay: FiniteDuration = {
    val now = ZonedDateTime.now()
    val nextTick = now.plusHours(1).truncatedTo(ChronoUnit.HOURS)
    // +1 to be sure we will be the next hour (the Akka scheduler has a 100ms resolution)
    val intervalInSeconds: Long = ChronoUnit.SECONDS.between(now, nextTick) + 1
    intervalInSeconds.seconds
  }

  // The akka scheduler will tick every hour,
  // but then we choose to act or not depending on the config
  // (hourly for demo / weekly for prod)
  val delay: FiniteDuration = 1.hour

  actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = initialDelay, delay = delay)(() =>
    checkIfItIsTimeToSendThenSendEmails()
  )

  def checkIfItIsTimeToSendThenSendEmails(): Unit =
    if (checkIfItIsTimeToAct()) {
      notificationService.weeklyEmails()
      ()
    }

  implicit val EqInstance: Eq[DayOfWeek] = (x: DayOfWeek, y: DayOfWeek) => x.name() === y.name()

  // Note: this could be done easily by reading a cron expression
  // from the config. We don't do this to avoid more dependencies.
  // This decision can be reassessed.
  private def checkIfItIsTimeToAct(): Boolean =
    if (configuration.get[Boolean]("app.features.weeklyEmails")) {
      if (configuration.get[Boolean]("app.weeklyEmailsDebugSendHourly")) {
        true
      } else {
        val scheduledDay =
          DayOfWeek.valueOf(configuration.get[String]("app.weeklyEmailsDayOfWeek").toUpperCase)
        val scheduledHour = configuration.get[Int]("app.weeklyEmailsHourOfDay")
        // The crucial part is that `now` is a ZonedDateTime
        // Once we use users' TZ, then the hour will be correct for everyone
        val now: ZonedDateTime = Time.nowParis()
        now.getHour === scheduledHour &&
        now.getDayOfWeek === scheduledDay
      }
    } else {
      false
    }

}
