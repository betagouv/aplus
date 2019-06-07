package models

import extentions.Time
import org.joda.time.{DateTime, Days, Period}

trait AgeModel {
  val creationDate: DateTime

  lazy val ageInDays = Days.daysBetween(creationDate, DateTime.now(Time.dateTimeZone)).getDays
  lazy val age = new Period(creationDate, DateTime.now(Time.dateTimeZone))
  lazy val ageString = {
    if(age.getYears > 0) {
      s"${age.getYears} années"
    } else if(age.getMonths > 0) {
      s"${age.getMonths} mois"
    } else if(age.getWeeks > 0) {
      s"${age.getWeeks} semaines"
    } else if(age.getDays > 0) {
      s"${age.getDays} jours"
    } else if(age.getHours > 0) {
      s"${age.getHours} heures"
    } else if(age.getMinutes > 0) {
      s"${age.getMinutes} minutes"
    } else {
      s"quelques secondes"
    }
  }
}
