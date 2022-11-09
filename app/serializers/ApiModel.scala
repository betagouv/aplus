package serializers

import cats.syntax.all._
import helper.Time
import java.time.Instant
import java.util.UUID
import models.{Application, Area, Authorization, Organisation, User, UserGroup}
import play.api.libs.json._

object ApiModel {

  object implicits {
    implicit val areaFormat = Json.format[Area]

    implicit val organisationIdReads =
      implicitly[Reads[String]].map(Organisation.Id.apply)

    implicit val organisationIdWrites =
      implicitly[Writes[String]].contramap[Organisation.Id](_.id)

    implicit val organisationFormat = Json.format[Organisation]
  }

  case class ApiError(message: String)

  object ApiError {
    implicit val apiErrorWrites = Json.writes[ApiError]
  }

  // API model for the admin deploiement
  case class FranceServiceInstanceLine(
      nomFranceService: String,
      commune: String,
      departementName: String,
      departementCode: String,
      matchedGroup: Option[String],
      groupSize: Int,
      departementIsDone: Boolean,
      contactMail: Option[String],
      phone: Option[String]
  )

  implicit val franceServiceInstanceLineFormat = Json.format[FranceServiceInstanceLine]

  case class FranceServices(franceServices: List[FranceServices.Line])

  object FranceServices {

    case class Line(
        matricule: Option[Int],
        groupId: UUID,
        name: Option[String],
        description: Option[String],
        areas: String,
        organisation: Option[String],
        email: Option[String],
        publicNote: Option[String],
    )

    case class NewMatricule(
        matricule: Option[Int],
        groupId: Option[UUID],
        name: Option[String],
        description: Option[String],
        areaCode: Option[String],
        email: Option[String],
        internalSupportComment: Option[String],
    )

    case class NewMatricules(
        matricules: List[NewMatricule],
    )

    case class MatriculeUpdate(matricule: Int, groupId: UUID)
    case class GroupUpdate(matricule: Int, groupId: UUID)

    case class Update(
        matriculeUpdate: Option[MatriculeUpdate],
        groupUpdate: Option[GroupUpdate],
    )

    case class InsertResult(
        hasInsertedGroup: Boolean,
        matricule: Option[Int],
        groupId: Option[UUID],
        groupName: Option[String],
        error: Option[String]
    )

    case class InsertsResult(inserts: List[InsertResult])

    object NewMatricules {
      implicit val franceServicesNewMatriculeReads = Json.reads[NewMatricule]
      implicit val franceServicesNewMatriculesReads = Json.reads[NewMatricules]
    }

    object Update {
      implicit val franceServicesMatriculeUpdateReads = Json.reads[MatriculeUpdate]
      implicit val franceServicesGroupUpdateReads = Json.reads[GroupUpdate]
      implicit val franceServicesUpdateReads = Json.reads[Update]
    }

    implicit val franceServicesLineWrites = Json.writes[FranceServices.Line]
    implicit val franceServicesWrites = Json.writes[FranceServices]
    implicit val franceServicesInsertResult = Json.writes[InsertResult]
    implicit val franceServicesInsertsResult = Json.writes[InsertsResult]
  }

  object DeploymentData {
    import implicits.organisationFormat

    case class OrganisationSet(
        id: String,
        organisations: Set[Organisation]
    )

    case class AreaData(
        areaId: String,
        areaName: String,
        numOfInstructorByOrganisationSet: Map[String, Int],
        numOfOrganisationSetWithOneInstructor: Int
    )

    implicit val organisationSetFormat = Json.format[OrganisationSet]
    implicit val areaDataFormat = Json.format[AreaData]
    implicit val deploymentDataFormat = Json.format[DeploymentData]

  }

  case class DeploymentData(
      organisationSets: List[DeploymentData.OrganisationSet],
      areasData: List[DeploymentData.AreaData],
      numOfAreasWithOneInstructorByOrganisationSet: Map[String, Int]
  )

  // Used for selecting group on the signup page
  case class SelectableGroup(id: UUID, name: String, organisationId: String, areaId: UUID)

  object SelectableGroup {
    implicit val format = Json.format[SelectableGroup]
  }

