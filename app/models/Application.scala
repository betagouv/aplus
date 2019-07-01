package models

import java.util.UUID

import org.joda.time.{DateTime, Hours, Minutes}

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
                       seenByUserIds: List[UUID] = List(),
                       usefulness: Option[String] = None,
                       closedDate: Option[DateTime] = None,
                       expertInvited: Boolean = false) extends AgeModel {

   lazy val searchData = {
     val stripChars = "\"<>'"
     s"${Area.fromId(area).map(_.name).getOrElse("")} ${creatorUserName.filterNot(stripChars contains _)} ${userInfos.values.map(_.filterNot(stripChars contains _)).mkString(" ")} ${subject.filterNot(stripChars contains _)} ${description.filterNot(stripChars contains _)} ${invitedUsers.values.map(_.filterNot(stripChars contains _)).mkString(" ")} ${answers.map(_.message.filterNot(stripChars contains _)).mkString(" ")}"
   }

   def longStatus(user: User) = closed match {
     case true => "Clôturée"
     case _ if user.id == creatorUserId && answers.exists(_.creatorUserID != user.id) => "Répondu"
     case _ if user.id == creatorUserId && seenByUserIds.intersect(invitedUsers.keys.toList).nonEmpty => "Consultée"
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

  def status = closed match {
    case true => "Clôturée"
    case _ if answers.filterNot(_.creatorUserID != creatorUserId).nonEmpty => "Répondu"
    case _ if seenByUserIds.intersect(invitedUsers.keys.toList).nonEmpty => "Consultée"
    case _ => "Nouvelle"
  }

   def invitedUsers(users: List[User]): List[User] = invitedUsers.keys.flatMap(userId => users.find(_.id == userId)).toList

   def administrations(users: List[User]): List[String] = invitedUsers(users).map(_.qualite).distinct

   def creatorUserQualite(users: List[User]): Option[String] = users.find(_.id == creatorUserId).map(_.qualite)

   def allUserInfos = userInfos ++ answers.flatMap(_.userInfos.getOrElse(Map()))

   lazy val anonymousApplication = {
       val newUsersInfo = userInfos.map{ case (key,value) => key -> s"**$key (${value.length})**" }
       val newAnswers = answers.map{
         answer =>
           answer.copy(userInfos = answer.userInfos.map(_.map{ case (key,value) => key -> s"**$key (${value.length})**" }),
             message = s"** Message de ${answer.message.length} caractères **")
       }
       copy(userInfos = newUsersInfo,
         subject = s"** Sujet de ${subject.length} caractères **",
         description = s"** Description de ${description.length} caractères **",
         answers = newAnswers)
   }

   // Security

   def canBeShowedBy(user: User) =  user.admin ||
     (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
     (user.expert && invitedUsers.keys.toList.contains(user.id) && !closed)||
     creatorUserId==user.id

    def fileCanBeShowed(user: User, answer: UUID) =
      answers.find(_.id == answer) match {
        case None => false
        case Some(answer) if answer.filesAvailabilityLeftInDays == None => false // You can't download expired file
        case Some(answer) if answer.creatorUserID == user.id => false   // You can't download your own file
        case _ =>
          (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
            (user.helper && user.id == creatorUserId)
      }


    def canBeAnsweredBy(user: User) =
      (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
        (user.expert && invitedUsers.keys.toList.contains(user.id) && !closed)||
        creatorUserId==user.id

   def canHaveExpertsInvitedBy(user: User) =
     (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
     creatorUserId==user.id

   def canHaveAgentsInvitedBy(user: User) =
     (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
       (user.expert && invitedUsers.keys.toList.contains(user.id) && !closed)

   def canBeClosedBy(user: User) =
    (user.expert && invitedUsers.keys.toList.contains(user.id)) ||
      creatorUserId==user.id || user.admin

   def haveUserInvitedOn(user: User) = invitedUsers.keys.toList.contains(user.id)


   // Stats
   lazy val estimatedClosedDate = (closedDate,closed) match {
     case (Some(date), _)  => Some(date)
     case (_,true) => Some(answers.lastOption.map(_.creationDate).getOrElse(creationDate))
     case _ => None
   }

   lazy val resolutionTimeInMinutes: Option[Int] = if(closed) {
     val lastDate = answers.lastOption.map(_.creationDate).orElse(closedDate).getOrElse(creationDate)
     Some(Minutes.minutesBetween(creationDate, lastDate).getMinutes())
   } else {
     None
   }

   lazy val firstAgentAnswerDate = answers.find(_.id != creatorUserId).map(_.creationDate)

   lazy val firstAnswerTimeInMinutes = firstAgentAnswerDate.map{ firstAnswerDate => Minutes.minutesBetween(creationDate, firstAnswerDate).getMinutes }
}

object Application {
  val USER_FIRST_NAME_KEY = "Prénom"
  val USER_LAST_NAME_KEY = "Nom de famille"
  val USER_BIRTHDAY_KEY = "Date de naissance"
  val USER_SOCIAL_SECURITY_NUMBER_KEY = "Numéro de sécurité sociale"
  val USER_CAF_NUMBER_KEY = "Identifiant CAF"
  val USER_APPLICATION_NUMBER_KEY = "Numéro de dossier"
  val USER_BIRTHNAME_KEY = "Nom de Naissance"
}