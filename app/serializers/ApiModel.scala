package serializers

import cats.syntax.all._
import helper.Time
import java.time.Instant
import java.util.UUID
import models.{Application, Area, Authorization, Organisation, User, UserGroup}
import play.api.libs.json._

object ApiModel {

  object implicits {
    implicit val areaFormat: Format[Area] = Json.format[Area]

    implicit val organisationIdReads: Reads[Organisation.Id] =
      implicitly[Reads[String]].map(Organisation.Id.apply)

    implicit val organisationIdWrites: Writes[Organisation.Id] =
      implicitly[Writes[String]].contramap[Organisation.Id](_.id)

    implicit val organisationFormat: Format[Organisation] = Json.format[Organisation]
  }

  case class ApiError(message: String)

  object ApiError {
    implicit val apiErrorWrites: Writes[ApiError] = Json.writes[ApiError]
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

  implicit val franceServiceInstanceLineFormat: Format[FranceServiceInstanceLine] =
    Json.format[FranceServiceInstanceLine]

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
      implicit val franceServicesNewMatriculeReads: Reads[NewMatricule] = Json.reads[NewMatricule]

      implicit val franceServicesNewMatriculesReads: Reads[NewMatricules] =
        Json.reads[NewMatricules]

    }

    object Update {

      implicit val franceServicesMatriculeUpdateReads: Reads[MatriculeUpdate] =
        Json.reads[MatriculeUpdate]

      implicit val franceServicesGroupUpdateReads: Reads[GroupUpdate] = Json.reads[GroupUpdate]
      implicit val franceServicesUpdateReads: Reads[Update] = Json.reads[Update]
    }

    implicit val franceServicesLineWrites: Writes[FranceServices.Line] =
      Json.writes[FranceServices.Line]

    implicit val franceServicesWrites: Writes[FranceServices] = Json.writes[FranceServices]
    implicit val franceServicesInsertResult: Writes[InsertResult] = Json.writes[InsertResult]
    implicit val franceServicesInsertsResult: Writes[InsertsResult] = Json.writes[InsertsResult]
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

