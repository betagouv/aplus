package models

import cats.{Eq, Show}
import cats.syntax.all._
import helper.{Pseudonymizer, Time}
import java.time.{Instant, ZonedDateTime}
import java.util.UUID
import models.Answer.AnswerType
import models.Application.SeenByUser
import models.Application.Status.{Archived, New, Processed, Processing, Sent, ToArchive}

case class Application(
    id: UUID,
    creationDate: ZonedDateTime,
    creatorUserName: String,
    creatorUserId: UUID,
    creatorGroupId: Option[UUID],
    // Name of the group "at the time of application creation"
    creatorGroupName: Option[String],
    subject: String,
    description: String,
    // TODO: rename `userInfos` => `usagerInfos`
    userInfos: Map[String, String],
    // Contains all the invited users at creation and in all answers
    // (except the last one, as long as it has not been saved)
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
    mandatType: Option[Application.MandatType],
    mandatDate: Option[String],
    invitedGroupIdsAtCreation: List[UUID],
    isInFranceServicesNetwork: Boolean,
    personalDataWiped: Boolean = false,
) extends AgeModel {

  val invitedGroups: Set[UUID] =
    (invitedGroupIdsAtCreation ::: answers.flatMap(_.invitedGroupIds)).toSet

  val seenByUserIds: List[UUID] = seenByUsers.map(_.userId)

  val seenByUsersMap: Map[UUID, Instant] = seenByUsers.map { case SeenByUser(userId, date) =>
    (userId, date)
  }.toMap

  def newAnswersFor(userId: UUID): List[Answer] = {
    val maybeSeenLastDate = seenByUsers.find(_.userId === userId).map(_.lastSeenDate)
    maybeSeenLastDate
      .map(seenLastDate => answers.filter(_.creationDate.toInstant.isAfter(seenLastDate)))
      .getOrElse(answers)
  }

  lazy val searchData: String = {
    val stripChars = "\"<>'"
    val areaName: String = Area.fromId(area).map(_.name).orEmpty
    val creatorName: String = creatorUserName.filterNot(stripChars contains _)
    val creatorGroupNameAtCreation: String =
      creatorGroupName.map(_.filterNot(stripChars contains _)).orEmpty
    val userInfosStripped: String =
      userInfos.values.map(_.filterNot(stripChars contains _)).mkString(" ")
    val subjectStripped: String = subject.filterNot(stripChars contains _)
    val descriptionStripped: String = description.filterNot(stripChars contains _)
    val invitedUserNames: String =
      invitedUsers.values.map(_.filterNot(stripChars contains _)).mkString(" ")
    val answersStripped: String =
      answers.map(_.message.filterNot(stripChars contains _)).mkString(" ")

    areaName + " " +
      creatorName + " " +
      creatorGroupNameAtCreation + " " +
      userInfosStripped + " " +
      subjectStripped + " " +
      descriptionStripped + " " +
      invitedUserNames + " " +
      answersStripped
  }

  def userAnswers: List[Answer] = answers.filter(answer =>
    answer.answerType =!= AnswerType.InviteByUser &&
      answer.answerType =!= AnswerType.InviteAsExpert &&
      answer.answerType =!= AnswerType.InviteThroughGroupPermission
  )

  private def isProcessed =
    userAnswers.lastOption.exists(_.answerType === AnswerType.ApplicationProcessed)

  private def isCreator(userId: UUID) = userId === creatorUserId

  def hasBeenDisplayedFor(userId: UUID): Boolean =
    isCreator(userId) || seenByUserIds.contains[UUID](userId)

  lazy val answeredByNonCreator = userAnswers.exists(_.creatorUserID =!= creatorUserId)

  def longStatus(user: User): Application.Status =
    closed match {
      case true                                       => Archived
      case false if isProcessed && isCreator(user.id) => ToArchive
      case false if isProcessed                       => Processed
      case false if answeredByNonCreator              => Processing
      case false if isCreator(user.id)                => Sent
      case false                                      => New
    }

  def status: Application.Status =
    closed match {
      case true                          => Archived
      case false if isProcessed          => Processed
      case false if answeredByNonCreator => Processing
      case _                             => New
    }

  def invitedUsers(users: List[User]): List[User] =
    invitedUsers.keys.flatMap(userId => users.find(_.id === userId)).toList

  def allUserInfos: Map[String, String] = userInfos ++ answers.flatMap(_.userInfos.getOrElse(Map()))

  lazy val anonymousApplication: Application = {
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

  // Security (TODO: put in Authorization)

  def canHaveExpertsInvitedBy(user: User) = false

  // TODO : be more open to expert invitation if it's reintroduced
  // (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
  //  creatorUserId === user.id

  def canHaveAgentsInvitedBy(user: User): Boolean =
    (user.instructor && invitedUsers.keys.toList.contains(user.id)) ||
      (user.expert && invitedUsers.keys.toList.contains(user.id) && !closed)

// TODO: remove
  def haveUserInvitedOn(user: User): Boolean = invitedUsers.keys.toList.contains(user.id)

  def withWipedPersonalData: Application = {
    val wipedUsagerInfos: Map[String, String] =
      userInfos.map { case (key, _) => (key, "") }
    val wipedAnswers: List[Answer] = answers.map(answer =>
      answer.copy(
        message = "",
        userInfos = answer.userInfos.map(_.map { case (key, _) => (key, "") }),
      )
    )
    copy(
      subject = "",
      description = "",
      userInfos = wipedUsagerInfos,
      answers = wipedAnswers,
    )
  }

  def anonymize: Application = {
    val wiped = withWipedPersonalData
    val zone = creationDate.getZone
    val anonCreationDate = creationDate.toLocalDate.atStartOfDay(zone).withHour(10)
    val anonMandatDate = mandatDate.map(_ => anonCreationDate.format(Time.dateWithHourFormatter))
    val anonClosedDate = closedDate.map(date => date.toLocalDate.atStartOfDay(zone).withHour(14))
    val pseudoCreatorName = new Pseudonymizer(creatorUserId).fullName
    val pseudoInvitedUsers = invitedUsers.map { case (id, _) =>
      (id, new Pseudonymizer(id).fullName)
    }
    val anonSeenByUsers = seenByUsers.map { case SeenByUser(id, date) =>
      SeenByUser(id, date.atZone(zone).toLocalDate.atStartOfDay(zone).withHour(12).toInstant)
    }
    val anonAnswers = wiped.answers.map(answer =>
      Answer(
        id = answer.id,
        applicationId = answer.applicationId,
        creationDate = answer.creationDate.toLocalDate.atStartOfDay(zone).withHour(12),
        answerType = answer.answerType,
        message = answer.message,
        creatorUserID = answer.creatorUserID,
        creatorUserName = new Pseudonymizer(answer.creatorUserID).fullName,
        invitedUsers = answer.invitedUsers.map { case (id, _) =>
          (id, new Pseudonymizer(id).fullName)
        },
        visibleByHelpers = answer.visibleByHelpers,
        declareApplicationHasIrrelevant = answer.declareApplicationHasIrrelevant,
        userInfos = answer.userInfos,
        invitedGroupIds = answer.invitedGroupIds,
      )
    )
    Application(
      id = id,
      creationDate = anonCreationDate,
      creatorUserName = pseudoCreatorName,
      creatorUserId = wiped.creatorUserId,
      creatorGroupId = wiped.creatorGroupId,
      creatorGroupName = wiped.creatorGroupName,
      subject = wiped.subject,
      description = wiped.description,
      userInfos = wiped.userInfos,
      invitedUsers = pseudoInvitedUsers,
      area = wiped.area,
      irrelevant = wiped.irrelevant,
      answers = anonAnswers,
      internalId = wiped.internalId,
      closed = wiped.closed,
      seenByUsers = anonSeenByUsers,
      usefulness = wiped.usefulness,
      closedDate = anonClosedDate,
      expertInvited = wiped.expertInvited,
      hasSelectedSubject = wiped.hasSelectedSubject,
      category = wiped.category,
      mandatType = wiped.mandatType,
      mandatDate = anonMandatDate,
      invitedGroupIdsAtCreation = wiped.invitedGroupIdsAtCreation,
      isInFranceServicesNetwork = wiped.isInFranceServicesNetwork,
      personalDataWiped = wiped.personalDataWiped,
    )
  }

}

object Application {

  sealed trait Status

  object Status {

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
    implicit val Eq: Eq[Status] = (x: Status, y: Status) => x == y

    implicit val Show: Show[Status] = new Show[Status] {

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
    def now(userId: UUID): SeenByUser = SeenByUser(userId, Instant.now())
  }

  def filesAvailabilityLeftInDays(filesExpirationInDays: Int)(
      application: Application
  ): Option[Int] =
    application.ageInDays.some.map(filesExpirationInDays - _).filter(_ >= 0)

  sealed trait MandatType

  object MandatType {
    case object Sms extends MandatType
    case object Phone extends MandatType
    case object Paper extends MandatType

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
    implicit val Eq: Eq[MandatType] = (x: MandatType, y: MandatType) => x == y

  }

  val UserFirstNameKey = "Prénom"
  val UserLastNameKey = "Nom de famille"
  val UserBirthdayKey = "Date de naissance"
  val UserSocialSecurityNumberKey = "Numéro de sécurité sociale"
  val UserCafNumberKey = "Identifiant CAF"
  val UserAddressKey = "Adresse postale"
  val UserPhoneNumberKey = "Numéro de téléphone"
  val UserApplicationNumberKey = "Numéro de dossier"
  val UserBirthnameKey = "Nom de naissance"

  val optionalUserInfosKeys: List[String] = List(
    UserSocialSecurityNumberKey,
    UserCafNumberKey,
    UserAddressKey,
    UserPhoneNumberKey,
    UserApplicationNumberKey,
    UserBirthnameKey,
  )

  def invitedUserContextualizedName(
      user: User,
      userGroups: List[UserGroup],
      currentAreaId: Option[UUID],
      creatorGroupId: Option[UUID]
  ): String = {
    val defaultContexts: List[String] = currentAreaId
      .fold(userGroups)(areaId => userGroups.filter(_.areaIds.contains[UUID](areaId)))
      .flatMap { (userGroup: UserGroup) =>
        if (user.instructor) {
          for {
            areaInseeCode <- userGroup.areaIds.flatMap(Area.fromId).map(_.inseeCode).headOption
            organisation <- userGroup.organisation
          } yield {
            s"(${organisation.name} - $areaInseeCode)"
          }
        } else {
          List(s"(${userGroup.name})")
        }
      }
      .distinct

    val creatorGroup = creatorGroupId.flatMap(id => userGroups.find(_.id === id))
    val isInCreatorGroup: Boolean =
      creatorGroupId.map(id => user.groupIds.contains[UUID](id)).getOrElse(false)

    val contexts: List[String] =
      if (isInCreatorGroup)
        creatorGroup.fold(defaultContexts) { group =>
          val creatorGroupIsInApplicationArea: Boolean =
            currentAreaId.map(areaId => group.areaIds.contains[UUID](areaId)).getOrElse(true)
          val name: String =
            if (creatorGroupIsInApplicationArea)
              group.name
            else
              group.areaIds
                .flatMap(Area.fromId)
                .map(_.inseeCode)
                .headOption
                .fold(group.name)(code =>
                  if (group.name.contains(code)) group.name else s"${group.name} - $code"
                )
          s"($name)" :: Nil
        }
      else
        defaultContexts

    val capitalizedUserName = user.name.split(' ').map(_.capitalize).mkString(" ")
    if (contexts.isEmpty)
      s"$capitalizedUserName ( ${user.qualite} )"
    else
      s"$capitalizedUserName ${contexts.mkString(",")}"
  }

}
