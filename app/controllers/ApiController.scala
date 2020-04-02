package controllers

import actions.LoginAction
import Operators.UserOperators
import helper.StringHelper
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.EventType.DeploymentDashboardUnauthorized
import models.{Area, Authorization, Organisation, UserGroup}
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import serializers.ApiModel._
import services.{EventService, OrganisationService, UserGroupService, UserService}

@Singleton
case class ApiController @Inject() (
    loginAction: LoginAction,
    eventService: EventService,
    organisationService: OrganisationService,
    userService: UserService,
    userGroupService: UserGroupService
)(implicit val ec: ExecutionContext)
    extends InjectedController
    with UserOperators {
  import OrganisationService.FranceServiceInstance

  private def matchFranceServiceInstance(
      franceServiceInstance: FranceServiceInstance,
      groups: List[UserGroup],
      doNotMatchTheseEmails: Set[String]
  ): Option[UserGroup] = {
    def byEmail: Option[UserGroup] =
      franceServiceInstance.contactMail.flatMap(email =>
        if (doNotMatchTheseEmails.contains(email)) {
          None
        } else {
          groups.find(group => (group.email: Option[String]) == (Some(email): Option[String]))
        }
      )
    def byName: Option[UserGroup] =
      groups.find(group =>
        StringHelper
          .stripEverythingButLettersAndNumbers(group.name)
          .contains(
            StringHelper.stripEverythingButLettersAndNumbers(franceServiceInstance.nomFranceService)
          )
      )
    def byCommune: Option[UserGroup] =
      groups.find(group =>
        StringHelper
          .stripEverythingButLettersAndNumbers(group.name)
          .contains(StringHelper.stripEverythingButLettersAndNumbers(franceServiceInstance.commune))
      )
    byEmail.orElse(byName).orElse(byCommune).filter { userGroup =>
      val areas: List[Area] = userGroup.areaIds.flatMap(Area.fromId)
      areas.exists(_.inseeCode == franceServiceInstance.departementCode.code)
    }
  }

  def franceServiceDeployment: Action[AnyContent] = loginAction.async { implicit request =>
    asUserWithAuthorization(Authorization.isAdminOrObserver) { () =>
      DeploymentDashboardUnauthorized -> "Accès non autorisé au dashboard de déploiement"
    } { () =>
      val userGroups = userGroupService.allGroups.filter(
        _.organisationSetOrDeducted.exists(_.id == Organisation.franceServicesId)
      )
      val franceServiceInstances = organisationService.franceServiceInfos.instances
      val doNotMatchTheseEmails =
        franceServiceInstances
          .flatMap(_.contactMail)
          .groupBy(identity)
          .filter(_._2.length > 1)
          .keys
          .toSet
      val matches: List[(FranceServiceInstance, Option[UserGroup], Area)] =
        franceServiceInstances
          .map(instance =>
            (
              instance,
              matchFranceServiceInstance(instance, userGroups, doNotMatchTheseEmails),
              Area.fromInseeCode(instance.departementCode.code).getOrElse(Area.notApplicable)
            )
          )
      val allGroupIds: List[UUID] = matches.flatMap(_._2).map(_.id)
      val allUsers = userService.byGroupIds(allGroupIds)
      val groupSizes: Map[UUID, Int] = allUsers
        .flatMap(user => user.groupIds.map(groupId => (groupId, user)))
        .groupBy(_._1) // Group by groupId
        .map { case (groupId, users) => (groupId, users.size) }
        .toMap
      val data: List[FranceServiceInstanceLine] = matches
        .map {
          case (franceServiceInstance, groupOpt, area) =>
            FranceServiceInstanceLine(
              nomFranceService = franceServiceInstance.nomFranceService,
              commune = franceServiceInstance.commune,
              departementName = area.name,
              departementCode = area.inseeCode,
              matchedGroup = groupOpt.map(_.name),
              groupSize = groupOpt.flatMap(group => groupSizes.get(group.id)).getOrElse(0),
              departementIsDone = false,
              contactMail = franceServiceInstance.contactMail,
              phone = franceServiceInstance.phone
            )
        }
        .groupBy(_.departementCode)
        .flatMap {
          case (_, sameDepartementLines) =>
            val departementIsDone = sameDepartementLines.forall(_.groupSize >= 2)
            sameDepartementLines.map(_.copy(departementIsDone = departementIsDone))
        }
        .toList
        .sortBy(line => (line.departementCode, line.nomFranceService))
      Future(Ok(Json.toJson(data)))
    }
  }

}
