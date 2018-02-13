package models

import java.util.UUID

import org.joda.time.DateTime
import org.joda.time.Period

case class Application(id: UUID,
                       status: String,
                       creationDate: DateTime,
                       creatorUserName: String,
                       creatorUserId: UUID,
                       subject: String,
                       description: String,
                       userInfos: Map[String, String],
                       invitedUsers: Map[UUID, String],
                       area: UUID) {
   lazy val ageString = {
     val period = new Period(creationDate, DateTime.now(Time.timeZone))
     if(period.getMonths > 1) {
       s"il y a ${period.getMonths} mois"
     } else if(period.getWeeks > 1) {
       s"il y a ${period.getWeeks} semaines"
     } else if(period.getDays > 1) {
       s"il y a ${period.getDays} jours"
     } else if(period.getHours > 1) {
       s"il y a ${period.getHours} heures"
     } else if(period.getMinutes > 1) {
       s"il y a ${period.getMinutes} minutes"
     } else {
       s"à l'instant"
     }
   }
}