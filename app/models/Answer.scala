package models

import java.util.UUID

import org.joda.time.{DateTime, Period}

case class Answer(id: UUID,
                  applicationId: UUID,
                  creationDate: DateTime,
                  message: String,
                  creatorUserID: UUID,
                  creatorUserName: String,
                  invitedUsers: Map[UUID, String],
                  visibleByHelpers: Boolean,
                  area: UUID,
                  declareApplicationHasIrrelevant: Boolean,
                  userInfos: Option[Map[String, String]]){

  lazy val age = new Period(creationDate, DateTime.now(Time.dateTimeZone))
  lazy val ageString = {
    if(age.getMonths > 0) {
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