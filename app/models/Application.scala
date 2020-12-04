package models

import java.time.temporal.ChronoUnit.MINUTES
import java.time.{Instant, ZonedDateTime}
import java.util.UUID
import cats.{Eq, Show}
import cats.syntax.all._
import models.Answer.AnswerType.ApplicationProcessed
import models.Application.SeenByUser
import models.Application.Status.{Archived, New, Processed, Processing}

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
    answers: List[Answer] = List.empty[Answer],
    internalId: Int = -1,
    closed: Boolean = false,
    seenByUsers: List[SeenByUser] = List.empty[SeenByUser],
    usefulness: Option[String] = Option.empty[String],
    closedDate: Option[ZonedDateTime] = Option.empty[ZonedDateTime],
    expertInvited: Boolean = false,
    hasSelectedSubject: Boolean = false,
    category: Option[String] = Option.empty[String],
    files: Map[String, Long] = Map.empty[String, Long],
    mandatType: Option[Application.MandatType],
    mandatDate: Option[String],
    invitedGroupIdsAtCreation: List[UUID]
) extends AgeModel {

  // Legacy case, can be removed once data has been cleaned up.
  val isWithoutInvitedGroupIdsLegacyCase: Boolean =
    invitedGroupIdsAtCreation.isEmpty

  val invitedGroups: Set[UUID] =
    (invitedGroupIdsAtCreation ::: answers.flatMap(_.invitedGroupIds)).toSet

  val seenByUserIds = seenByUsers.map(_.userId)

  def newAnswersFor(userId: UUID) = {
    val maybeSeenLastDate = seenByUsers.find(_.userId === userId).map(_.lastSeenDate)
    maybeSeenLastDate
      .map(seenLastDate => answers.filter(_.creationDate.toInstant.isAfter(seenLastDate)))
      .getOrElse(answers)
  }

  lazy val allFiles: Map[String, Long] = {
    files ++ answers.flatMap(_.files).flatten
  }

  lazy val searchData = {
    val stripChars = "\"<>'"
    val areaName: String = Area.fromId(area).map(_.name).orEmpty
    val creatorName: String = creatorUserName.filterNot(stripChars contains _)
    val userInfosStripped: String =
      userInfos.values.map(_.filterNot(stripChars contains _)).mkString(" ")
    val subjectStripped: String = subject.filterNot(stripChars contains _)
    val descriptionStripped: String = description.filterNot(stripChars contains _)
    val invitedUserNames: String =
      invitedUsers.values.map(_.filterNot(stripChars contains _)).mkString(" ")
    val answersStripped: String =
      answers.map(_.message.filterNot(stripChars contains _)).mkString(" ")

    s"$areaName $creatorName $userInfosStripped $subjectStripped $descriptionStripped $invitedUserNames $answersStripped"
  }

  private def isProcessed = answers.lastOption.exists(_.answerType === ApplicationProcessed)
  private def isCreator(user: User) = user.id === creatorUserId

  def longStatus(user: User): Application.Status = {
    def answeredByOtherThan(user: User) = answers.exists(_.creatorUserID =!= user.id)
    lazy val seenByInvitedUser = seenByUserIds.intersect(invitedUsers.keys.toList).nonEmpty

    closed match {
      case true                                                   => Archived
      case false if isProcessed && isCreator(user)                => Application.Status.ToArchive
      case false if isProcessed                                   => Processed
      case false if answeredByOtherThan(user) | seenByInvitedUser => Processing
      case false if isCreator(user)                               => Application.Status.Sent
      case false                                                  => New
      // FIXME Avis de consultation ?
      // FIXME Idée : Trouver un autre signe pour montrer que j'ai vu la demande (gras pour les non lues ? code couleur ?)
    }
  }

  def status: Application.Status = {
    lazy val answeredByCreator = answers.exists(_.creatorUserID === creatorUserId)
    lazy val viewedByAtLeastOneInvitedUser =
      seenByUserIds.intersect(invitedUsers.keys.toList).nonEmpty

    closed match {
      case true                                                       => Archived
      case false if isProcessed                                       => Processed
      case false if answeredByCreator | viewedByAtLeastOneInvitedUser => Processing
      case false                                                      => New
    }
  }

  def invitedUsers(users: List[User]): List[User] =
    invitedUsers.keys.flatMap(userId => users.find(_.id === userId)).toList

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

  def canBeOpenedBy(user: User) =
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

  sealed trait Status

  object Status {

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
    implicit val Eq: Eq[Status] = (x: Status, y: Status) => x == y

    implicit val Show = new Show[Status] {

      override def show(status: Status) = status match {
        case Archived   => "Archivée"
        case ToArchive  => "À archiver"
        case Processed  => "Traitée"
        case Processing => "En cours"
        case Sent       => "Envoyée"
        case New        => "Nouvelle"
      }

    }

    case object Archived extends Status
    case object ToArchive extends Status
    case object Processed extends Status
    case object Processing extends Status
    case object Sent extends Status
    case object New extends Status
  }

  final case class SeenByUser(userId: UUID, lastSeenDate: Instant)

  object SeenByUser {
    def now(userId: UUID) = SeenByUser(userId, Instant.now())
  }

  def filesAvailabilityLeftInDays(filesExpirationInDays: Int)(application: Application) =
    application.ageInDays.some.map(filesExpirationInDays - _).filter(_ >= 0)

  sealed trait MandatType

  object MandatType {
    case object Sms extends MandatType
    case object Phone extends MandatType
    case object Paper extends MandatType

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
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