    implicit val organisationSetFormat: Format[OrganisationSet] = Json.format[OrganisationSet]
    implicit val areaDataFormat: Format[AreaData] = Json.format[AreaData]
    implicit val deploymentDataFormat: Format[DeploymentData] = Json.format[DeploymentData]

  }

  case class DeploymentData(
      organisationSets: List[DeploymentData.OrganisationSet],
      areasData: List[DeploymentData.AreaData],
      numOfAreasWithOneInstructorByOrganisationSet: Map[String, Int]
  )

  // Used for selecting group on the signup page
  case class SelectableGroup(id: UUID, name: String, organisationId: String, areaId: UUID)

  object SelectableGroup {
    implicit val format: Format[SelectableGroup] = Json.format[SelectableGroup]
  }

  object UserInfos {

    case class Group(id: UUID, name: String, currentUserCanEditGroup: Boolean)

    case class Permissions(
        helper: Boolean,
        instructor: Boolean,
        groupAdmin: Boolean,
        admin: Boolean,
        expert: Boolean,
        managingOrganisations: List[String],
        managingAreas: List[String],
    )

    implicit val userInfosGroupFormat: Format[UserInfos.Group] = Json.format[UserInfos.Group]

    implicit val userInfosPermissionsFormat: Format[UserInfos.Permissions] =
      Json.format[UserInfos.Permissions]

    implicit val userInfosFormat: Format[UserInfos] = Json.format[UserInfos]

    def fromUser(
        user: User,
        rights: Authorization.UserRights,
        idToGroup: Map[UUID, UserGroup]
    ): UserInfos = {
      val completeName =
        if (user.sharedAccount)
          user.name
        else {
          val firstName = user.firstName.getOrElse("")
          val lastName = user.lastName.getOrElse("")
          if (firstName.nonEmpty || lastName.nonEmpty) User.standardName(firstName, lastName)
          else user.name
        }
      val groups = user.groupIds.flatMap(idToGroup.get)
      val organisations: List[String] =
        groups.flatMap(_.organisation.map(_.shortName)).toSet.toList.sorted
      UserInfos(
        id = user.id,
        firstName = user.firstName,
        lastName = user.lastName,
        name = user.name,
        completeName = completeName,
        qualite = user.qualite,
        email = user.email,
        phoneNumber = user.phoneNumber,
        areas = user.areas.flatMap(Area.fromId).map(_.toString).sorted,
        groupNames = groups.map(_.name),
        groups = groups
          .map(group =>
            UserInfos.Group(group.id, group.name, Authorization.canEditGroup(group)(rights))
          ),
        groupEmails = groups.flatMap(_.email),
        organisations = organisations,
        disabled = user.disabledRoleName.nonEmpty,
        sharedAccount = user.sharedAccount,
        cgu = user.cguAcceptationDate.nonEmpty,
        passwordActivated = user.passwordActivated,
        permissions = Permissions(
          helper = user.helperRoleName.nonEmpty,
          instructor = user.instructorRoleName.nonEmpty,
          groupAdmin = user.groupAdminRoleName.nonEmpty,
          admin = user.adminRoleName.nonEmpty,
          expert = user.expert,
          managingOrganisations =
            user.managingOrganisationIds.flatMap(Organisation.byId).map(_.shortName).sorted,
          managingAreas = user.managingAreaIds.flatMap(Area.fromId).map(_.toString).sorted,
        )
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
      areas: List[String],
      groupNames: List[String],
      groups: List[UserInfos.Group],
      groupEmails: List[String],
      organisations: List[String],
      disabled: Boolean,
      sharedAccount: Boolean,
      cgu: Boolean,
      passwordActivated: Boolean,
      // This case class is a workaround for the 22 fields tuple limit in play-json
      permissions: UserInfos.Permissions,
  )

  object UserGroupInfos {

    implicit val userGroupInfosFormat: Format[UserGroupInfos] = Json.format[UserGroupInfos]

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
    implicit val searchResultFormat: Format[SearchResult] = Json.format[SearchResult]
  }

  object UserGroupSimpleInfos {

    implicit val format: Format[UserGroupSimpleInfos] = Json.format[UserGroupSimpleInfos]

    def fromUserGroup(group: UserGroup): UserGroupSimpleInfos =
      UserGroupSimpleInfos(
        id = group.id,
        name = group.name,
        description = group.description,
        areas = group.areaIds.flatMap(Area.fromId).map(_.toString),
        organisation = group.organisation.map(_.shortName),
        publicNote = group.publicNote,
      )

  }

  case class UserGroupSimpleInfos(
      id: UUID,
      name: String,
      description: Option[String],
      areas: List[String],
      organisation: Option[String],
      publicNote: Option[String],
  )

  case class InviteInfos(applicationId: UUID, areaId: UUID, groups: List[UserGroupSimpleInfos])

  object InviteInfos {
    implicit val format: Format[InviteInfos] = Json.format[InviteInfos]
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

    implicit val applicationMetadataStatsWrites: Writes[ApplicationMetadata.Stats] =
      Json.writes[ApplicationMetadata.Stats]

    implicit val applicationMetadataGroupsWrites: Writes[ApplicationMetadata.Groups] =
      Json.writes[ApplicationMetadata.Groups]

    implicit val applicationMetadataWrites: Writes[ApplicationMetadata] =
      Json.writes[ApplicationMetadata]

    // Groups needed: creator groups + invited groups at creation + invited groups on answers
    def fromApplication(
        application: Application,
        rights: Authorization.UserRights,
        idToUser: Map[UUID, User],
        idToGroup: Map[UUID, UserGroup]
    ): ApplicationMetadata = {
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

    implicit val applicationMetadataResultWrites: Writes[ApplicationMetadataResult] =
      Json.writes[ApplicationMetadataResult]

  }

}
