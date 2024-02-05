package helper

import cats.Order
import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.concurrent.duration.FiniteDuration

object Time {

  private val timeZoneString = "Europe/Paris"
  val timeZoneParis = ZoneId.of(timeZoneString)

  def truncateAtHour(zone: ZoneId)(instant: Instant, hour: Int): Instant =
    instant
      .atZone(zone)
      .toLocalDate
      .atStartOfDay(zone)
      .withHour(hour)
      .toInstant

  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] =
    Ordering.fromLessThan(_.isBefore(_))

  def nowParis() = ZonedDateTime.now(timeZoneParis)

  def formatPatternFr(date: ZonedDateTime, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRANCE))

  def formatPatternFr(date: LocalDate, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRANCE))

  val adminsFormatter = DateTimeFormatter.ofPattern("dd/MM/YY-HH:mm", Locale.FRANCE)

  // Note: we use an Instant here to make clear that we will set our own TZ
  def formatForAdmins(date: Instant): String =
    date.atZone(timeZoneParis).format(adminsFormatter)

  val hourAndMinutesFormatter = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)

  val dateWithHourFormatter = DateTimeFormatter.ofPattern("dd/MM/YYYY H'h'", Locale.FRANCE)

  implicit final val zonedDateTimeInstance: Order[ZonedDateTime] =
    new Order[ZonedDateTime] {
      override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = x.compareTo(y)
    }

  implicit final val instantInstance: Order[Instant] =
    new Order[Instant] {
      override def compare(x: Instant, y: Instant): Int = x.compareTo(y)
    }

  def formatFiniteDuration(duration: FiniteDuration): String = {
    val hours = duration.toHours
    val minutes = duration.toMinutes % 60
    val seconds = duration.toSeconds % 60
    f"$hours%02d:$minutes%02d:$seconds%02d"
  }

}
