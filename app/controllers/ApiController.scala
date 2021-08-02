package controllers

import actions.LoginAction
import cats.syntax.all._
import controllers.Operators.UserOperators
import helper.StringHelper
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.EventType.DeploymentDashboardUnauthorized
import models.{Area, Authorization, Organisation, User, UserGroup}
import play.api.libs.json.Json
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys
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
          groups.find(group => group.email === Some(email))
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
      areas.exists(_.inseeCode === franceServiceInstance.departementCode.code)
    }
  }

  def franceServiceDeployment: Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.isAdminOrObserver) { () =>
        DeploymentDashboardUnauthorized -> "Accès non autorisé au dashboard de déploiement"
      } { () =>
        val userGroups = userGroupService.allGroups.filter(
          _.organisationSetOrDeducted.exists(_.id === Organisation.franceServicesId)
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
          .map { case (franceServiceInstance, groupOpt, area) =>
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
          .flatMap { case (_, sameDepartementLines) =>
            val departementIsDone = sameDepartementLines.forall(_.groupSize >= 2)
            sameDepartementLines.map(_.copy(departementIsDone = departementIsDone))
          }
          .toList
          .sortBy(line => (line.departementCode, line.nomFranceService))
        Future(Ok(Json.toJson(data)))
      }
    }

  private val organisationSetAll: List[Set[Organisation]] = {
    val groups: List[Set[Organisation]] = List(
      Set("DDFIP", "DRFIP"),
      Set("CPAM", "CRAM", "CNAM"),
      Set("CARSAT", "CNAV")
    ).map(_.flatMap(id => Organisation.byId(Organisation.Id(id))))
    val groupedSet: Set[Organisation.Id] = groups.flatMap(_.map(_.id)).toSet
    val nonGrouped: List[Organisation] =
      Organisation.organismesOperateurs.filterNot(org => groupedSet.contains(org.id))
    groups ::: nonGrouped.map(Set(_))
  }

  private val organisationSetFranceService: List[Set[Organisation]] = (
    List(
      Set("DDFIP", "DRFIP"),
      Set("CPAM", "CRAM", "CNAM"),
      Set("CARSAT", "CNAV"),
      Set("ANTS", "Préf")
    ) :::
      List(
        "CAF",
        "CDAD",
        "La Poste",
        "MSA",
        "Pôle emploi"
      ).map(Set(_))
  ).map(_.flatMap(id => Organisation.byId(Organisation.Id(id))))

  private def organisationSetId(organisations: Set[Organisation]): String =
    organisations.map(_.id.toString).mkString

  def deploymentData: Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.isAdminOrObserver) { () =>
        DeploymentDashboardUnauthorized -> "Accès non autorisé au dashboard de déploiement"
      } { () =>
        val userGroups = userGroupService.allGroups
        userService.allNoNameNoEmail.map { users =>
          def usersIn(area: Area, organisationSet: Set[Organisation]): List[User] =
            for {
              group <- userGroups.filter(group =>
                group.areaIds.contains[UUID](area.id)
                  && organisationSet.exists(group.organisationSetOrDeducted.contains[Organisation])
              )
              user <- users if user.groupIds.contains[UUID](group.id)
            } yield user

          val organisationSets: List[Set[Organisation]] =
            if (request.getQueryString(Keys.QueryParam.uniquementFs).getOrElse("oui") === "oui") {
              organisationSetFranceService
            } else {
              organisationSetAll
            }

          val areasData = for {
            area <- request.currentUser.areas.flatMap(Area.fromId).filterNot(_.name === "Demo")
          } yield {
            val numOfInstructors: Map[Set[Organisation], Int] = (
              for {
                organisations <- organisationSets
                users = usersIn(area, organisations)
                userSum = users.count(_.instructor)
              } yield (organisations, userSum)
            ).toMap

            DeploymentData.AreaData(
              areaId = area.id.toString,
              areaName = area.toString,
              numOfInstructorByOrganisationSet = numOfInstructors.map {
                case (organisations, count) => (organisationSetId(organisations), count)
              },
              numOfOrganisationSetWithOneInstructor = numOfInstructors.count(_._2 > 0)
            )
          }

          val numOfAreasWithOneInstructorByOrganisationSet =
            organisationSets.map { organisations =>
              val id = organisationSetId(organisations)
              val count =
                areasData.count(data => data.numOfInstructorByOrganisationSet.getOrElse(id, 0) > 0)
              (id, count)
            }.toMap

          val data = DeploymentData(
            organisationSets = organisationSets.map(organisations =>
              DeploymentData.OrganisationSet(
                id = organisationSetId(organisations),
                organisations = organisations
              )
            ),
            areasData = areasData,
            numOfAreasWithOneInstructorByOrganisationSet =
              numOfAreasWithOneInstructorByOrganisationSet
          )
          Ok(Json.toJson(data))
        }
      }
    }

}
