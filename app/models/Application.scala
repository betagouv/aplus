package models

import java.util.UUID

import org.joda.time.DateTime
import org.joda.time.Period

case class Application(id: UUID,
                       creationDate: DateTime,
                       creatorUserName: String,
                       creatorUserId: UUID,
                       subject: String,
                       description: String,
                       userInfos: Map[String, String],
                       invitedUsers: Map[UUID, String],
                       area: UUID,
                       irrelevant: Boolean,
                       answers: List[Answer] = List(),
                       internalId: Int = -1,
                       closed: Boolean = false) {
   lazy val ageString = {
     val period = new Period(creationDate, DateTime.now(Time.timeZone))
     if(period.getMonths > 0) {
       s"il y a ${period.getMonths} mois"
     } else if(period.getWeeks > 0) {
       s"il y a ${period.getWeeks} semaines"
     } else if(period.getDays > 0) {
       s"il y a ${period.getDays} jours"
     } else if(period.getHours > 0) {
       s"il y a ${period.getHours} heures"
     } else if(period.getMinutes > 0) {
       s"il y a ${period.getMinutes} minutes"
     } else {
       s"à l'instant"
     }
   }

   lazy val searchData = {
     val stripChars = "\"<>'"
     s"${creatorUserName.filterNot(stripChars contains _)} ${userInfos.values.map(_.filterNot(stripChars contains _)).mkString(" ")} ${subject.filterNot(stripChars contains _)} ${description.filterNot(stripChars contains _)} ${invitedUsers.values.map(_.filterNot(stripChars contains _)).mkString(" ")}"
   }

   def status(user: User) = closed match {
     case true => "Clôturé"
     case _ if user.id == creatorUserId && answers.exists(_.creatorUserID != user.id) => "Répondu"
     case _ if user.id == creatorUserId => "Envoyé"
     case _ if answers.exists(_.creatorUserID == user.id) => "Répondu"
     case _ => "Nouvelle"
   }

   def qualities(users: List[User]): Set[String] = invitedUsers.keys.flatMap(userId => users.find(_.id == userId)).map(_.qualite).toSet
}