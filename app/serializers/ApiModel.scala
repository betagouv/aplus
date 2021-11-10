package serializers

import java.time.Instant
import java.util.UUID
import models.{Area, Organisation, User, UserGroup}
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
        organisation = group.organisation.flatMap(Organisation.byId).map(_.shortName),
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

}
