package controllers

import actions.{LoginAction, RequestWithUserData}
import cats.syntax.all._
import controllers.Operators.UserOperators
import helper.StringHelper
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Area, Authorization, Error, EventType, Organisation, User, UserGroup}
import play.api.libs.json.{JsValue, Json}
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

  def franceServices: Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        EventType.FSApiAccessUnauthorized,
        "Accès non autorisé à l'API de liste des France Services"
      ) { () =>
        userGroupService.franceServices.map(
          _.fold(
            toFSApiError,
            franceServices => {
              val lines = franceServices.flatMap { case (fsOpt, groupOpt) =>
                fsOpt
                  .map(_.groupId)
                  .orElse(groupOpt.map(_.id))
                  .map(groupId =>
                    FranceServices.Line(
                      matricule = fsOpt.map(_.matricule),
                      groupId = groupId,
                      name = groupOpt.map(_.name),
                      description = groupOpt.flatMap(_.description),
                      areas = groupOpt
                        .map(_.areaIds.flatMap(Area.fromId).map(_.toString).mkString(", "))
                        .getOrElse(""),
                      organisation = groupOpt
                        .flatMap(_.organisation.map(_.shortName)),
                      email = groupOpt.flatMap(_.email),
                      publicNote = groupOpt.flatMap(_.publicNote),
                    )
                  )
              }
              Ok(Json.toJson(FranceServices(lines)))
            }
          )
        )
      }
    }

  def addFranceServices: Action[JsValue] =
    loginAction[JsValue](parse.json).async { implicit request: RequestWithUserData[JsValue] =>
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        EventType.FSApiAccessUnauthorized,
        "Accès non autorisé à l'API d'ajout de France Services"
      ) { () =>
        request.body
          .validate[FranceServices.NewMatricules]
          .fold(
            errors => {
              val errorMessage = helper.PlayFormHelper.prettifyJsonFormInvalidErrors(errors)
              eventService.log(EventType.FSMatriculeInvalidData, s"$errorMessage")
              Future.successful(BadRequest(Json.toJson(ApiError(errorMessage))))
            },
            newMatricules => {
              val inserts = newMatricules.matricules.traverse { newLine =>
                (newLine.matricule, newLine.groupId) match {
                  case (Some(matricule), Some(groupId)) =>
                    userGroupService
                      .addFSMatricule(groupId, matricule)
                      .map(
                        _.fold(
                          e => {
                            eventService.logError(e)
                            FranceServices
                              .InsertResult(
                                false,
                                matricule.some,
                                groupId.some,
                                none,
                                errorToMessage(e).some
                              )
                          },
                          _ => {
                            eventService.log(
                              EventType.FSMatriculeChanged,
                              s"Ajout du matricule '$matricule' au groupe $groupId"
                            )
                            FranceServices
                              .InsertResult(false, matricule.some, groupId.some, newLine.name, none)
                          }
                        )
                      )
                  case (Some(matricule), None) if !newLine.name.isEmpty =>
                    val groupId = UUID.randomUUID()
                    val group = UserGroup(
                      id = groupId,
                      name =
                        newLine.name.map(StringHelper.commonStringInputNormalization).getOrElse(""),
                      description =
                        newLine.description.map(StringHelper.commonStringInputNormalization),
                      inseeCode = Nil,
                      creationDate = ZonedDateTime.now(),
                      areaIds = newLine.areaCode
                        .flatMap { code =>
                          val rawCode = code.trim
                          Area.fromInseeCode(if (rawCode.size === 1) "0" + rawCode else rawCode)
                        }
                        .map(_.id)
                        .toList,
                      organisationId = Organisation.franceServicesId.some,
                      email = newLine.email.map(StringHelper.commonStringInputNormalization),
                      publicNote = none,
                      internalSupportComment = newLine.internalSupportComment
                        .map(StringHelper.commonStringInputNormalization),
                    )

                    userGroupService
                      .addGroup(group)
                      .flatMap(
                        _.fold(
                          e => {
                            eventService.logError(e)
                            Future.successful(
                              FranceServices
                                .InsertResult(
                                  false,
                                  matricule.some,
                                  none,
                                  none,
                                  errorToMessage(e).some
                                )
                            )
                          },
                          _ => {
                            eventService.log(
                              EventType.UserGroupCreated,
                              s"Groupe ${group.id} ajouté par l'utilisateur d'id ${request.currentUser.id}",
                              s"Groupe ${group.toLogString}".some
                            )
                            userGroupService
                              .addFSMatricule(groupId, matricule)
                              .map(
                                _.fold(
                                  e => {
                                    eventService.logError(e)
                                    FranceServices
                                      .InsertResult(
                                        true,
                                        matricule.some,
                                        groupId.some,
                                        newLine.name,
                                        errorToMessage(e).some
                                      )
                                  },
                                  _ => {
                                    eventService.log(
                                      EventType.FSMatriculeChanged,
                                      s"Ajout du matricule '$matricule' au groupe $groupId"
                                    )
                                    FranceServices
                                      .InsertResult(
                                        true,
                                        matricule.some,
                                        groupId.some,
                                        newLine.name,
                                        none
                                      )
                                  }
                                )
                              )
                          }
                        )
                      )
                  case _ =>
                    Future.successful(
                      FranceServices.InsertResult(
                        false,
                        newLine.matricule,
                        newLine.groupId,
                        none,
                        "Impossible de traiter la ligne: matricule vide ou informations manquantes".some
                      )
                    )
                }
              }
              inserts.map { resultList =>
                val result = FranceServices.InsertsResult(resultList)
                Ok(Json.toJson(result))
              }
            }
          )
      }
    }

  def updateFranceService: Action[JsValue] =
    loginAction[JsValue](parse.json).async { implicit request: RequestWithUserData[JsValue] =>
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        EventType.FSApiAccessUnauthorized,
        "Accès non autorisé à l'API de mise à jour des France Services"
      ) { () =>
        request.body
          .validate[FranceServices.Update]
          .fold(
            errors => {
              val errorMessage = helper.PlayFormHelper.prettifyJsonFormInvalidErrors(errors)
              eventService.log(EventType.FSMatriculeInvalidData, s"$errorMessage")
              Future.successful(BadRequest(Json.toJson(ApiError(errorMessage))))
            },
            {
              case FranceServices.Update(Some(matriculeUpdate), _) =>
                userGroupService
                  .updateFSMatricule(matriculeUpdate.groupId, matriculeUpdate.matricule)
                  .map(
                    _.fold(
                      toFSApiError,
                      _ => {
                        eventService.log(
                          EventType.FSMatriculeChanged,
                          s"Mise à jour du matricule du groupe ${matriculeUpdate.groupId} : '${matriculeUpdate.matricule}'"
                        )
                        Ok(Json.toJson(Json.obj()))
                      }
                    )
                  )
              case FranceServices.Update(_, Some(groupUpdate)) =>
                userGroupService
                  .updateFSGroup(groupUpdate.matricule, groupUpdate.groupId)
                  .map(
                    _.fold(
                      toFSApiError,
                      _ => {
                        eventService.log(
                          EventType.FSMatriculeChanged,
                          s"Mise à jour du groupe associé au matricule ${groupUpdate.matricule} : '${groupUpdate.groupId}'"
                        )
                        Ok(Json.toJson(Json.obj()))
                      }
                    )
                  )
              case FranceServices.Update(None, None) =>
                Future.successful(Ok(Json.toJson(Json.obj())))
            }
          )
      }
    }

  def deleteFranceService(matricule: Int): Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        EventType.FSApiAccessUnauthorized,
        "Accès non autorisé à l'API de suppression des France Services"
      ) { () =>
        userGroupService
          .deleteFSMatricule(matricule)
          .map(
            _.fold(
              toFSApiError,
              _ => {
                eventService
                  .log(EventType.FSMatriculeChanged, s"Suppression du matricule $matricule")
                Ok(Json.obj())
              }
            )
          )
      }
    }

  private def errorToMessage(error: Error): String =
    error.description + error.unsafeData.map(data => " [" + data + "]").getOrElse("")

  private def toFSApiError(error: Error)(implicit request: RequestWithUserData[_]): Result = {
    eventService.logError(error)
    if (error.eventType === EventType.FSMatriculeInvalidData)
      BadRequest(Json.toJson(ApiError(errorToMessage(error))))
    else if (error.eventType === EventType.FSMatriculeError)
      InternalServerError(Json.toJson(ApiError(errorToMessage(error))))
    else
      InternalServerError(Json.toJson(ApiError(errorToMessage(error))))
  }

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
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        EventType.DeploymentDashboardUnauthorized,
        "Accès non autorisé au dashboard de déploiement"
      ) { () =>
        val userGroups = userGroupService.allGroups.filter(group =>
          group.organisation
            .orElse(Organisation.deductedFromName(group.name))
            .exists(_.id === Organisation.franceServicesId)
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
      asUserWithAuthorization(Authorization.isAdminOrObserver)(
        EventType.DeploymentDashboardUnauthorized,
        "Accès non autorisé au dashboard de déploiement"
      ) { () =>
        val userGroups = userGroupService.allGroups
        userService.allNoNameNoEmail.map { users =>
          def usersIn(area: Area, organisationSet: Set[Organisation]): List[User] =
            for {
              group <- userGroups.filter(group =>
                group.areaIds.contains[UUID](area.id)
                  && organisationSet.exists(
                    group.organisation
                      .orElse(Organisation.deductedFromName(group.name))
                      .contains[Organisation]
                  )
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
          Ok(Json.toJson(data))
        }
      }
    }

}
