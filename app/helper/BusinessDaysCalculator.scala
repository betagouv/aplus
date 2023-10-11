package helper

import java.time.{DayOfWeek, ZonedDateTime}
import java.time.temporal.ChronoUnit

object BusinessDaysCalculator {

  def isBusinessDay(date: ZonedDateTime): Boolean = {
    val dayOfWeek = date.getDayOfWeek
    !Set(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).contains(dayOfWeek)
  }

  def businessHoursBetween(startTime: ZonedDateTime, endTime: ZonedDateTime): Int = {
    val (start, end) =
      if (endTime.isAfter(startTime)) (startTime, endTime) else (endTime, startTime)

    // scalastyle:ignore var.local
    var totalHours: Int = 0

    if (start.truncatedTo(ChronoUnit.DAYS).isEqual(end.truncatedTo(ChronoUnit.DAYS))) {
      if (isBusinessDay(start)) {
        totalHours = end.getHour - start.getHour
      }
    } else {
      // scalastyle:ignore var.local
      var current = start.truncatedTo(ChronoUnit.DAYS)
      // scalastyle:ignore while
      while (!current.isAfter(end)) {
        if (isBusinessDay(current)) {
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
