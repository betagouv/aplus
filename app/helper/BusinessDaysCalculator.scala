package helper

import java.time.{DayOfWeek, LocalDate, ZonedDateTime}
import java.time.temporal.ChronoUnit

object BusinessDaysCalculator {

  def isBusinessDay(date: ZonedDateTime, holidays: Set[LocalDate]): Boolean = {
    val localDate = date.toLocalDate
    val dayOfWeek = date.getDayOfWeek
    !Set(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).contains(dayOfWeek) && !holidays.contains(localDate)
  }

  def businessHoursBetween(
      startTime: ZonedDateTime,
      endTime: ZonedDateTime,
      holidays: Set[LocalDate] = Set.empty
  ): Int = {
    val (start, end) =
      if (endTime.isAfter(startTime)) (startTime, endTime) else (endTime, startTime)

    // scalastyle:ignore var.local
    var totalHours: Int = 0

    if (start.truncatedTo(ChronoUnit.DAYS).isEqual(end.truncatedTo(ChronoUnit.DAYS))) {
      if (isBusinessDay(start, holidays)) {
        totalHours = end.getHour - start.getHour
      }
    } else {
      // scalastyle:ignore var.local
      var current = start.truncatedTo(ChronoUnit.DAYS)
      // scalastyle:ignore while
      while (!current.isAfter(end)) {
        if (isBusinessDay(current, holidays)) {
          if (current.isEqual(start.truncatedTo(ChronoUnit.DAYS))) {
            // For the start day, count hours from start time to end of day
            totalHours += 24 - start.getHour
          } else if (current.isEqual(end.truncatedTo(ChronoUnit.DAYS))) {
            // For the end day, count hours from start of day to end time
            totalHours += end.getHour
          } else {
            // Full business day
            totalHours += 24
          }
        }
        current = current.plusDays(1)
      }
    }

    totalHours
  }

}
