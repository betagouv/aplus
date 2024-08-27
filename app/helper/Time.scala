package helper

import cats.Order
import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.concurrent.duration.FiniteDuration

object Time {

  private val timeZoneString = "Europe/Paris"
  val timeZoneParis: ZoneId = ZoneId.of(timeZoneString)

  def truncateAtHour(zone: ZoneId)(instant: Instant, hour: Int): Instant =
    instant
      .atZone(zone)
      .toLocalDate
      .atStartOfDay(zone)
      .withHour(hour)
      .toInstant

  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] =
    Ordering.fromLessThan(_.isBefore(_))

  def nowParis(): ZonedDateTime = ZonedDateTime.now(timeZoneParis)

  def formatPatternFr(date: ZonedDateTime, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRANCE))

  def formatPatternFr(date: LocalDate, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRANCE))

  val adminsFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/YY-HH:mm", Locale.FRANCE)

  // Note: we use an Instant here to make clear that we will set our own TZ
  def formatForAdmins(date: Instant): String =
    date.atZone(timeZoneParis).format(adminsFormatter)

  val hourAndMinutesFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)

  val dateWithHourFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/YYYY H'h'", Locale.FRANCE)

  def readableDuration(duration: FiniteDuration): String = {
    val millis = duration.toMillis
    val hours = millis / 3600000
    val minutes = (millis % 3600000) / 60000
    val seconds = (millis % 60000) / 1000
    val remainingMillis = millis % 1000
    f"$hours%02dh:$minutes%02dm:$seconds%02ds.$remainingMillis%03d"
  }

  implicit final val zonedDateTimeInstance: Order[ZonedDateTime] =
    new Order[ZonedDateTime] {
      override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = x.compareTo(y)
    }

  implicit final val instantInstance: Order[Instant] =
    new Order[Instant] {
      override def compare(x: Instant, y: Instant): Int = x.compareTo(y)
    }

}
