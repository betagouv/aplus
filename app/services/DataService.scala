package services

import cats.effect.IO
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Area, Organisation, User}
import serializers.ApiModel.DeploymentData

object DataService {

  val organisationSetAll: List[Set[Organisation]] = {
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

  val organisationSetFranceService: List[Set[Organisation]] = (
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

}

@Singleton
class DataService @Inject() (
    userService: UserService,
    userGroupService: UserGroupService,
) {

  private def organisationSetId(organisations: Set[Organisation]): String =
    organisations.map(_.id.toString).mkString

  def operateursDeploymentData(
      organisationSets: List[Set[Organisation]],
      areas: List[Area]
  ): IO[DeploymentData] =
    userGroupService.all.flatMap { userGroups =>
      userService.allNoNameNoEmail.map { users =>
        def usersIn(area: Area, organisationSet: Set[Organisation]): List[User] =
          for {
            group <- userGroups.filter(group =>
              group.areaIds.contains[UUID](area.id)
                && organisationSet.exists(group.organisation.contains[Organisation])
            )
            user <- users if user.groupIds.contains[UUID](group.id)
          } yield user

        val areasData = for {
          area <- areas
        } yield {
          val numOfInstructors: Map[Set[Organisation], Int] = (
            for {
              organisations <- organisationSets
              users = usersIn(area, organisations)
              userSum = users
                .filter(user => user.instructor && !user.disabled)
                .map(_.id)
                .distinct
                .size
            } yield (organisations, userSum)
          ).toMap

          DeploymentData.AreaData(
            areaId = area.id.toString,
            areaName = area.toString,
            areaCode = area.inseeCode,
            numOfInstructorByOrganisationSet = numOfInstructors.map { case (organisations, count) =>
              (organisationSetId(organisations), count)
            },
            numOfOrganisationSetWithOneInstructor = numOfInstructors
              .count { case (_, numOfInstructors) => numOfInstructors > 0 }
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
        data
      }
    }

}
