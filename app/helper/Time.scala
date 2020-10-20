package helper

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.collection.immutable.ListMap

object Time {

  private val timeZoneString = "Europe/Paris"
  val timeZoneParis = ZoneId.of(timeZoneString)

  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] =
    Ordering.fromLessThan(_.isBefore(_))

  def nowParis() = ZonedDateTime.now(timeZoneParis)

  def formatPatternFr(date: ZonedDateTime, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRANCE))

  def formatPatternFr(date: LocalDate, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRANCE))

  val hourAndMinutesFormatter = DateTimeFormatter.ofPattern("HH'h'mm")

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

  /** Example `ListMap(2018/02 -> février 2018, 2018/03 -> mars 2018)` */
  def monthsMap(fromDate: ZonedDateTime, toDate: ZonedDateTime): ListMap[String, String] = {
    val keyFormatter = DateTimeFormatter.ofPattern("YYYY/MM", Locale.FRANCE)
    val valueFormatter = DateTimeFormatter.ofPattern("MMMM YYYY", Locale.FRANCE)
    val beginning = fromDate.withDayOfMonth(1)
    def recursion(date: ZonedDateTime): ListMap[String, String] =
      if (date.isBefore(beginning)) {
        ListMap()
      } else {
        recursion(date.minusMonths(1)) +
          (date.format(keyFormatter) -> date.format(valueFormatter))
      }
    recursion(toDate)
  }

}
