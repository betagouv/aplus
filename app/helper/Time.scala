package helper

import cats.Order
import java.time.{Instant, LocalDate, YearMonth, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.collection.immutable.ListMap

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

  // Note that .atDay(1) will yield incorrect format value
  def formatMonthYearAllLetters(month: YearMonth): String =
    month.atDay(15).format(monthYearAllLettersFormatter)

  val adminsFormatter = DateTimeFormatter.ofPattern("dd/MM/YY-HH:mm", Locale.FRANCE)
  private val monthYearAllLettersFormatter = DateTimeFormatter.ofPattern("MMMM YYYY", Locale.FRANCE)

  // Note: we use an Instant here to make clear that we will set our own TZ
  def formatForAdmins(date: Instant): String =
    date.atZone(timeZoneParis).format(adminsFormatter)

  val hourAndMinutesFormatter = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)

  val dateWithHourFormatter = DateTimeFormatter.ofPattern("dd/MM/YYYY H'h'", Locale.FRANCE)

  def weeksMap(fromDate: ZonedDateTime, toDate: ZonedDateTime): ListMap[String, String] = {
    val keyFormatter = DateTimeFormatter.ofPattern("YYYY/ww", Locale.FRANCE)
    val valueFormatter = DateTimeFormatter.ofPattern("E dd MMM YYYY", Locale.FRANCE)
    val weekFieldISO = java.time.temporal.WeekFields.of(Locale.FRANCE).dayOfWeek()
    def recursion(date: ZonedDateTime): ListMap[String, String] =
      if (date.isBefore(fromDate)) {
        ListMap()
      } else {
        recursion(date.minusWeeks(1)) +
          (date.format(keyFormatter) -> date.format(valueFormatter))
      }
    val toDateFirstDayOfWeek = toDate.`with`(weekFieldISO, 1)
    recursion(toDateFirstDayOfWeek)
  }

  def monthsBetween(fromDate: ZonedDateTime, toDate: ZonedDateTime): List[YearMonth] = {
    val beginning = fromDate.withDayOfMonth(1)
    def recursion(date: ZonedDateTime): Vector[YearMonth] =
      if (date.isBefore(beginning)) {
        Vector.empty[YearMonth]
      } else {
        recursion(date.minusMonths(1)) :+ YearMonth.from(date)
      }
    recursion(toDate).toList
  }

  implicit final val zonedDateTimeInstance: Order[ZonedDateTime] =
    new Order[ZonedDateTime] {
      override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = x.compareTo(y)
    }

}
