package extentions

import java.util.Locale

import org.joda.time.{DateTime, DateTimeZone}

import extentions.Time.dateTimeOrdering
import scala.collection.immutable.ListMap

object Time {
  val timeZone = "Europe/Paris"
  val dateTimeZone = DateTimeZone.forID(timeZone)
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isBefore(_))
  def now() = DateTime.now(dateTimeZone)

  def weeksMap(fromDate: DateTime, toDate: DateTime): ListMap[String, String] = {
    val weekDay = toDate.weekOfWeekyear().roundFloorCopy()
    def recursion(date: DateTime): ListMap[String, String] =
      if (date.isBefore(fromDate)) {
        ListMap()
      } else {
        recursion(date.minusWeeks(1)) + (f"${date.getYear}/${date.getWeekOfWeekyear}%02d" -> date
          .toString("E dd MMM YYYY", new Locale("fr")))
      }
    recursion(weekDay)
  }

  def monthsMap(fromDate: DateTime, toDate: DateTime): ListMap[String, String] = {
    def recursion(date: DateTime): ListMap[String, String] =
      if (date.isBefore(fromDate)) {
        ListMap()
      } else {
        recursion(date.minusMonths(1)) + (f"${date.getYear}/${date.getMonthOfYear}%02d" -> date
          .toString("MMMM YYYY", new Locale("fr")))
      }
    recursion(toDate)
  }
}
