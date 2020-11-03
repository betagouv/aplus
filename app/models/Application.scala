package models

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MINUTES
import java.util.UUID

import cats.Eq
import cats.syntax.all._
import helper.BooleanHelper.not
import models.Authorization.{isExpert, isHelper, isInstructor, UserRights}

case class Application(
    id: UUID,
    creationDate: ZonedDateTime,
    creatorUserName: String,
    creatorUserId: UUID,
    subject: String,
    description: String,
    // TODO: rename `userInfos` => `usagerInfos`
    userInfos: Map[String, String],
    invitedUsers: Map[UUID, String],
    area: UUID,
    irrelevant: Boolean,
    answers: List[Answer] = List(),
    internalId: Int = -1,
    closed: Boolean = false,
    seenByUserIds: List[UUID] = List(),
    usefulness: Option[String] = None,
    closedDate: Option[ZonedDateTime] = None,
    expertInvited: Boolean = false,
    hasSelectedSubject: Boolean = false,
    category: Option[String] = None,
    files: Map[String, Long] = Map(),
    mandatType: Option[Application.MandatType],
    mandatDate: Option[String],
    invitedGroupIds: List[UUID]
) extends AgeModel {

  lazy val allFiles: Map[String, Long] = {
    files ++ answers.flatMap(_.files).flatten
  }

  lazy val searchData = {
    val stripChars = "\"<>'"
    val areaName: String = Area.fromId(area).map(_.name).getOrElse("")
    val creatorName: String = creatorUserName.filterNot(stripChars contains _)
    val userInfosStripped: String =
      userInfos.values.map(_.filterNot(stripChars contains _)).mkString(" ")
    val subjectStripped: String = subject.filterNot(stripChars contains _)
    val descriptionStripped: String = description.filterNot(stripChars contains _)
    val invitedUserNames: String =
      invitedUsers.values.map(_.filterNot(stripChars contains _)).mkString(" ")
    val answersStripped: String =
      answers.map(_.message.filterNot(stripChars contains _)).mkString(" ")

    (areaName + " " +
      creatorName + " " +
      userInfosStripped + " " +
      subjectStripped + " " +
      descriptionStripped + " " +
      invitedUserNames + " " +
      answersStripped)
  }

  def longStatus(user: User) =
    closed match {
      case true => "Archivée"
      case _ if user.id === creatorUserId && answers.exists(_.creatorUserID =!= user.id) =>
        "Répondu"
      case _
          if user.id === creatorUserId && seenByUserIds
            .intersect(invitedUsers.keys.toList)
            .nonEmpty =>
        "Consultée"
      case _ if user.id === creatorUserId                   => "Envoyée"
      case _ if answers.exists(_.creatorUserID === user.id) => "Répondu"
      case _ if answers.exists(_.creatorUserName.contains(user.qualite)) => {
        val username = answers
          .find(_.creatorUserName.contains(user.qualite))
          .map(_.creatorUserName)
          .getOrElse("un collègue")
          .replaceAll("\\(.*\\)", "")
          .trim
        s"Répondu par ${username}"
      }
      case _ if seenByUserIds.contains(user.id) => "Consultée"
      case _                                    => "Nouvelle"
    }

  def status =
    closed match {
      case true                                                            => "Archivée"
      case _ if answers.exists(_.creatorUserID === creatorUserId)          => "Répondu"
      case _ if seenByUserIds.intersect(invitedUsers.keys.toList).nonEmpty => "Consultée"
      case _                                                               => "Nouvelle"
    }

  def invitedUsers(users: List[User]): List[User] =
    invitedUsers.keys.flatMap(userId => users.find(_.id === userId)).toList

  def administrations(users: List[User]): List[String] = invitedUsers(users).map(_.qualite).distinct

  def creatorUserQualite(users: List[User]): Option[String] =
    users.find(_.id === creatorUserId).map(_.qualite)

  def allUserInfos = userInfos ++ answers.flatMap(_.userInfos.getOrElse(Map()))

  lazy val anonymousApplication = {
    val newUsersInfo = userInfos.map { case (key, value) => key -> s"**$key (${value.length})**" }
    val newAnswers = answers.map { answer =>
      answer.copy(
        userInfos = answer.userInfos.map(_.map { case (key, value) =>
          key -> s"**$key (${value.length})**"
        }),
        message = s"** Message de ${answer.message.length} caractères **"
      )
    }
    val result = copy(
      userInfos = newUsersInfo,
      description = s"** Description de ${description.length} caractères **",
      answers = newAnswers
    )
    if (hasSelectedSubject) {
      result
    } else {
      result.copy(subject = s"** Sujet de ${subject.length} caractères **")
    }
  }

  // Security

  def canHaveExpertsInvitedBy(user: User) =
    (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
      creatorUserId === user.id

  def canHaveAgentsInvitedBy(user: User) =
    (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
      (user.expert && invitedUsers.keys.toList.contains(user.id) && !closed)

  def canBeClosedBy(user: User) =
    (user.expert && invitedUsers.keys.toList.contains(user.id)) ||
      creatorUserId === user.id || user.admin

// TODO: remove
  def haveUserInvitedOn(user: User) = invitedUsers.keys.toList.contains(user.id)

  // Stats
  lazy val estimatedClosedDate = (closedDate, closed) match {
    case (Some(date), _) => Some(date)
    case (_, true)       => Some(answers.lastOption.map(_.creationDate).getOrElse(creationDate))
    case _               => None
  }

  lazy val resolutionTimeInMinutes: Option[Int] = if (closed) {
    val lastDate = answers.lastOption.map(_.creationDate).orElse(closedDate).getOrElse(creationDate)
    Some(MINUTES.between(creationDate, lastDate).toInt)
  } else {
    None
  }

  lazy val firstAgentAnswerDate = answers.find(_.id =!= creatorUserId).map(_.creationDate)

  lazy val firstAnswerTimeInMinutes: Option[Int] = firstAgentAnswerDate.map { firstAnswerDate =>
    MINUTES.between(creationDate, firstAnswerDate).toInt
  }

}

object Application {

  def filesAvailabilityLeftInDays(filesExpirationInDays: Int)(application: Application) =
    application.ageInDays.some.map(filesExpirationInDays - _).filter(_ >= 0)

  sealed trait MandatType

  object MandatType {
    case object Sms extends MandatType
    case object Phone extends MandatType
    case object Paper extends MandatType

    implicit val Eq: Eq[MandatType] = (x: MandatType, y: MandatType) => x == y
  }

  val USER_FIRST_NAME_KEY = "Prénom"
  val USER_LAST_NAME_KEY = "Nom de famille"
  val USER_BIRTHDAY_KEY = "Date de naissance"
  val USER_SOCIAL_SECURITY_NUMBER_KEY = "Numéro de sécurité sociale"
  val USER_CAF_NUMBER_KEY = "Identifiant CAF"
  val USER_APPLICATION_NUMBER_KEY = "Numéro de dossier"
  val USER_BIRTHNAME_KEY = "Nom de Naissance"
}
