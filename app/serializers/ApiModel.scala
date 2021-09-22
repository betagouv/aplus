package serializers

import java.util.UUID
import models.{Area, Organisation}
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

}
