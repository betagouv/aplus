package models

import java.util.UUID

import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.Days

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
                       closed: Boolean = false,
                       seenByUserIds: List[UUID] = List()) {

   lazy val age = new Period(creationDate, DateTime.now(Time.timeZone))
   lazy val ageString = {
     if(age.getMonths > 0) {
       s"il y a ${age.getMonths} mois"
     } else if(age.getWeeks > 0) {
       s"il y a ${age.getWeeks} semaines"
     } else if(age.getDays > 0) {
       s"il y a ${age.getDays} jours"
     } else if(age.getHours > 0) {
       s"il y a ${age.getHours} heures"
     } else if(age.getMinutes > 0) {
       s"il y a ${age.getMinutes} minutes"
     } else {
       s"à l'instant"
     }
   }

   lazy val searchData = {
     val stripChars = "\"<>'"
     s"${creatorUserName.filterNot(stripChars contains _)} ${userInfos.values.map(_.filterNot(stripChars contains _)).mkString(" ")} ${subject.filterNot(stripChars contains _)} ${description.filterNot(stripChars contains _)} ${invitedUsers.values.map(_.filterNot(stripChars contains _)).mkString(" ")}"
   }

   def status(user: User) = closed match {
     case true => "Clôturée"
     case _ if user.id == creatorUserId && answers.exists(_.creatorUserID != user.id) => "Répondu"
     case _ if user.id == creatorUserId && seenByUserIds.intersect(invitedUsers.keys.toList).isEmpty == false => "Consultée"
     case _ if user.id == creatorUserId => "Envoyée"
     case _ if answers.exists(_.creatorUserID == user.id) => "Répondu"
     case _ if answers.exists(_.creatorUserName.contains(user.qualite)) => {
       val username = answers.find(_.creatorUserName.contains(user.qualite))
         .map(_.creatorUserName)
         .getOrElse("un collègue")
         .replaceAll("\\(.*\\)","")
         .trim
       s"Répondu par ${username}"
     }
     case _ if seenByUserIds.contains(user.id) => "Consultée"
     case _ => "Nouvelle"
   }

   def invitedUsers(users: List[User]): List[User] = invitedUsers.keys.flatMap(userId => users.find(_.id == userId)).toList

   def isLateForUser(user: User): Boolean = !closed && invitedUsers.contains(user.id) &&
     answers.forall(_.creatorUserID != user.id) && ( age.getMonths > 0 || age.toStandardDays.getDays > 5 )

   def isNearlyLateForUser(user: User): Boolean = !closed && invitedUsers.contains(user.id) &&
    answers.forall(_.creatorUserID != user.id) && ( age.getMonths > 0 || age.toStandardDays.getDays > 3 )
}