package models

import org.joda.time.{DateTime, DateTimeZone}

object Time {
  val timeZone = "Europe/Paris"
  val dateTimeZone = DateTimeZone.forID(timeZone)
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  def now() = DateTime.now(dateTimeZone)
}