  object UserInfos {
    case class Group(id: UUID, name: String)

    implicit val userInfosGroupFormat = Json.format[UserInfos.Group]
    implicit val userInfosFormat = Json.format[UserInfos]

    def fromUser(user: User, idToGroup: Map[UUID, UserGroup]): UserInfos = {
      val completeName = {
        val firstName = user.firstName.getOrElse("")
        val lastName = user.lastName.getOrElse("")
        if (firstName.nonEmpty || lastName.nonEmpty) s"${user.name} ($lastName $firstName)"
        else user.name
      }
      UserInfos(
        id = user.id,
        firstName = user.firstName,
        lastName = user.lastName,
        name = user.name,
        completeName = completeName,
        qualite = user.qualite,
        email = user.email,
        phoneNumber = user.phoneNumber,
        helper = user.helperRoleName.nonEmpty,
        instructor = user.instructorRoleName.nonEmpty,
        areas = user.areas.flatMap(Area.fromId).map(_.toString),
        groupNames = user.groupIds.flatMap(idToGroup.get).map(_.name),
        groups = user.groupIds
          .flatMap(idToGroup.get)
          .map(group => UserInfos.Group(group.id, group.name)),
        groupEmails = user.groupIds.flatMap(idToGroup.get).flatMap(_.email),
        groupAdmin = user.groupAdminRoleName.nonEmpty,
        admin = user.adminRoleName.nonEmpty,
        expert = user.expert,
        disabled = user.disabledRoleName.nonEmpty,
        sharedAccount = user.sharedAccount,
        cgu = user.cguAcceptationDate.nonEmpty,
      )
    }

  }

  case class UserInfos(
      id: UUID,
      firstName: Option[String],
      lastName: Option[String],
      name: String,
      completeName: String,
      qualite: String,
      email: String,
      phoneNumber: Option[String],
      helper: Boolean,
      instructor: Boolean,
      areas: List[String],
      groupNames: List[String],
      groups: List[UserInfos.Group],
      groupEmails: List[String],
      groupAdmin: Boolean,
      admin: Boolean,
      expert: Boolean,
      disabled: Boolean,
      sharedAccount: Boolean,
      cgu: Boolean
  )

  object UserGroupInfos {
    import implicits._

    implicit val userGroupInfosFormat = Json.format[UserGroupInfos]

    def fromUserGroup(group: UserGroup): UserGroupInfos =
      UserGroupInfos(
        id = group.id,
        name = group.name,
        description = group.description,
        creationDate = group.creationDate.toInstant,
        areas = group.areaIds.flatMap(Area.fromId).map(_.toString),
        organisation = group.organisation.map(_.shortName),
        email = group.email,
        publicNote = group.publicNote
      )

  }

  case class UserGroupInfos(
      id: UUID,
      name: String,
      description: Option[String],
      creationDate: Instant,
      areas: List[String],
      organisation: Option[String],
      email: Option[String],
      publicNote: Option[String],
  )

  case class SearchResult(users: List[UserInfos], groups: List[UserGroupInfos])

  object SearchResult {
    implicit val searchResultFormat = Json.format[SearchResult]
  }

  // Embedded classes are here to avoid the 22 fields limit in Play Json
  case class ApplicationMetadata(
      id: UUID,
      creationDateFormatted: String,
      creationDay: String,
      creatorUserName: String,
      creatorUserId: UUID,
      areaName: String,
      pertinence: String,
      internalId: Int,
      closed: Boolean,
      usefulness: String,
      closedDateFormatted: Option[String],
      closedDay: Option[String],
      status: String,
      currentUserCanSeeAnonymousApplication: Boolean,
      groups: ApplicationMetadata.Groups,
      stats: ApplicationMetadata.Stats,
  )

  object ApplicationMetadata {

    case class Stats(
        numberOfInvitedUsers: Int,
        numberOfMessages: Int,
        numberOfAnswers: Int,
        firstAnswerTimeInMinutes: String,
        resolutionTimeInMinutes: String,
        firstAnswerTimeInDays: String,
        resolutionTimeInDays: String,
    )

