package models

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.{DAYS, HOURS, MINUTES, MONTHS, WEEKS, YEARS}

trait AgeModel {
  val creationDate: ZonedDateTime

  def ageInMinutes: Int = MINUTES.between(creationDate, ZonedDateTime.now()).toInt
  def ageInHours: Int = HOURS.between(creationDate, ZonedDateTime.now()).toInt
  def ageInDays: Int = DAYS.between(creationDate, ZonedDateTime.now()).toInt
  def ageInWeeks: Int = WEEKS.between(creationDate, ZonedDateTime.now()).toInt
  def ageInMonths: Int = MONTHS.between(creationDate, ZonedDateTime.now()).toInt
  def ageInYears: Int = YEARS.between(creationDate, ZonedDateTime.now()).toInt

  def ageString: String =
    if (ageInYears > 0) {
      s"$ageInYears années"
    } else if (ageInMonths > 0) {
      s"$ageInMonths mois"
    } else if (ageInWeeks > 0) {
      s"$ageInWeeks semaines"
    } else if (ageInDays > 0) {
      s"$ageInDays jours"
    } else if (ageInHours > 0) {
      s"$ageInHours heures"
    } else if (ageInMinutes > 0) {
      s"$ageInMinutes minutes"
    } else {
      s"quelques secondes"
    }

}
