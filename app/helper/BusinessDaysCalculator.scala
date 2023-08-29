package helper

import java.time.{DayOfWeek, ZonedDateTime}
import java.time.temporal.ChronoUnit

object BusinessDaysCalculator {

  def isBusinessDay(date: ZonedDateTime): Boolean = {
    val dayOfWeek = date.getDayOfWeek
    dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
  }

  def businessHoursBetween(startTime: ZonedDateTime, endTime: ZonedDateTime): Int = {
    val (start, end) =
      if (endTime.isAfter(startTime)) (startTime, endTime) else (endTime, startTime)

    var totalHours: Int = 0

    if (start.truncatedTo(ChronoUnit.DAYS).isEqual(end.truncatedTo(ChronoUnit.DAYS))) {
      if (isBusinessDay(start)) {
        totalHours = end.getHour - start.getHour
      }
    } else {
      var current = start.truncatedTo(ChronoUnit.DAYS)
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