    case class Groups(
        creatorUserGroupsNames: String,
        creatorGroupName: String,
        groupNamesInvitedAtCreation: String,
        groupNamesInvitedOnAnswers: String,
    )

    implicit val applicationMetadataStatsWrites = Json.writes[ApplicationMetadata.Stats]
    implicit val applicationMetadataGroupsWrites = Json.writes[ApplicationMetadata.Groups]
    implicit val applicationMetadataWrites = Json.writes[ApplicationMetadata]

    // Groups needed: creator groups + invited groups at creation + invited groups on answers
    def fromApplication(
        application: Application,
        rights: Authorization.UserRights,
        idToUser: Map[UUID, User],
        idToGroup: Map[UUID, UserGroup]
    ) = {
      val areaName = Area.fromId(application.area).map(_.name).getOrElse("Sans territoire")
      val pertinence = if (!application.irrelevant) "Oui" else "Non"
      val creatorUser = idToUser.get(application.creatorUserId)
      val creatorUserGroupsNames = creatorUser.toList
        .flatMap(_.groupIds)
        .distinct
        .flatMap(idToGroup.get)
        .map(_.name)
        .mkString(",")
      val creatorGroupName =
        application.creatorGroupId.toList.flatMap(idToGroup.get).map(_.name).mkString(",")
      val groupNamesInvitedAtCreation = application.invitedGroupIdsAtCreation.distinct
        .flatMap(idToGroup.get)
        .map(_.name)
        .mkString(",")
      val groupNamesInvitedOnAnswers = application.answers
        .flatMap(_.invitedGroupIds)
        .distinct
        .flatMap(idToGroup.get)
        .map(_.name)
        .mkString(",")
      ApplicationMetadata(
        id = application.id,
        creationDateFormatted = Time.formatForAdmins(application.creationDate.toInstant),
        creationDay = Time.formatPatternFr(application.creationDate, "YYY-MM-dd"),
        creatorUserName = application.creatorUserName,
        creatorUserId = application.creatorUserId,
        areaName = areaName,
        pertinence = pertinence,
        internalId = application.internalId,
        closed = application.closed,
        usefulness = application.usefulness.getOrElse(""),
        closedDateFormatted =
          application.closedDate.map(date => Time.formatForAdmins(date.toInstant)),
        closedDay = application.closedDate.map(date =>
          Time.formatPatternFr(application.creationDate, "YYY-MM-dd")
        ),
        status = application.status.show,
        currentUserCanSeeAnonymousApplication =
          Authorization.canSeeApplication(application)(rights),
        groups = ApplicationMetadata.Groups(
          creatorUserGroupsNames = creatorUserGroupsNames,
          creatorGroupName = creatorGroupName,
          groupNamesInvitedAtCreation = groupNamesInvitedAtCreation,
          groupNamesInvitedOnAnswers = groupNamesInvitedOnAnswers,
        ),
        stats = ApplicationMetadata.Stats(
          numberOfInvitedUsers = application.invitedUsers.size,
          numberOfMessages = application.answers.length + 1,
          numberOfAnswers =
            application.answers.count(_.creatorUserID =!= application.creatorUserId),
          firstAnswerTimeInMinutes =
            application.firstAnswerTimeInMinutes.map(_.toString).getOrElse(""),
          resolutionTimeInMinutes =
            application.resolutionTimeInMinutes.map(_.toString).getOrElse(""),
          firstAnswerTimeInDays = application.firstAnswerTimeInMinutes
            .map(_.toDouble / (60.0 * 24.0))
            .map(days => f"$days%.2f".reverse.dropWhile(_ === '0').reverse.stripSuffix("."))
            .getOrElse(""),
          resolutionTimeInDays = application.resolutionTimeInMinutes
            .map(_.toDouble / (60.0 * 24.0))
            .map(days => f"$days%.2f".reverse.dropWhile(_ === '0').reverse.stripSuffix("."))
            .getOrElse("")
        )
      )
    }

  }

  case class ApplicationMetadataResult(applications: List[ApplicationMetadata])

  object ApplicationMetadataResult {
    implicit val applicationMetadataResultWrites = Json.writes[ApplicationMetadataResult]
  }

}
