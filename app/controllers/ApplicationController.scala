package controllers

import actions.{BaseLoginAction, LoginAction, RequestWithUserData}
import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import constants.Constants
import helper.{Time, UUIDHelper}
import helper.BooleanHelper.not
import helper.CSVUtil.escape
import helper.PlayFormHelpers.formErrorsLog
import helper.ScalatagsHelpers.writeableOf_Modifier
import helper.StringHelper.NonEmptyTrimmedString
import helper.TwirlImports.toHtml
import java.nio.file.Path
import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{
  Answer,
  Application,
  Area,
  Authorization,
  Error,
  EventType,
  FileMetadata,
  Mandat,
  Organisation,
  User,
  UserGroup
}
import models.Answer.AnswerType
import models.forms.{
  AnswerFormData,
  ApplicationFormData,
  ApplicationsPageInfos,
  CloseApplicationFormData,
  InvitationFormData,
  StatsFormData
}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{
  Action,
  AnyContent,
  BaseController,
  Call,
  ControllerComponents,
  RequestHeader,
  Result,
  Session
}
import play.twirl.api.Html
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import serializers.ApiModel.{
  ApplicationMetadata,
  ApplicationMetadataResult,
  InviteInfos,
  UserGroupSimpleInfos
}
import serializers.Keys
import services.{
  ApplicationService,
  BusinessDaysService,
  DataService,
  EventService,
  FileService,
  MandatService,
  NotificationService,
  OrganisationService,
  ServicesDependencies,
  UserGroupService,
  UserService
}
import views.applications.myApplications.MyApplicationInfos
import views.dashboard.DashboardInfos
import views.helpers.forms.flashSuccessRawHtmlKey

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */
@Singleton
case class ApplicationController @Inject() (
    applicationService: ApplicationService,
    businessDaysService: BusinessDaysService,
    config: AppConfig,
    val controllerComponents: ControllerComponents,
    dataService: DataService,
    dependencies: ServicesDependencies,
    eventService: EventService,
    fileService: FileService,
    loginAction: LoginAction,
    mandatService: MandatService,
    notificationsService: NotificationService,
    organisationService: OrganisationService,
    userGroupService: UserGroupService,
    userService: UserService,
    ws: WSClient,
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends BaseController
    with I18nSupport
    with Operators.Common
    with Operators.ApplicationOperators
    with Operators.UserOperators {

  import dependencies.ioRuntime

  private val success = "success"

  private def filterVisibleGroups(areaId: UUID, user: User, rights: Authorization.UserRights)(
      groups: List[UserGroup]
  ): List[UserGroup] =
    if (Authorization.isAdmin(rights) || user.areas.contains[UUID](areaId)) {
      groups
    } else {
      // This case is a weird political restriction:
      // Users are basically segmented between 2 overall types or `Organisation`
      // `Organisation.organismesAidants` & `Organisation.organismesOperateurs`
      val visibleOrganisations = groups
        .map(_.organisationId)
        .collect {
          case Some(organisationId)
              if Organisation.organismesAidants
                .map(_.id)
                .contains[Organisation.Id](organisationId) =>
            Organisation.organismesAidants.map(_.id)
          case Some(_) => Organisation.organismesOperateurs.map(_.id)
        }
        .flatten
        .toSet

      groups.filter(group =>
        group.organisationId match {
          case None     => false
          case Some(id) => visibleOrganisations.contains(id)
        }
      )
    }

  /** Groups with instructors available to the current user */

  private def fetchGroupsWithInstructors(
      areaId: UUID,
      currentUser: User,
      rights: Authorization.UserRights,
      currentUserGroups: List[UserGroup]
  ): Future[(List[UserGroup], List[User], List[User])] = {
    val hasFranceServicesAccess = currentUserGroups.exists(_.isInFranceServicesNetwork)
    val groupsOfAreaFuture =
      userGroupService.byArea(areaId, excludeFranceServicesNetwork = !hasFranceServicesAccess)
    groupsOfAreaFuture.map { groupsOfArea =>
      val visibleGroups = filterVisibleGroups(areaId, currentUser, rights)(groupsOfArea)
      val usersInThoseGroups = userService.byGroupIds(visibleGroups.map(_.id))
      // Note: we don't care about users who are in several areas
      val coworkers = usersInThoseGroups
        .filter(user => Authorization.canAddUserAsCoworkerToNewApplication(user)(rights))
        .filterNot(user => user.id === currentUser.id)
      // This could be optimized by doing only one SQL query
      val instructorsOfGroups = usersInThoseGroups.filter(_.instructor)
      val groupIdsWithInstructors = instructorsOfGroups.flatMap(_.groupIds).toSet
      val groupsOfAreaWithInstructor =
        visibleGroups.filter(user => groupIdsWithInstructors.contains(user.id))
      (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers)
    }
  }

  // Note: `defaultArea` is not stateful between pages,
  // because changing area is considered to be a special case.
  // This might change in the future depending on user feedback.
  private def defaultArea(user: User): Future[Area] =
    userGroupService
      .byIdsFuture(user.groupIds)
      .map(_.flatMap(_.areaIds).flatMap(Area.fromId).headOption.getOrElse(Area.ain))

  private def areaInQueryString(implicit request: RequestWithUserData[_]): Option[Area] =
    request
      .getQueryString(Keys.QueryParam.areaId)
      .flatMap(UUIDHelper.fromString)
      .flatMap(Area.fromId)

  private def currentArea(implicit request: RequestWithUserData[_]): Future[Area] =
    areaInQueryString
      .map(Future.successful)
      .getOrElse(defaultArea(request.currentUser))

  private def extractAreaOutOfFormOrThrow(form: Form[_], formName: String): Area =
    form.data
      .get(Keys.Application.areaId)
      .map(UUID.fromString)
      .flatMap(Area.fromId)
      .getOrElse(throw new Exception("No key 'areaId' in " + formName))

  def create: Action[AnyContent] =
    loginAction.async { implicit request =>
      eventService.log(
        EventType.ApplicationFormShowed,
        "Visualise le formulaire de création de demande"
      )
      currentArea.flatMap(currentArea =>
        userGroupService
          .byIdsFuture(request.currentUser.groupIds)
          .flatMap(userGroups =>
            fetchGroupsWithInstructors(
              currentArea.id,
              request.currentUser,
              request.rights,
              userGroups
            ).map { case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
              val categories = organisationService.categories
              Ok(
                views.html.createApplication(request.currentUser, request.rights, currentArea)(
                  userGroups,
                  instructorsOfGroups,
                  groupsOfAreaWithInstructor,
                  coworkers,
                  readSharedAccountUserSignature(request.session),
                  canCreatePhoneMandat = currentArea === Area.calvados,
                  categories,
                  ApplicationFormData.form(request.currentUser)
                )
              )
            }
          )
      )
    }

  def contextualizedUserName(
      user: User,
      currentAreaId: UUID,
      creatorGroupId: Option[UUID]
  ): String = {
    val userGroups = userGroupService.byIds(user.groupIds)
    Application.invitedUserContextualizedName(user, userGroups, currentAreaId.some, creatorGroupId)
  }

  private def handlingFiles(applicationId: UUID, answerId: Option[UUID])(
      onError: Error => Future[Result]
  )(
      onSuccess: List[FileMetadata] => Future[Result]
  )(implicit request: RequestWithUserData[AnyContent]): Future[Result] = {
    val tmpFiles: List[(Path, String)] =
      request.body.asMultipartFormData
        .map(_.files.filter(_.key.matches("file\\[\\d+\\]")))
        .getOrElse(Nil)
        .collect {
          case attachment if attachment.filename.nonEmpty =>
            attachment.ref.path -> attachment.filename
        }
        .toList
    val document = answerId match {
      case None           => FileMetadata.Attached.Application(applicationId)
      case Some(answerId) => FileMetadata.Attached.Answer(applicationId, answerId)
    }
    val newFilesF = fileService.saveFiles(tmpFiles.toList, document, request.currentUser)
    val pendingFilesF = answerId match {
      case None           => fileService.byApplicationId(applicationId)
      case Some(answerId) => fileService.byAnswerId(answerId)
    }
    (for {
      newFiles <- EitherT(newFilesF)
      pendingFiles <- EitherT(pendingFilesF)
      // Note that the 2 futures insert and select file_metadata in a very racy way
      // but we don't care about the actual status here, only filenames
      uniqueNewFiles = newFiles.filter(file => pendingFiles.forall(_.id =!= file.id))
      result <- EitherT(onSuccess(pendingFiles ::: uniqueNewFiles).map(_.asRight[Error]))
    } yield result).value.flatMap(_.fold(onError, Future.successful))
  }

  def createPost: Action[AnyContent] =
    loginAction.async { implicit request =>
      val form = ApplicationFormData.form(request.currentUser).bindFromRequest()
      val applicationId =
        ApplicationFormData.extractApplicationId(form).getOrElse(UUID.randomUUID())

      // Get `areaId` from the form, to avoid losing it in case of errors
      val currentArea: Area = extractAreaOutOfFormOrThrow(form, "Application creation form")

      handlingFiles(applicationId, none) { error =>
        eventService.logError(error)
        userGroupService
          .byIdsFuture(request.currentUser.groupIds)
          .flatMap(userGroups =>
            fetchGroupsWithInstructors(
              currentArea.id,
              request.currentUser,
              request.rights,
              userGroups
            ).map { case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
              val message =
                "Erreur lors de l'envoi de fichiers. Cette erreur est possiblement temporaire."
              BadRequest(
                views.html
                  .createApplication(request.currentUser, request.rights, currentArea)(
                    userGroups,
                    instructorsOfGroups,
                    groupsOfAreaWithInstructor,
                    coworkers,
                    None,
                    canCreatePhoneMandat = currentArea === Area.calvados,
                    organisationService.categories,
                    form,
                    Nil,
                  )
              )
                .flashing("application-error" -> message)
            }
          )
      } { files =>
        form.fold(
          formWithErrors =>
            // binding failure, you retrieve the form containing errors:
            userGroupService
              .byIdsFuture(request.currentUser.groupIds)
              .flatMap(userGroups =>
                fetchGroupsWithInstructors(
                  currentArea.id,
                  request.currentUser,
                  request.rights,
                  userGroups
                )
                  .map { case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
                    eventService.log(
                      EventType.ApplicationCreationInvalid,
                      s"L'utilisateur essaie de créer une demande invalide ${formErrorsLog(formWithErrors)}"
                    )
                    BadRequest(
                      views.html
                        .createApplication(request.currentUser, request.rights, currentArea)(
                          userGroups,
                          instructorsOfGroups,
                          groupsOfAreaWithInstructor,
                          coworkers,
                          None,
                          canCreatePhoneMandat = currentArea === Area.calvados,
                          organisationService.categories,
                          formWithErrors,
                          files,
                        )
                    )
                  }
              ),
          applicationData =>
            applicationData.creatorGroupId
              .fold(Future.successful[Option[UserGroup]](none))(groupId =>
                userGroupService.groupByIdFuture(groupId)
              )
              .map { creatorGroup =>
                // Note: we will deprecate .currentArea as a variable stored in the cookies
                val currentAreaId: UUID = currentArea.id
                val usersInGroups = userService.byGroupIds(applicationData.groups)
                val instructors: List[User] = usersInGroups.filter(_.instructor)
                val coworkers: List[User] =
                  applicationData.users.flatMap(id => userService.byId(id))
                val invitedUsers: Map[UUID, String] = (instructors ::: coworkers)
                  .map(user =>
                    user.id -> contextualizedUserName(user, currentAreaId, creatorGroup.map(_.id))
                  )
                  .toMap

                val description: String =
                  applicationData.signature
                    .fold(applicationData.description)(signature =>
                      applicationData.description + "\n\n" + signature
                    )
                val usagerInfos: Map[String, String] =
                  Map(
                    "Prénom" -> applicationData.usagerPrenom,
                    "Nom de famille" -> applicationData.usagerNom,
                    "Date de naissance" -> applicationData.usagerBirthDate
                  ) ++ applicationData.usagerOptionalInfos.collect {
                    case (infoName, infoValue)
                        if infoName.trim.nonEmpty && infoValue.trim.nonEmpty =>
                      infoName.trim -> infoValue.trim
                  }

                val isInFranceServicesNetwork = creatorGroup.exists(_.isInFranceServicesNetwork)
                val application = Application(
                  applicationId,
                  Time.nowParis(),
                  contextualizedUserName(
                    request.currentUser,
                    currentAreaId,
                    creatorGroup.map(_.id)
                  ),
                  request.currentUser.id,
                  creatorGroup.map(_.id),
                  creatorGroup.map(_.name),
                  applicationData.subject,
                  description,
                  usagerInfos,
                  invitedUsers,
                  currentArea.id,
                  irrelevant = false,
                  hasSelectedSubject =
                    applicationData.selectedSubject.contains[String](applicationData.subject),
                  category = applicationData.category,
                  mandatType = Application.MandatType.Paper.some,
                  mandatDate = Some(applicationData.mandatDate),
                  invitedGroupIdsAtCreation = applicationData.groups,
                  isInFranceServicesNetwork = isInFranceServicesNetwork,
                )
                if (applicationService.createApplication(application)) {
                  notificationsService.newApplication(application)
                  eventService.log(
                    EventType.ApplicationCreated,
                    s"La demande ${application.id} a été créée",
                    applicationId = application.id.some
                  )
                  application.invitedUsers.foreach { case (userId, _) =>
                    eventService.log(
                      EventType.ApplicationCreated,
                      s"Envoi de la nouvelle demande ${application.id} à l'utilisateur $userId",
                      applicationId = application.id.some,
                      involvesUser = userId.some
                    )
                  }
                  applicationData.linkedMandat.foreach { mandatId =>
                    if (
                      applicationData.mandatGenerationType === ApplicationFormData.mandatGenerationTypeIsNew
                    ) {
                      mandatService
                        .linkToApplication(Mandat.Id(mandatId), applicationId)
                        .onComplete {
                          case Failure(error) =>
                            eventService.log(
                              EventType.ApplicationLinkedToMandatError,
                              s"Erreur pour faire le lien entre le mandat $mandatId et la demande $applicationId",
                              applicationId = application.id.some,
                              underlyingException = error.some
                            )
                          case Success(Left(error)) =>
                            eventService.logError(error, applicationId = application.id.some)
                          case Success(Right(_)) =>
                            eventService.log(
                              EventType.ApplicationLinkedToMandat,
                              s"La demande ${application.id} a été liée au mandat $mandatId",
                              applicationId = application.id.some
                            )
                        }
                    }
                  }
                  Redirect(routes.ApplicationController.myApplications)
                    .withSession(
                      applicationData.signature.fold(
                        removeSharedAccountUserSignature(request.session)
                      )(signature => saveSharedAccountUserSignature(request.session, signature))
                    )
                    .flashing(
                      flashSuccessRawHtmlKey -> views.application
                        .applicationSentSuccessMessage(applicationId)
                        .toString
                    )
                } else {
                  eventService.log(
                    EventType.ApplicationCreationError,
                    s"La demande ${application.id} n'a pas pu être créée",
                    applicationId = application.id.some
                  )
                  InternalServerError(
                    "Erreur Interne: Votre demande n'a pas pu être envoyée. Merci de réessayer ou de contacter l'administrateur"
                  )
                }
              }
        )
      }
    }

  private def visibleApplicationsMetadata(
      user: User,
      rights: Authorization.UserRights,
      areaOption: Option[Area],
      numOfMonthsDisplayed: Int
  ): Future[List[Application]] =
    (
      Authorization.isAdmin(rights),
      Authorization.isAreaManager(rights),
      Authorization.isManager(rights),
      areaOption
    ) match {
      case (true, _, _, None) =>
        applicationService.allForAreas(user.areas, numOfMonthsDisplayed.some, false)
      case (true, _, _, Some(area)) =>
        applicationService.allForAreas(List(area.id), numOfMonthsDisplayed.some, false)
      case (_, true, _, None) =>
        applicationService.allForAreas(user.managingAreaIds, numOfMonthsDisplayed.some, false)
      case (_, true, _, Some(area)) if user.managingAreaIds.contains[UUID](area.id) =>
        applicationService.allForAreas(List(area.id), numOfMonthsDisplayed.some, false)
      case (_, _, true, None) =>
        val userIds = userService.byGroupIds(user.groupIds, includeDisabled = true).map(_.id)
        applicationService.allForUserIds(userIds, numOfMonthsDisplayed.some, false)
      case (_, _, true, Some(area)) =>
        val userIds = userService.byGroupIds(user.groupIds, includeDisabled = true).map(_.id)
        applicationService
          .allForUserIds(userIds, numOfMonthsDisplayed.some, false)
          .map(_.filter(application => application.area === area.id))
      case _ =>
        Future.successful(Nil)
    }

  private def extractApplicationsAdminQuery(implicit
      request: RequestWithUserData[_]
  ): (Option[Area], Int) = {
    val areaOpt = areaInQueryString.filterNot(_.id === Area.allArea.id)
    val numOfMonthsDisplayed: Int = request
      .getQueryString(Keys.QueryParam.numOfMonthsDisplayed)
      .flatMap(s => Try(s.toInt).toOption)
      .getOrElse(3)
    (areaOpt, numOfMonthsDisplayed)
  }

  def applicationsAdmin: Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.canSeeApplicationsMetadata)(
        EventType.AllApplicationsUnauthorized,
        "L'utilisateur n'a pas de droit d'afficher les métadonnées des demandes"
      ) { () =>
        val (areaOpt, numOfMonthsDisplayed) = extractApplicationsAdminQuery
        eventService.log(
          EventType.AllApplicationsShowed,
          s"Accède à la page des métadonnées des demandes [$areaOpt ; $numOfMonthsDisplayed]"
        )
        Future(
          Ok(
            views.applicationsAdmin
              .page(request.currentUser, request.rights, areaOpt, numOfMonthsDisplayed)
          )
        )
      }
    }

  def applicationsMetadata: Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.canSeeApplicationsMetadata)(
        EventType.AllApplicationsUnauthorized,
        "Liste des metadonnées des demandes non autorisée",
        errorResult = Forbidden(Json.toJson(ApplicationMetadataResult(Nil))).some
      ) { () =>
        val (areaOpt, numOfMonthsDisplayed) = extractApplicationsAdminQuery
        visibleApplicationsMetadata(
          request.currentUser,
          request.rights,
          areaOpt,
          numOfMonthsDisplayed
        ).map { applications =>
          eventService.log(
            EventType.AllApplicationsShowed,
            "Accède à la liste des metadata des demandes " +
              s"[territoire ${areaOpt.map(_.name).getOrElse("tous")} ; " +
              s"taille : ${applications.size}]"
          )
          val userIds: List[UUID] = (applications.flatMap(_.invitedUsers.keys) ++
            applications.map(_.creatorUserId)).toList.distinct
          val users = userService.byIds(userIds, includeDisabled = true)
          val groupIds =
            (users.flatMap(_.groupIds) ::: applications.flatMap(application =>
              application.invitedGroupIdsAtCreation ::: application.answers.flatMap(
                _.invitedGroupIds
              )
            )).distinct
          val groups = userGroupService.byIds(groupIds)
          val idToUser = users.map(user => (user.id, user)).toMap
          val idToGroup = groups.map(group => (group.id, group)).toMap
          val metadata = applications.map(application =>
            ApplicationMetadata.fromApplication(
              application,
              request.rights,
              idToUser,
              idToGroup
            )
          )
          Ok(Json.toJson(ApplicationMetadataResult(metadata)))
        }
      }
    }

  private def lastOperateurAnswer(application: Application): Option[Answer] =
    application.userAnswers
      .filter(_.creatorUserID =!= application.creatorUserId)
      .lastOption

  private def remainingHoursBeforeLate(application: Application): Option[Int] =
    if (
      application.closed ||
      application.status === Application.Status.Processed
    )
      None
    else
      (
        Some(lastOperateurAnswer(application) match {
          case None =>
            (3 * 24) - businessDaysService
              .businessHoursBetween(application.creationDate, ZonedDateTime.now())
          case Some(lastAnswer) =>
            (15 * 24) - businessDaysService.businessHoursBetween(
              lastAnswer.creationDate,
              ZonedDateTime.now()
            )
        })
      )

  private def applicationIsLate(application: Application): Boolean =
    remainingHoursBeforeLate(application) match {
      case None                 => false
      case Some(remainingHours) => remainingHours < 0
    }

  private def shouldServeDsfr(user: User) =
    config.groupsWithDsfr.intersect(user.groupIds.toSet).nonEmpty && (
      user.admin || (
        !user.groupAdmin &&
          user.observableOrganisationIds.isEmpty &&
          user.managingAreaIds.isEmpty &&
          user.managingOrganisationIds.isEmpty
      )
    )

  private def myApplicationsBoard(
      user: User,
      userRights: Authorization.UserRights,
      asAdmin: Boolean,
      urlBase: String,
  )(log: ApplicationsPageInfos => Unit)(implicit request: RequestHeader): Future[Result] =
    applicationBoardInfos(user, userRights, asAdmin, urlBase).map {
      case (infos, filteredByStatus, userGroups) =>
        log(infos)
        (
          if (shouldServeDsfr(user)) {
            val selectedApplication =
              request
                .getQueryString("demande-visible")
                .flatMap(UUIDHelper.fromString)
                .flatMap(id => filteredByStatus.find(_.application.id === id))
            // TODO redirect to this page after failed form
            val selectedApplicationFiles = Nil
            Ok(
              views.applications.myApplications
                .page(
                  user,
                  userRights,
                  filteredByStatus,
                  selectedApplication,
                  selectedApplicationFiles,
                  userGroups,
                  infos,
                  config
                )
            )
          } else
            Ok(
              views.applications.myApplicationsLegacy
                .page(user, userRights, filteredByStatus.map(_.application), userGroups, infos)
            )
        )
        .withHeaders(CACHE_CONTROL -> "no-store")
    }

  private def applicationBoardInfos(
      user: User,
      userRights: Authorization.UserRights,
      asAdmin: Boolean,
      urlBase: String
  )(implicit
      request: play.api.mvc.RequestHeader
  ): Future[(ApplicationsPageInfos, List[MyApplicationInfos], List[UserGroup])] =
    userGroupService.byIdsFuture(user.groupIds).flatMap { userGroups =>
      val selectedGroupsFilter = request.queryString
        .get(ApplicationsPageInfos.groupFilterKey)
        .map(_.flatMap(id => Try(UUID.fromString(id)).toOption).toSet)
      val statusFilter = request.getQueryString(ApplicationsPageInfos.statusFilterKey)
      val filters = ApplicationsPageInfos.Filters(
        selectedGroups = selectedGroupsFilter,
        status = statusFilter,
        urlBase = urlBase,
      )

      val allApplications = applicationService.allOpenOrRecentForUserId(
        user.id,
        asAdmin,
        Time.nowParis()
      )
      val (allClosedApplications, allOpenApplications) = allApplications.partition(_.closed)

      val allGroupsOpenCount = allOpenApplications.length
      val allGroupsClosedCount = allClosedApplications.length

      val openApplicationsByGroupCounts: Map[UUID, Int] =
        userGroups
          .map(group =>
            (
              group.id,
              allOpenApplications.count(application =>
                application.creatorGroupId
                  .map(id => id === group.id)
                  .getOrElse(false) || application.invitedGroups.contains(group.id)
              )
            )
          )
          .toMap

      val filteredByGroups = selectedGroupsFilter match {
        case None => allApplications
        case Some(filteringGroups) =>
          allApplications.filter { application =>
            application.creatorGroupId.map(filteringGroups.contains).getOrElse(false) ||
            filteringGroups.intersect(application.invitedGroups).nonEmpty
          }
      }

      val (closedFilteredByGroups, openFilteredByGroups) =
        filteredByGroups.partition { application =>
          if (user.instructor) {
            val isCreator = Authorization.isApplicationCreator(application)(userRights) ||
              Authorization.isInApplicationCreatorGroup(application)(userRights)
            val isProcessed = application.status === Application.Status.Processed && !isCreator
            application.closed || isProcessed
          } else {
            application.closed
          }
        }
      val filteredByGroupsOpenCount = openFilteredByGroups.length
      val filteredByGroupsClosedCount = closedFilteredByGroups.length

      val interactedApplications = openFilteredByGroups.filter { application =>
        application.creatorUserId === user.id ||
        application.userAnswers.exists(answer => answer.creatorUserID === user.id)
      }
      val interactedApplicationsCount = interactedApplications.length

      val applicationsByStatus =
        openFilteredByGroups.groupBy(application => application.longStatus(user))
      val newApplications: List[Application] =
        applicationsByStatus.get(Application.Status.New).getOrElse(Nil)
      val newApplicationsCount = newApplications.length
      val processingApplications: List[Application] =
        applicationsByStatus.get(Application.Status.Processing).getOrElse(Nil)
      val processingApplicationsCount = processingApplications.length

      val lateApplications = openFilteredByGroups.filter(applicationIsLate)
      val lateCount = lateApplications.length

      val filteredByStatus =
        if (filters.isMine)
          interactedApplications
        else if (filters.isNew)
          newApplications
        else if (filters.isProcessing)
          processingApplications
        else if (filters.isLate)
          lateApplications
        else if (filters.isArchived)
          closedFilteredByGroups
        else
          openFilteredByGroups

      val infos = ApplicationsPageInfos(
        filters = filters,
        groupsCounts = openApplicationsByGroupCounts,
        allGroupsOpenCount = allGroupsOpenCount,
        allGroupsClosedCount = allGroupsClosedCount,
        filteredByGroupsOpenCount = filteredByGroupsOpenCount,
        filteredByGroupsClosedCount = filteredByGroupsClosedCount,
        interactedCount = interactedApplicationsCount,
        newCount = newApplicationsCount,
        processingCount = processingApplicationsCount,
        lateCount = lateCount,
      )

      val allGroupsIds = filteredByStatus
        .flatMap(application =>
          application.creatorGroupId.toList ::: application.invitedGroups.toList
        )
        .distinct
      userGroupService.byIdsFuture(allGroupsIds).map { allGroups =>
        val groupsMap = allGroups.map(group => (group.id, group)).toMap
        val filteredApplications = filteredByStatus.map { application =>
          val creatorIsInFS = userGroups.exists(group =>
            group.organisationId
              .map(organisation => organisation === Organisation.franceServicesId)
              .getOrElse(false)
          )
          val creatorGroup = application.creatorGroupId.flatMap(groupsMap.get)
          val invitedGroups = application.invitedGroups.toList.flatMap(groupsMap.get)
          val shouldBeAnsweredInTheNext24h =
            remainingHoursBeforeLate(application).map(_ <= 24).getOrElse(false)
          MyApplicationInfos(
            application = application,
            creatorIsInFS = creatorIsInFS,
            creatorGroup = creatorGroup,
            invitedGroups = invitedGroups,
            lastOperateurAnswer = lastOperateurAnswer(application),
            shouldBeAnsweredInTheNext24h = shouldBeAnsweredInTheNext24h,
          )
        }

        (infos, filteredApplications, userGroups)
      }
    }

  private def dashboardInfos(user: User, adminMasquerade: Boolean): Future[DashboardInfos] =
    userGroupService.byIdsFuture(user.groupIds).map { userGroups =>
      val allApplications =
        applicationService.allOpenOrRecentForUserId(user.id, false, Time.nowParis())

      val groupInfos = userGroups
        .map { group =>
          val applications = allApplications.filter(application =>
            application.creatorGroupId.map(id => id === group.id).getOrElse(false) ||
              application.invitedGroups.contains(group.id)
          )

          DashboardInfos.Group(
            group,
            newCount = applications.count(_.status === Application.Status.New),
            lateCount = applications.count(applicationIsLate),
          )
        }

      val startDate = LocalDate.now().minusDays(30)
      val endDate = LocalDate.now()
      val chartFilters =
        if (user.admin)
          views.internalStats.Filters(
            startDate = startDate,
            endDate = endDate,
            areaIds = Nil,
            organisationIds = Nil,
            creatorGroupIds = Nil,
            invitedGroupIds = Nil,
          )
        else {
          val (creatorGroupIds, invitedGroupIds) = divideStatsGroups(userGroups)
          views.internalStats.Filters(
            startDate = startDate,
            endDate = endDate,
            areaIds = Nil,
            organisationIds = Nil,
            creatorGroupIds = creatorGroupIds,
            invitedGroupIds = invitedGroupIds
          )
        }

      val applicationsPageEmptyFilters = ApplicationsPageInfos.emptyFilters(
        if (adminMasquerade)
          controllers.routes.ApplicationController.allAs(user.id).url
        else
          controllers.routes.ApplicationController.myApplications.url
      )
      DashboardInfos(
        newCount = allApplications.count(_.status === Application.Status.New),
        lateCount = allApplications.count(applicationIsLate),
        groupInfos,
        chartFilters,
        applicationsPageEmptyFilters,
      )
    }

  def dashboard: Action[AnyContent] =
    loginAction.async { implicit request =>
      dashboardInfos(request.currentUser, adminMasquerade = false)
        .map(infos => Ok(views.dashboard.page(request.currentUser, request.rights, infos, config)))
    }

  def dashboardAs(otherUserId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withUser(otherUserId) { (otherUser: User) =>
        asUserWithAuthorization(Authorization.canSeeOtherUserNonPrivateViews(otherUser))(
          EventType.MasqueradeUnauthorized,
          s"Accès non autorisé pour voir le dashboard de $otherUserId",
          errorInvolvesUser = otherUser.id.some
        ) { () =>
          LoginAction.readUserRights(otherUser).flatMap { userRights =>
            dashboardInfos(otherUser, adminMasquerade = true)
              .map(infos => Ok(views.dashboard.page(otherUser, userRights, infos, config)))
          }
        }
      }
    }

  def myApplications: Action[AnyContent] =
    loginAction.async { implicit request =>
      myApplicationsBoard(
        request.currentUser,
        request.rights,
        request.currentUser.admin,
        controllers.routes.ApplicationController.myApplications.url
      ) { infos =>
        eventService.log(
          EventType.MyApplicationsShowed,
          s"Visualise la liste des demandes : ${infos.countsLog}"
        )
      }
    }

  val statsAction: BaseLoginAction = loginAction.withPublicPage(
    dataService
      .operateursDeploymentData(DataService.organisationSetFranceService, Area.allExcludingDemo)
      .map(deploymentData => Ok(views.publicStats.page(deploymentData)))
  )

  def stats: Action[AnyContent] =
    statsAction.async { implicit request =>
      statsPage(routes.ApplicationController.stats, request.currentUser, request.rights)
    }

  def statsAs(otherUserId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withUser(otherUserId) { (otherUser: User) =>
        asUserWithAuthorization(Authorization.canSeeOtherUserNonPrivateViews(otherUser))(
          EventType.MasqueradeUnauthorized,
          s"Accès non autorisé pour voir la page stats de $otherUserId",
          errorInvolvesUser = otherUser.id.some
        ) { () =>
          LoginAction.readUserRights(otherUser).flatMap { userRights =>
            statsPage(routes.ApplicationController.statsAs(otherUserId), otherUser, userRights)
          }
        }
      }
    }

  private def divideStatsGroups(groups: List[UserGroup]): (List[UUID], List[UUID]) = {
    val creatorGroupIds = groups
      .filter(group =>
        group.organisationId
          .map(id => Organisation.organismesAidants.map(_.id).contains[Organisation.Id](id))
          .getOrElse(false)
      )
      .map(_.id)
    val invitedGroupIds =
      groups.map(_.id).filterNot(id => creatorGroupIds.contains[UUID](id))

    (creatorGroupIds, invitedGroupIds)
  }

  private def statsPage(formUrl: Call, user: User, rights: Authorization.UserRights)(implicit
      request: RequestWithUserData[_]
  ): Future[Result] = {
    // TODO: remove `.get`
    val (areaIds, queryOrganisationIds, queryGroupIds, creationMinDate, creationMaxDate) =
      StatsFormData.form.bindFromRequest().value.get

    val organisationIds =
      if (Authorization.isAdmin(rights))
        queryOrganisationIds
      else if (Authorization.isAreaManager(rights))
        queryOrganisationIds
          .filter(id => user.managingOrganisationIds.contains[Organisation.Id](id))
      else
        queryOrganisationIds.filter(id => Authorization.canObserveOrganisation(id)(rights))

    // Note: admins can request stats on groups, but they are excluded
    // from filters for performance reasons
    // Note 2: having both organisations and groups does not work for now
    val groupsThatCanBeFilteredByFuture: Future[List[UserGroup]] =
      if (Authorization.isAdmin(rights)) {
        val groupIds = (queryGroupIds ::: user.groupIds).distinct
        userGroupService.byIdsFuture(groupIds)
      } else if (Authorization.isAreaManager(rights)) {
        userGroupService.allForAreaManager(user)
      } else {
        userGroupService.byIdsFuture(user.groupIds)
      }

    groupsThatCanBeFilteredByFuture.flatMap { groupsThatCanBeFilteredBy =>
      val selectedGroupIds =
        if (organisationIds.nonEmpty) Nil
        else
          queryGroupIds.intersect(groupsThatCanBeFilteredBy.map(_.id))

      val areasThatCanBeFilteredBy =
        if (Authorization.isAdmin(rights))
          Area.all
        else
          Area.allExcludingDemo

      val (canFilterByOrganisation, organisationsThatCanBeFilteredBy) =
        if (Authorization.isAdmin(rights))
          (true, Organisation.all)
        else if (Authorization.isObserver(rights))
          (true, user.observableOrganisationIds.flatMap(Organisation.byId))
        else if (Authorization.isAreaManager(rights))
          (true, user.managingOrganisationIds.flatMap(Organisation.byId))
        else
          (false, Nil)

      val form = views.internalStats.SelectionForm(
        canFilterByOrganisation = canFilterByOrganisation,
        areasThatCanBeFilteredBy = areasThatCanBeFilteredBy,
        organisationsThatCanBeFilteredBy = organisationsThatCanBeFilteredBy,
        groupsThatCanBeFilteredBy = groupsThatCanBeFilteredBy,
        creationMinDate = creationMinDate,
        creationMaxDate = creationMaxDate,
        selectedAreaIds = areaIds,
        selectedOrganisationIds = organisationIds,
        selectedGroupIds = selectedGroupIds
      )

      val charts: Future[Html] = {
        val validQueryGroups =
          groupsThatCanBeFilteredBy.filter(group => selectedGroupIds.contains[UUID](group.id))
        val (creatorGroupIds, invitedGroupIds) = divideStatsGroups(validQueryGroups)

        Future.successful(
          views.internalStats.charts(
            views.internalStats.Filters(
              startDate = creationMinDate,
              endDate = creationMaxDate,
              areaIds,
              organisationIds,
              creatorGroupIds,
              invitedGroupIds,
            ),
            config
          )
        )
      }

      charts
        .map { html =>
          eventService.log(
            EventType.StatsShowed,
            "Visualise les stats [Territoires '" + areaIds.mkString(",") +
              "' ; Organismes '" + queryOrganisationIds.mkString(",") +
              "' ; Groupes '" + queryGroupIds.mkString(",") +
              "' ; Date début '" + creationMinDate +
              "' ; Date fin '" + creationMaxDate + "']"
          )
          Ok(views.html.stats.page(user, rights)(formUrl, html, form))
        }
    }
  }

  def allAs(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withUser(userId, includeDisabled = request.currentUser.admin) { (otherUser: User) =>
        asUserWithAuthorization(Authorization.canSeeOtherUserNonPrivateViews(otherUser))(
          EventType.AllAsUnauthorized,
          s"Accès non autorisé pour voir la liste des demandes de $userId",
          errorInvolvesUser = Some(otherUser.id)
        ) { () =>
          LoginAction.readUserRights(otherUser).flatMap { userRights =>
            myApplicationsBoard(
              otherUser,
              userRights,
              request.currentUser.admin,
              controllers.routes.ApplicationController.allAs(userId).url
            ) { infos =>
              eventService
                .log(
                  EventType.AllAsShowed,
                  s"Visualise la vue de l'utilisateur $userId : ${infos.countsLog}",
                  involvesUser = Some(otherUser.id)
                )
            }
          }
        }
      }
    }

  def showExportMyApplicationsCSV: Action[AnyContent] =
    loginAction { implicit request =>
      Ok(views.html.CSVExport(request.currentUser, request.rights))
    }

  private def applicationsToCSV(applications: List[Application]): String = {
    val usersId = applications.flatMap(_.invitedUsers.keys) ++ applications.map(_.creatorUserId)
    val users = userService.byIds(usersId, includeDisabled = true)
    val userGroupIds = users.flatMap(_.groupIds)
    val groups = userGroupService.byIds(userGroupIds)

    def applicationToCSV(application: Application): String = {
      val creatorUser = users.find(_.id === application.creatorUserId)
      val invitedUsers =
        users.filter(user => application.invitedUsers.keys.toList.contains[UUID](user.id))
      val creatorUserGroupNames = creatorUser.toList
        .flatMap(_.groupIds)
        .flatMap(groupId => groups.filter(_.id === groupId))
        .map(_.name)
        .mkString(",")
      val invitedUserGroupNames = invitedUsers
        .flatMap(_.groupIds)
        .distinct
        .flatMap(groupId => groups.filter(_.id === groupId))
        .map(_.name)
        .mkString(",")

      List[String](
        application.id.toString,
        application.status.show,
        // Precision limited for stats
        Time.formatPatternFr(application.creationDate, "YYY-MM-dd"),
        creatorUserGroupNames,
        invitedUserGroupNames,
        Area.all.find(_.id === application.area).map(_.name).head,
        application.closedDate.map(date => Time.formatPatternFr(date, "YYY-MM-dd")).getOrElse(""),
        if (not(application.irrelevant)) "Oui" else "Non",
        application.usefulness.getOrElse("?"),
        application.firstAnswerTimeInMinutes.map(_.toString).getOrElse(""),
        application.resolutionTimeInMinutes.map(_.toString).getOrElse("")
      ).map(escape)
        .mkString(";")
    }

    val headers = List(
      "Id",
      "Etat",
      "Date de création",
      "Groupes du demandeur",
      "Groupes des invités",
      "Territoire",
      "Date de clôture",
      "Pertinente",
      "Utile",
      "Délais de première réponse (Minutes)",
      "Délais de clôture (Minutes)"
    ).mkString(";")

    (List(headers) ++ applications.map(applicationToCSV)).mkString("\n")
  }

  def myCSV: Action[AnyContent] =
    loginAction { implicit request =>
      val currentDate = Time.nowParis()
      val exportedApplications = applicationService
        .allOpenOrRecentForUserId(request.currentUser.id, request.currentUser.admin, currentDate)

      val date = Time.formatPatternFr(currentDate, "YYY-MM-dd-HH'h'mm")
      val csvContent = applicationsToCSV(exportedApplications)

      eventService.log(EventType.MyCSVShowed, s"Visualise le CSV de mes demandes")
      Ok(csvContent)
        .withHeaders(
          CONTENT_DISPOSITION -> s"""attachment; filename="aplus-demandes-$date.csv"""",
          CACHE_CONTROL -> "no-store"
        )
        .as("text/csv")
    }

  private def usersWhoCanBeInvitedOn(application: Application, currentAreaId: UUID)(implicit
      request: RequestWithUserData[_]
  ): Future[List[User]] = {
    val excludeFranceServicesNetwork = !application.isInFranceServicesNetwork
    if (request.currentUser.expert || request.currentUser.admin) {
      val creator = userService.byId(application.creatorUserId, includeDisabled = true)
      val creatorGroups: Set[UUID] = creator.toList.flatMap(_.groupIds).toSet
      userGroupService
        .byArea(currentAreaId, excludeFranceServicesNetwork = excludeFranceServicesNetwork)
        .map { groupsOfArea =>
          userService
            .byGroupIds(groupsOfArea.map(_.id))
            .filter(user =>
              user.instructor || user.groupIds.toSet.intersect(creatorGroups).nonEmpty
            )
        }
    } else {
      // 1. coworkers
      val coworkers = Future(userService.byGroupIds(request.currentUser.groupIds))
      // 2. coworkers of instructors that are already on the application
      //    these will mostly be the ones that have been added as users after
      //    the application has been sent.
      val instructorsCoworkers = {
        val invitedUsers: List[User] =
          userService.byIds(application.invitedUsers.keys.toList, includeDisabled = true)
        val groupsOfInvitedUsers: Set[UUID] = invitedUsers.flatMap(_.groupIds).toSet
        userGroupService
          .byArea(application.area, excludeFranceServicesNetwork = excludeFranceServicesNetwork)
          .map { groupsOfArea =>
            val invitedGroups: Set[UUID] =
              groupsOfInvitedUsers.intersect(groupsOfArea.map(_.id).toSet)
            userService.byGroupIds(invitedGroups.toList).filter(_.instructor)
          }
      }
      coworkers.combine(instructorsCoworkers)
    }
  }
    .map(
      _.filterNot(user =>
        user.id === request.currentUser.id || application.invitedUsers.contains(user.id)
      )
    )

  /** Theses are all groups in an area which are not present in the discussion. */
  private def groupsWhichCanBeInvited(
      forAreaId: UUID,
      application: Application
  ): Future[List[UserGroup]] = {
    val invitedUsers: List[User] =
      userService.byIds(application.invitedUsers.keys.toList, includeDisabled = true)
    // Groups already present on the Application
    val groupsOfInvitedUsers: Set[UUID] = invitedUsers.flatMap(_.groupIds).toSet
    val excludeFranceServicesNetwork = !application.isInFranceServicesNetwork
    userGroupService
      .byArea(forAreaId, excludeFranceServicesNetwork = excludeFranceServicesNetwork)
      .map { groupsOfArea =>
        val groupsThatAreNotInvited =
          groupsOfArea.filterNot(group => groupsOfInvitedUsers.contains(group.id))
        val groupIdsWithInstructors: Set[UUID] =
          userService
            .byGroupIds(groupsThatAreNotInvited.map(_.id))
            .filter(_.instructor)
            .flatMap(_.groupIds)
            .toSet
        val groupsThatAreNotInvitedWithInstructor =
          groupsThatAreNotInvited.filter(user => groupIdsWithInstructors.contains(user.id))
        groupsThatAreNotInvitedWithInstructor.sortBy(_.name)
      }
  }

  def applicationInvitableGroups(applicationId: UUID, areaId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application =>
        groupsWhichCanBeInvited(areaId, application).map { invitableGroups =>
          val infos = InviteInfos(
            applicationId = applicationId,
            areaId = areaId,
            groups = invitableGroups.map(UserGroupSimpleInfos.fromUserGroup)
          )
          Ok(Json.toJson(infos))
        }
      }
    }

  private def showApplication(
      application: Application,
      form: Form[AnswerFormData],
      openedTab: String,
  )(toResult: Html => Result)(implicit request: RequestWithUserData[_]): Future[Result] = {
    val selectedArea: Area =
      areaInQueryString
        .getOrElse(Area.fromId(application.area).getOrElse(Area.all.head))
    val selectedAreaId = selectedArea.id
    val applicationUsers: List[UUID] =
      application.creatorUserId ::
        application.invitedUsers.map { case (id, _) => id }.toList :::
        application.answers.map(_.creatorUserID)
    usersWhoCanBeInvitedOn(application, selectedAreaId).flatMap { usersWhoCanBeInvited =>
      groupsWhichCanBeInvited(selectedAreaId, application).flatMap { invitableGroups =>
        userGroupService.byIdsFuture(request.currentUser.groupIds).flatMap { userGroups =>
          val filesF = EitherT(fileService.byApplicationId(application.id))
          val organisationsF = EitherT(userService.usersOrganisations(applicationUsers))
          (for {
            files <- filesF
            organisations <- organisationsF
          } yield (files, organisations)).value
            .map(
              _.fold(
                error => {
                  eventService.logError(error)
                  InternalServerError(Constants.genericError500Message)
                },
                { case (files, organisations) =>
                  val groups = userGroupService
                    .byIds(usersWhoCanBeInvited.flatMap(_.groupIds))
                    .filter(_.areaIds.contains[UUID](selectedAreaId))
                  val groupsWithUsersThatCanBeInvited = groups.map { group =>
                    group -> usersWhoCanBeInvited.filter(_.groupIds.contains[UUID](group.id))
                  }
                  toResult(
                    views.html.showApplication(request.currentUser, request.rights)(
                      userGroups,
                      groupsWithUsersThatCanBeInvited,
                      invitableGroups,
                      application,
                      form,
                      openedTab,
                      selectedArea,
                      readSharedAccountUserSignature(request.session),
                      files,
                      organisations
                    )
                  ).withHeaders(CACHE_CONTROL -> "no-store")
                }
              )
            )
        }
      }
    }
  }

  def show(id: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(id) { application =>
        val openedTab = request.flash
          .get("opened-tab")
          .orElse(
            request
              .getQueryString("onglet")
              .map(value => if (value === "invitation") "invite" else "answer")
          )
          .getOrElse("answer")
        showApplication(
          application,
          AnswerFormData.form(request.currentUser, false),
          openedTab = openedTab,
        ) { html =>
          eventService.log(
            EventType.ApplicationShowed,
            s"Demande $id consultée",
            applicationId = application.id.some
          )
          Ok(html)
        }
      }
    }

  def file(fileId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      EitherT(fileService.fileMetadata(fileId))
        .flatMap(metadataOpt =>
          EitherT.right[Error](
            metadataOpt match {
              case None =>
                IO.blocking(
                  eventService.log(EventType.FileNotFound, s"Le fichier $fileId n'existe pas")
                ).as(NotFound("Nous n'avons pas trouvé ce fichier"))
              case Some(metadata) =>
                val applicationId = metadata.attached match {
                  case FileMetadata.Attached.Application(id)          => id
                  case FileMetadata.Attached.Answer(applicationId, _) => applicationId
                }
                withApplicationIO(applicationId) { (application: Application) =>
                  val isAuthorized =
                    Authorization
                      .fileCanBeShown(config.filesExpirationInDays)(
                        metadata.attached,
                        application
                      )(request.rights)
                  if (isAuthorized) {
                    metadata.status match {
                      case FileMetadata.Status.Scanning =>
                        IO.blocking(
                          eventService.log(
                            EventType.FileNotFound,
                            s"Le fichier ${metadata.id} du document ${metadata.attached} est en cours de scan",
                            applicationId = applicationId.some
                          )
                        ).as(
                          NotFound(
                            "Le fichier est en cours de scan par un antivirus. Il devrait être disponible d'ici peu."
                          )
                        )
                      case FileMetadata.Status.Quarantined =>
                        IO.blocking(
                          eventService.log(
                            EventType.FileQuarantined,
                            s"Le fichier ${metadata.id} du document ${metadata.attached} est en quarantaine",
                            applicationId = applicationId.some
                          )
                        ).as(
                          NotFound(
                            "L'antivirus a mis en quarantaine le fichier. Si vous avez envoyé ce fichier, il est conseillé de vérifier votre ordinateur avec un antivirus. Si vous pensez qu'il s'agit d'un faux positif, nous vous invitons à changer le format, puis envoyer à nouveau sous un nouveau format."
                          )
                        )
                      case FileMetadata.Status.Available =>
                        IO.blocking(
                          eventService.log(
                            EventType.FileOpened,
                            s"Le fichier ${metadata.id} du document ${metadata.attached} a été ouvert",
                            applicationId = applicationId.some
                          )
                        ) >>
                          sendFile(metadata)
                      case FileMetadata.Status.Expired =>
                        IO.blocking(
                          eventService.log(
                            EventType.FileNotFound,
                            s"Le fichier ${metadata.id} du document ${metadata.attached} est expiré",
                            applicationId = applicationId.some
                          )
                        ).as(NotFound("Ce fichier à expiré."))
                      case FileMetadata.Status.Error =>
                        IO.blocking(
                          eventService.log(
                            EventType.FileNotFound,
                            s"Le fichier ${metadata.id} du document ${metadata.attached} a une erreur",
                            applicationId = applicationId.some
                          )
                        ).as(
                          NotFound(
                            "Une erreur est survenue lors de l'enregistrement du fichier. Celui-ci n'est pas disponible."
                          )
                        )
                    }
                  } else {
                    IO.blocking(
                      eventService.log(
                        EventType.FileUnauthorized,
                        s"L'accès aux fichiers sur la demande $applicationId n'est pas autorisé (fichier $fileId)",
                        applicationId = application.id.some
                      )
                    ).as(
                      Unauthorized(
                        s"Vous n'avez pas les droits suffisants pour voir les fichiers sur cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
                      )
                    )

                  }
                }
            }
          )
        )
        .valueOrF(error =>
          IO.blocking(eventService.logError(error))
            .as(
              InternalServerError(
                "Une erreur est survenue pour trouver le fichier. " +
                  "Cette erreur est probablement temporaire."
              )
            )
        )
        .unsafeToFuture()
    }

  private def sendFile(
      metadata: FileMetadata
  )(implicit request: RequestWithUserData[_]): IO[Result] = {
    val fileResult = (fileExists: Boolean) =>
      IO(
        if (fileExists)
          fileService
            .fileStream(metadata)
            .map(contentSource =>
              Ok.streamed(
                content = contentSource,
                contentLength = Some(metadata.filesize.toLong),
                // Will set "Content-Disposition: attachment"
                // This avoids potential security issues if a malicious HTML page is uploaded
                `inline` = false,
                fileName = Some(metadata.filename)
              ).withHeaders(CACHE_CONTROL -> "no-store")
            )
        else
          NotFound("Nous n'avons pas trouvé ce fichier").asRight
      )

    (
      for {
        fileExists <- EitherT(fileService.fileExistsOnS3(metadata.id))
        result <- EitherT(fileResult(fileExists))
      } yield result
    ).value.flatMap(
      _.fold(
        error =>
          IO.blocking(eventService.logError(error))
            .as(
              InternalServerError(
                "Une erreur est survenue pour trouver le fichier. " +
                  "Cette erreur est probablement temporaire."
              )
            ),
        IO.pure,
      )
    )
  }

  private def buildAnswerMessage(message: String, signature: Option[String]) =
    signature.map(s => message + "\n\n" + s).getOrElse(message)

  private val defaultApplicationProcessedMessage = "J’ai traité la demande."
  private val WorkInProgressMessage = "Je m’en occupe."
  private val WrongInstructorMessage = "Je ne suis pas le bon interlocuteur."

  def answerApplicationHasBeenProcessed(applicationId: UUID): Action[AnyContent] =
    answerAction(applicationId, applicationHasBeenProcessedForm = true)

  def answer(applicationId: UUID): Action[AnyContent] =
    answerAction(applicationId, applicationHasBeenProcessedForm = false)

  private def answerAction(
      applicationId: UUID,
      applicationHasBeenProcessedForm: Boolean
  ): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application =>
        val answerId = AnswerFormData
          .extractAnswerId(AnswerFormData.form(request.currentUser, false).bindFromRequest())
          .getOrElse(UUID.randomUUID())
        handlingFiles(applicationId, answerId.some) { error =>
          eventService.logError(error)
          val message =
            "Erreur lors de l'envoi de fichiers. Cette erreur est possiblement temporaire. " +
              "Votre réponse n'a pas pu être enregistrée."
          Future.successful(
            Redirect(
              routes.ApplicationController.show(applicationId).withFragment("answer-error")
            )
              .flashing("answer-error" -> message, "opened-tab" -> "answer")
          )
        } { files =>
          val form =
            if (applicationHasBeenProcessedForm)
              AnswerFormData
                .applicationHasBeenProcessedForm(request.currentUser, files.nonEmpty)
                .bindFromRequest()
            else
              AnswerFormData.form(request.currentUser, files.nonEmpty).bindFromRequest()
          form.fold(
            formWithErrors => {
              showApplication(
                application,
                formWithErrors,
                openedTab = "answer",
              ) { html =>
                val error =
                  s"Erreur dans le formulaire de réponse (${formErrorsLog(formWithErrors)})"
                eventService.log(
                  EventType.AnswerFormError,
                  error,
                  applicationId = application.id.some
                )
                BadRequest(html)
              }
            },
            answerData => {
              val answerType =
                if (answerData.applicationHasBeenProcessed)
                  AnswerType.ApplicationProcessed
                else
                  AnswerType.fromString(answerData.answerType)
              val currentAreaId = application.area

              val message = (answerType, answerData.message) match {
                case (AnswerType.Custom, Some(message)) =>
                  buildAnswerMessage(message, answerData.signature)
                case (AnswerType.ApplicationProcessed, message) =>
                  buildAnswerMessage(
                    message.getOrElse(defaultApplicationProcessedMessage),
                    answerData.signature
                  )
                case (AnswerType.WorkInProgress, _) =>
                  buildAnswerMessage(WorkInProgressMessage, answerData.signature)
                case (AnswerType.WrongInstructor, _) =>
                  buildAnswerMessage(WrongInstructorMessage, answerData.signature)
                case (AnswerType.Custom, None) => buildAnswerMessage("", answerData.signature)
                // None of the other cases are reachable here
                // It might be good to express it as a different type
                case (AnswerType.InviteByUser, _)   => buildAnswerMessage("", answerData.signature)
                case (AnswerType.InviteAsExpert, _) => buildAnswerMessage("", answerData.signature)
                case (AnswerType.InviteThroughGroupPermission, _) =>
                  buildAnswerMessage("", answerData.signature)
              }

              val answer = Answer(
                answerId,
                applicationId,
                Time.nowParis(),
                answerType,
                message,
                request.currentUser.id,
                contextualizedUserName(
                  request.currentUser,
                  currentAreaId,
                  application.creatorGroupId
                ),
                Map.empty[UUID, String],
                not(answerData.privateToHelpers),
                answerData.applicationIsDeclaredIrrelevant,
                answerData.usagerOptionalInfos.collect {
                  case (NonEmptyTrimmedString(infoName), NonEmptyTrimmedString(infoValue)) =>
                    (infoName, infoValue)
                }.some,
                invitedGroupIds = List.empty[UUID]
              )
              // If the new answer creator is the application creator, we force the application reopening
              val shouldBeOpened = answer.creatorUserID === application.creatorUserId
              val answerAdded =
                applicationService.addAnswer(applicationId, answer, false, shouldBeOpened)

              if (answerAdded === 1) {
                eventService.log(
                  EventType.AnswerCreated,
                  s"La réponse ${answer.id} a été créée sur la demande $applicationId",
                  applicationId = application.id.some
                )
                notificationsService.newAnswer(application, answer)
                Future(
                  Redirect(
                    s"${routes.ApplicationController.show(applicationId)}#answer-${answer.id}"
                  )
                    .withSession(
                      answerData.signature.fold(removeSharedAccountUserSignature(request.session))(
                        signature => saveSharedAccountUserSignature(request.session, signature)
                      )
                    )
                    .flashing(success -> "Votre réponse a bien été envoyée")
                )
              } else {
                eventService.log(
                  EventType.AnswerCreationError,
                  s"La réponse ${answer.id} n'a pas été créée sur la demande $applicationId : problème BDD",
                  applicationId = application.id.some
                )
                Future(InternalServerError("Votre réponse n'a pas pu être envoyée"))
              }
            }
          )
        }
      }
    }

  def invite(applicationId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application =>
        val form = InvitationFormData.form.bindFromRequest()
        // Get `areaId` from the form, to avoid losing it in case of errors
        val currentArea: Area = extractAreaOutOfFormOrThrow(form, "Invite User form")
        form.fold(
          formWithErrors => {
            val message =
              s"Erreur dans le formulaire d’invitation (${formWithErrors.errors.map(_.format).mkString(", ")})."
            val error =
              s"Erreur dans le formulaire d’invitation (${formErrorsLog(formWithErrors)})"
            eventService.log(
              EventType.InviteFormValidationError,
              error,
              applicationId = application.id.some
            )
            Future(
              Redirect(
                routes.ApplicationController.show(applicationId).withFragment("answer-error")
              )
                .flashing("answer-error" -> message, "opened-tab" -> "invite")
            )
          },
          inviteData =>
            if (inviteData.invitedUsers.isEmpty && inviteData.invitedGroups.isEmpty) {
              val error =
                s"Erreur dans le formulaire d’invitation (une personne ou un organisme doit être sélectionné)"
              eventService.log(
                EventType.InviteFormValidationError,
                error,
                applicationId = application.id.some
              )
              Future(
                Redirect(
                  routes.ApplicationController.show(applicationId).withFragment("answer-error")
                )
                  .flashing("answer-error" -> error, "opened-tab" -> "invite")
              )
            } else {
              usersWhoCanBeInvitedOn(application, currentArea.id).flatMap {
                singleUsersWhoCanBeInvited =>
                  groupsWhichCanBeInvited(currentArea.id, application).map { invitableGroups =>
                    val usersWhoCanBeInvited: List[User] =
                      singleUsersWhoCanBeInvited ::: userService
                        .byGroupIds(invitableGroups.map(_.id))
                    // When a group is checked, to avoid inviting everybody in a group,
                    // we filter their users, keeping only instructors
                    val invitedUsersFromGroups: List[User] = usersWhoCanBeInvited
                      .filter(user =>
                        user.instructor &&
                          inviteData.invitedGroups.toSet.intersect(user.groupIds.toSet).nonEmpty
                      )
                    val directlyInvitedUsers: List[User] = usersWhoCanBeInvited
                      .filter(user => inviteData.invitedUsers.contains[UUID](user.id))
                    val invitedUsers: Map[UUID, String] =
                      (invitedUsersFromGroups ::: directlyInvitedUsers)
                        .map(user =>
                          (
                            user.id,
                            contextualizedUserName(user, currentArea.id, application.creatorGroupId)
                          )
                        )
                        .toMap

                    val answer = Answer(
                      UUID.randomUUID(),
                      applicationId,
                      Time.nowParis(),
                      AnswerType.InviteByUser,
                      inviteData.message,
                      request.currentUser.id,
                      contextualizedUserName(
                        request.currentUser,
                        currentArea.id,
                        application.creatorGroupId
                      ),
                      invitedUsers,
                      not(inviteData.privateToHelpers),
                      declareApplicationHasIrrelevant = false,
                      Map.empty[String, String].some,
                      invitedGroupIds = inviteData.invitedGroups
                    )

                    if (applicationService.addAnswer(applicationId, answer) === 1) {
                      notificationsService.newAnswer(application, answer)
                      eventService.log(
                        EventType.AgentsAdded,
                        s"L'ajout d'utilisateur (réponse ${answer.id}) a été créé sur la demande $applicationId",
                        applicationId = application.id.some
                      )
                      answer.invitedUsers.foreach { case (userId, _) =>
                        eventService.log(
                          EventType.AgentsAdded,
                          s"Utilisateur $userId invité sur la demande $applicationId (réponse ${answer.id})",
                          applicationId = application.id.some,
                          involvesUser = userId.some
                        )
                      }
                      Redirect(routes.ApplicationController.myApplications)
                        .flashing(success -> "Les utilisateurs ont été invités sur la demande")
                    } else {
                      eventService.log(
                        EventType.AgentsNotAdded,
                        s"L'ajout d'utilisateur ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD",
                        applicationId = application.id.some
                      )
                      InternalServerError("Les utilisateurs n'ont pas pu être invités")
                    }
                  }
              }
            }
        )
      }
    }

  def inviteExpert(applicationId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { (application: Application) =>
        val currentAreaId = application.area
        if (application.canHaveExpertsInvitedBy(request.currentUser)) {
          userService.allExperts.map { expertUsers =>
            val experts: Map[UUID, String] = expertUsers
              .map(user =>
                user.id -> contextualizedUserName(user, currentAreaId, application.creatorGroupId)
              )
              .toMap
            val answer = Answer(
              UUID.randomUUID(),
              applicationId,
              Time.nowParis(),
              AnswerType.InviteAsExpert,
              "J'ajoute un expert",
              request.currentUser.id,
              contextualizedUserName(
                request.currentUser,
                currentAreaId,
                application.creatorGroupId
              ),
              experts,
              visibleByHelpers = true,
              declareApplicationHasIrrelevant = false,
              Map.empty[String, String].some,
              invitedGroupIds = List.empty[UUID]
            )
            if (applicationService.addAnswer(applicationId, answer, expertInvited = true) === 1) {
              notificationsService.newAnswer(application, answer)
              eventService.log(
                EventType.AddExpertCreated,
                s"La réponse ${answer.id} a été créée sur la demande $applicationId",
                applicationId = application.id.some
              )
              answer.invitedUsers.foreach { case (userId, _) =>
                eventService.log(
                  EventType.AddExpertCreated,
                  s"Expert $userId invité sur la demande $applicationId (réponse ${answer.id})",
                  applicationId = application.id.some,
                  involvesUser = userId.some
                )
              }
              Redirect(routes.ApplicationController.myApplications)
                .flashing(success -> "Un expert a été invité sur la demande")
            } else {
              eventService.log(
                EventType.AddExpertNotCreated,
                s"L'invitation d'experts ${answer.id} n'a pas été créée sur la demande $applicationId : problème BDD",
                applicationId = application.id.some
              )
              InternalServerError("L'expert n'a pas pu être invité")
            }
          }
        } else {
          eventService.log(
            EventType.AddExpertUnauthorized,
            s"L'invitation d'experts pour la demande $applicationId n'est pas autorisée",
            applicationId = application.id.some
          )
          Future(
            Unauthorized(
              s"Vous n'avez pas les droits suffisants pour inviter des agents à cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
          )
        }
      }
    }

  // TODO : should be better to handle errors with better types (eg Either) than Boolean
  def reopen(applicationId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { (application: Application) =>
        Future.successful(Authorization.canOpenApplication(application)(request.rights)).flatMap {
          case true =>
            applicationService
              .reopen(applicationId)
              .filter(identity)
              .map { _ =>
                val message = "La demande a bien été réouverte"
                eventService
                  .log(EventType.ReopenCompleted, message, applicationId = application.id.some)
                Redirect(routes.ApplicationController.myApplications).flashing(success -> message)
              }
              .recover { _ =>
                val message = "La demande n'a pas pu être réouverte"
                eventService
                  .log(EventType.ReopenError, message, applicationId = application.id.some)
                InternalServerError(message)
              }
          case false =>
            val message = s"Non autorisé à réouvrir la demande $applicationId"
            eventService
              .log(EventType.ReopenUnauthorized, message, applicationId = application.id.some)
            Future.successful(Unauthorized(message))
        }
      }
    }

  def terminate(applicationId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { (application: Application) =>
        val form = CloseApplicationFormData.form.bindFromRequest()
        form.fold(
          formWithErrors => {
            eventService
              .log(
                EventType.TerminateIncompleted,
                s"La demande de clôture pour $applicationId est incomplète",
                applicationId = application.id.some
              )
            Future(
              InternalServerError(
                s"L'utilité de la demande n'est pas présente, il s'agit sûrement d'une erreur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
              )
            )
          },
          usefulness => {
            val finalUsefulness =
              usefulness.some.filter(_ =>
                Authorization.isApplicationCreator(application)(request.rights) ||
                  Authorization.isInApplicationCreatorGroup(application)(request.rights)
              )
            if (Authorization.canCloseApplication(application)(request.rights)) {
              if (
                applicationService
                  .close(applicationId, finalUsefulness, Time.nowParis())
              ) {
                eventService
                  .log(
                    EventType.TerminateCompleted,
                    s"La demande $applicationId est archivée",
                    applicationId = application.id.some
                  )
                val successMessage =
                  s"""La demande « ${application.subject} » a bien été archivée. """ +
                    "Bravo et merci pour la résolution de cette demande ! " +
                    "Cette demande sera encore consultable un mois à partir de maintenant dans la colonne « Archivées »"
                Future(
                  Redirect(routes.ApplicationController.myApplications)
                    .flashing(success -> successMessage)
                )
              } else {
                eventService.log(
                  EventType.TerminateError,
                  s"La demande $applicationId n'a pas pu être archivée en BDD",
                  applicationId = application.id.some
                )
                Future(
                  InternalServerError(
                    "Erreur interne: l'application n'a pas pu être indiquée comme archivée"
                  )
                )
              }
            } else {
              eventService.log(
                EventType.TerminateUnauthorized,
                s"L'utilisateur n'a pas le droit de clôturer la demande $applicationId",
                applicationId = application.id.some
              )
              Future(
                Unauthorized("Seul le créateur de la demande ou un expert peut archiver la demande")
              )
            }
          }
        )
      }
    }

  //
  // Signature Cookie (for shared accounts)
  //

  private val sharedAccountUserSignatureKey = "sharedAccountUserSignature"

  /** Note: using session because it is signed, other cookies are not signed */
  private def readSharedAccountUserSignature(session: Session): Option[String] =
    session.get(sharedAccountUserSignatureKey)

  /** Security: does not save signatures that are too big (longer than 1000 chars) */
  private def saveSharedAccountUserSignature[R](session: Session, signature: String): Session =
    if (signature.length <= 1000)
      session + (sharedAccountUserSignatureKey -> signature)
    else
      session

  private def removeSharedAccountUserSignature(session: Session): Session =
    session - sharedAccountUserSignatureKey

}
