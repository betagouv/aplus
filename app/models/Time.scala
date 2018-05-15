package models

import org.joda.time.{DateTime, DateTimeZone}

object Time {
  val timeZone = DateTimeZone.forID("Europe/Paris")
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
}
