package controllers

import java.nio.file.{Files, Path, Paths}
import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import actions._
import cats.implicits.{catsSyntaxOption, catsSyntaxOptionId, catsSyntaxTuple2Semigroupal}
import cats.syntax.all._
import constants.Constants
import forms.FormsPlusMap
import helper.BooleanHelper.not
import helper.CSVUtil.escape
import helper.StringHelper.{CanonizeString, NonEmptyTrimmedString}
import helper.Time.zonedDateTimeOrdering
import helper.{Hash, Time, UUIDHelper}
import javax.inject.{Inject, Singleton}
import models.Answer.AnswerType
import models.EventType._
import models._
import models.formModels.{AnswerFormData, ApplicationFormData, InvitationFormData}
import models.mandat.Mandat
import org.webjars.play.WebJarsUtil
import play.api.cache.AsyncCacheApi
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.twirl.api.Html
import serializers.{AttachmentHelper, DataModel, Keys}
import services._
import views.stats.StatsData

import scala.concurrent.Future.successful
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
case class ApplicationController @Inject() (
    loginAction: LoginAction,
    cache: AsyncCacheApi,
    userService: UserService,
    applicationService: ApplicationService,
    notificationsService: NotificationService,
    eventService: EventService,
    mandatService: MandatService,
    organisationService: OrganisationService,
    userGroupService: UserGroupService,
    configuration: play.api.Configuration,
    ws: WSClient,
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with play.api.i18n.I18nSupport
    with Operators.ApplicationOperators {

  private val filesPath = configuration.underlying.getString("app.filesPath")
  private val featureMandatSms: Boolean = configuration.get[Boolean]("app.features.smsMandat")

  private val featureCanSendApplicationsAnywhere: Boolean =
    configuration.get[Boolean]("app.features.canSendApplicationsAnywhere")

  // This is a feature that is temporary and should be activated
  // for short period of time during migrations for smooth handling of files.
  // Just remove the env variable FILES_SECOND_INSTANCE_HOST to deactivate.
  private val filesSecondInstanceHost: Option[String] =
    configuration.getOptional[String]("app.filesSecondInstanceHost")

  private val filesExpirationInDays: Int = configuration.get[Int]("app.filesExpirationInDays")

  private val dir = Paths.get(s"$filesPath")
  if (!Files.isDirectory(dir)) {
    Files.createDirectories(dir)
  }

  private val success = "success"

  private def applicationForm(currentUser: User) =
    Form(
      mapping(
        "subject" -> nonEmptyText.verifying(maxLength(150)),
        "description" -> nonEmptyText,
        "usagerPrenom" -> nonEmptyText.verifying(maxLength(30)),
        "usagerNom" -> nonEmptyText.verifying(maxLength(30)),
        "usagerBirthDate" -> nonEmptyText.verifying(maxLength(30)),
        "usagerOptionalInfos" -> FormsPlusMap.map(text.verifying(maxLength(30))),
        "users" -> list(uuid),
        "groups" -> list(uuid)
          .verifying("Vous devez sélectionner au moins une structure", _.nonEmpty),
        "category" -> optional(text),
        "selected-subject" -> optional(text),
        "signature" -> (
          if (currentUser.sharedAccount)
            nonEmptyText.transform[Option[String]](Some.apply, _.getOrElse(""))
          else ignored(Option.empty[String])
        ),
        "mandatType" -> text,
        "mandatDate" -> nonEmptyText,
        "linkedMandat" -> optional(uuid)
      )(ApplicationFormData.apply)(ApplicationFormData.unapply)
    )

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
        .map(_.organisation)
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
        group.organisation match {
          case None     => false
          case Some(id) => visibleOrganisations.contains(id)
        }
      )
    }

  private def fetchGroupsWithInstructors(
      areaId: UUID,
      currentUser: User,
      rights: Authorization.UserRights
  ): Future[(List[UserGroup], List[User], List[User])] = {
    val groupsOfAreaFuture = userGroupService.byArea(areaId)
    groupsOfAreaFuture.map { groupsOfArea =>
      val visibleGroups = filterVisibleGroups(areaId, currentUser, rights)(groupsOfArea)
      val usersInThoseGroups = userService.byGroupIds(visibleGroups.map(_.id))
      // Note: we don't care about users who are in several areas
      val coworkers = usersInThoseGroups
        .filter(user =>
          user.helper && user.groupIds.toSet.intersect(currentUser.groupIds.toSet).nonEmpty
        )
        .filterNot(user => user.id === currentUser.id)
      // This could be optimized by doing only one SQL query
      val instructorsOfGroups = usersInThoseGroups.filter(_.instructor)
      val groupIdsWithInstructors = instructorsOfGroups.flatMap(_.groupIds).toSet
      val groupsOfAreaWithInstructor =
        visibleGroups.filter(user => groupIdsWithInstructors.contains(user.id))
      (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers)
    }
  }

  // We want to ultimately remove the use of `request.currentUser.areas` for the
  // prefered linked `UserGroup.areaIds`
  private def currentAreaLegacy(implicit request: RequestWithUserData[_]): Area =
    request.session
      .get(Keys.Session.areaId)
      .flatMap(UUIDHelper.fromString)
      .orElse(request.currentUser.areas.headOption)
      .flatMap(Area.fromId)
      .getOrElse(Area.all.head)

  // Note: `defaultArea` is not stateful between pages,
  // because changing area is considered to be a special case.
  // This might change in the future depending on user feedback.
  private def defaultArea(user: User): Future[Area] =
    userGroupService
      .byIdsFuture(user.groupIds)
      .map(_.flatMap(_.areaIds).flatMap(Area.fromId).headOption.getOrElse(Area.ain))

  private def currentArea(implicit request: RequestWithUserData[_]): Future[Area] =
    request
      .getQueryString(Keys.QueryParam.areaId)
      .flatMap(UUIDHelper.fromString)
      .flatMap(Area.fromId)
      .map(successful)
      .getOrElse(defaultArea(request.currentUser))

  def create: Action[AnyContent] =
    loginAction.async { implicit request =>
      eventService.log(ApplicationFormShowed, "Visualise le formulaire de création de demande")
      currentArea.flatMap(currentArea =>
        fetchGroupsWithInstructors(currentArea.id, request.currentUser, request.rights).map {
          case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
            val categories = organisationService.categories
            Ok(
              views.html.createApplication(request.currentUser, request.rights, currentArea)(
                instructorsOfGroups,
                groupsOfAreaWithInstructor,
                coworkers,
                readSharedAccountUserSignature(request.session),
                canCreatePhoneMandat = currentArea === Area.calvados,
                featureMandatSms = featureMandatSms,
                featureCanSendApplicationsAnywhere = featureCanSendApplicationsAnywhere,
                categories,
                applicationForm(request.currentUser)
              )
            )
        }
      )
    }

  private def contextualizedUserName(user: User, currentAreaId: UUID): String = {
    val groups = userGroupService.byIds(user.groupIds)
    val contexts = groups
      .filter(_.areaIds.contains[UUID](currentAreaId))
      .flatMap { userGroup: UserGroup =>
        if (user.instructor) {
          for {
            areaInseeCode <- userGroup.areaIds.flatMap(Area.fromId).map(_.inseeCode).headOption
            organisationId <- userGroup.organisation
            organisation <- Organisation.byId(organisationId)
          } yield {
            s"(${organisation.name} - $areaInseeCode)"
          }
        } else {
          List(s"(${userGroup.name})")
        }
      }
      .distinct

    val capitalizedUserName = user.name.split(' ').map(_.capitalize).mkString(" ")
    if (contexts.isEmpty)
      s"$capitalizedUserName ( ${user.qualite} )"
    else
      s"$capitalizedUserName ${contexts.mkString(",")}"
  }

  def createPost: Action[AnyContent] =
    loginAction.async { implicit request =>
      val form = applicationForm(request.currentUser).bindFromRequest()
      val applicationId = AttachmentHelper.retrieveOrGenerateApplicationId(form.data)

      // Get `areaId` from the form, to avoid losing it in case of errors
      val currentArea: Area = form.data
        .get(Keys.Application.areaId)
        .map(UUID.fromString)
        .flatMap(Area.fromId)
        .getOrElse(throw new Exception("No key 'areaId' in the Application creation form."))

      val (pendingAttachments, newAttachments) =
        AttachmentHelper.computeStoreAndRemovePendingAndNewApplicationAttachment(
          applicationId,
          computeAttachmentsToStore(request),
          filesPath
        )
      form.fold(
        formWithErrors =>
          // binding failure, you retrieve the form containing errors:
          fetchGroupsWithInstructors(currentArea.id, request.currentUser, request.rights).map {
            case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
              eventService.log(
                ApplicationCreationInvalid,
                s"L'utilisateur essaie de créer une demande invalide ${formWithErrors.errors.map(_.message)}"
              )

              BadRequest(
                views.html
                  .createApplication(request.currentUser, request.rights, currentArea)(
                    instructorsOfGroups,
                    groupsOfAreaWithInstructor,
                    coworkers,
                    None,
                    canCreatePhoneMandat = currentArea === Area.calvados,
                    featureMandatSms = featureMandatSms,
                    featureCanSendApplicationsAnywhere = featureCanSendApplicationsAnywhere,
                    organisationService.categories,
                    formWithErrors,
                    pendingAttachments.keys ++ newAttachments.keys
                  )
              )
          },
        applicationData =>
          Future {
            // Note: we will deprecate .currentArea as a variable stored in the cookies
            val currentAreaId: UUID = currentArea.id
            val usersInGroups = userService.byGroupIds(applicationData.groups)
            val instructors: List[User] = usersInGroups.filter(_.instructor)
            val coworkers: List[User] = applicationData.users.flatMap(id => userService.byId(id))
            val invitedUsers: Map[UUID, String] = (instructors ::: coworkers)
              .map(user => user.id -> contextualizedUserName(user, currentAreaId))
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
                case (infoName, infoValue) if infoName.trim.nonEmpty && infoValue.trim.nonEmpty =>
                  infoName.trim -> infoValue.trim
              }
            val application = Application(
              applicationId,
              Time.nowParis(),
              contextualizedUserName(request.currentUser, currentAreaId),
              request.currentUser.id,
              applicationData.subject,
              description,
              usagerInfos,
              invitedUsers,
              currentArea.id,
              irrelevant = false,
              hasSelectedSubject =
                applicationData.selectedSubject.contains[String](applicationData.subject),
              category = applicationData.category,
              files = newAttachments ++ pendingAttachments,
              mandatType = DataModel.Application.MandatType
                .dataModelDeserialization(applicationData.mandatType),
              mandatDate = Some(applicationData.mandatDate),
              invitedGroupIdsAtCreation = applicationData.groups
            )
            if (applicationService.createApplication(application)) {
              notificationsService.newApplication(application)
              eventService.log(
                ApplicationCreated,
                s"La demande ${application.id} a été créée",
                Some(application)
              )
              applicationData.linkedMandat.foreach { mandatId =>
                mandatService
                  .linkToApplication(Mandat.Id(mandatId), applicationId)
                  .onComplete {
                    case Failure(error) =>
                      eventService.log(
                        ApplicationLinkedToMandatError,
                        s"Erreur pour faire le lien entre le mandat $mandatId et la demande $applicationId",
                        Some(application),
                        underlyingException = Some(error)
                      )
                    case Success(Left(error)) =>
                      eventService.logError(error, application = Some(application))
                    case Success(Right(_)) =>
                      eventService.log(
                        ApplicationLinkedToMandat,
                        s"La demande ${application.id} a été liée au mandat $mandatId",
                        Some(application)
                      )
                  }
              }
              Redirect(routes.ApplicationController.myApplications())
                .withSession(
                  applicationData.signature.fold(removeSharedAccountUserSignature(request.session))(
                    signature => saveSharedAccountUserSignature(request.session, signature)
                  )
                )
                .flashing(success -> "Votre demande a bien été envoyée")
            } else {
              eventService.log(
                ApplicationCreationError,
                s"La demande ${application.id} n'a pas pu être créée",
                Some(application)
              )
              InternalServerError(
                "Erreur Interne: Votre demande n'a pas pu être envoyée. Merci de réessayer ou de contacter l'administrateur"
              )
            }
          }
      )
    }

  private def computeAttachmentsToStore(
      request: RequestWithUserData[AnyContent]
  ): Iterable[(Path, String)] =
    request.body.asMultipartFormData
      .map(_.files.filter(_.key.matches("file\\[\\d+\\]")))
      .getOrElse(Nil)
      .collect {
        case attachment if attachment.filename.nonEmpty =>
          attachment.ref.path -> attachment.filename
      }

  private def allApplicationVisibleByUserAdmin(
      user: User,
      areaOption: Option[Area]
  ): Future[List[Application]] =
    (user.admin, areaOption) match {
      case (true, None) =>
        applicationService.allForAreas(user.areas)
      case (true, Some(area)) =>
        applicationService.allForAreas(List(area.id))
      case (false, None) if user.groupAdmin =>
        val userIds = userService.byGroupIds(user.groupIds, includeDisabled = true).map(_.id)
        applicationService.allForUserIds(userIds)
      case (false, Some(area)) if user.groupAdmin =>
        val userGroupIds =
          userGroupService.byIds(user.groupIds).filter(_.areaIds.contains[UUID](area.id)).map(_.id)
        val userIds = userService.byGroupIds(userGroupIds, includeDisabled = true).map(_.id)
        applicationService.allForUserIds(userIds)
      case _ =>
        Future(Nil)
    }

  def all(areaId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      (request.currentUser.admin, request.currentUser.groupAdmin) match {
        case (false, false) =>
          eventService.log(
            AllApplicationsUnauthorized,
            "L'utilisateur n'a pas de droit d'afficher toutes les demandes"
          )
          Future(
            Unauthorized(
              s"Vous n'avez pas les droits suffisants pour voir cette page. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
          )
        case _ =>
          val area = if (areaId === Area.allArea.id) None else Area.fromId(areaId)
          allApplicationVisibleByUserAdmin(request.currentUser, area).map {
            unfilteredApplications =>
              val filteredApplications =
                request.getQueryString(Keys.QueryParam.filterIsOpen) match {
                  case Some(_) => unfilteredApplications.filterNot(_.closed)
                  case None    => unfilteredApplications
                }
              eventService.log(
                AllApplicationsShowed,
                s"Visualise la liste des demandes de $areaId - taille = ${filteredApplications.size}"
              )
              Ok(
                views.html
                  .allApplications(request.currentUser, request.rights)(
                    filteredApplications,
                    area.getOrElse(Area.allArea)
                  )
              )
          }
      }
    }

  def myApplications: Action[AnyContent] =
    loginAction { implicit request =>
      val myApplications = applicationService.allOpenOrRecentForUserId(
        request.currentUser.id,
        request.currentUser.admin,
        Time.nowParis()
      )
      val (myClosedApplications, myOpenApplications) = myApplications.partition(_.closed)

      eventService.log(
        MyApplicationsShowed,
        s"Visualise la liste des applications : open=${myOpenApplications.size}/closed=${myClosedApplications.size}"
      )
      Ok(
        views.html.myApplications(request.currentUser, request.rights)(
          myOpenApplications,
          myClosedApplications
        )
      )
    }

  private def statsAggregates(applications: List[Application], users: List[User]): StatsData = {
    val now = Time.nowParis()
    val applicationsByArea: Map[Area, List[Application]] =
      applications
        .groupBy(_.area)
        .flatMap { case (areaId: UUID, applications: Seq[Application]) =>
          Area.all
            .find(area => area.id === areaId)
            .map(area => (area, applications))
        }

    val firstDate: ZonedDateTime =
      if (applications.isEmpty) now else applications.map(_.creationDate).min
    val months = Time.monthsMap(firstDate, now)
    val allApplications = applicationsByArea.flatMap(_._2).toList
    val allApplicationsByArea = applicationsByArea.map { case (area, applications) =>
      StatsData.AreaAggregates(
        area = area,
        StatsData.ApplicationAggregates(
          applications = applications,
          months = months,
          usersRelatedToApplications = users
        )
      )
    }.toList
    val data = StatsData(
      all = StatsData.ApplicationAggregates(
        applications = allApplications,
        months = months,
        usersRelatedToApplications = users
      ),
      aggregatesByArea = allApplicationsByArea
    )
    data
  }

  private def anonymousGroupsAndUsers(
      groups: List[UserGroup]
  ): Future[(List[User], List[Application])] =
    for {
      users <- userService.byGroupIdsAnonymous(groups.map(_.id))
      applications <- applicationService.allForUserIds(users.map(_.id))
    } yield (users, applications)

  private def generateStats[A](
      areaIds: List[UUID],
      organisationIds: List[Organisation.Id],
      groupIds: List[UUID],
      creationMinDate: LocalDate,
      creationMaxDate: LocalDate
  )(implicit
      webJarsUtil: org.webjars.play.WebJarsUtil,
      request: RequestWithUserData[A]
  ): Future[Html] = {
    val usersAndApplications: Future[(List[User], List[Application])] =
      (areaIds, organisationIds, groupIds) match {
        case (Nil, Nil, Nil) =>
          userService.allNoNameNoEmail.zip(applicationService.all())
        case (_ :: _, Nil, Nil) =>
          for {
            groups <- userGroupService.byAreas(areaIds)
            users <- (
              userService.byGroupIdsAnonymous(groups.map(_.id)),
              applicationService.allForAreas(areaIds)
            ).mapN(Tuple2.apply)
          } yield users
        case (_ :: _, _ :: _, Nil) =>
          for {
            groups <- userGroupService
              .byOrganisationIds(organisationIds)
              .map(_.filter(_.areaIds.intersect(areaIds).nonEmpty))
            users <- userService.byGroupIdsAnonymous(groups.map(_.id))
            applications <- applicationService
              .allForUserIds(users.map(_.id))
              .map(_.filter(application => areaIds.contains(application.area)))
          } yield (users, applications)
        case (_, _ :: _, _) =>
          userGroupService.byOrganisationIds(organisationIds).flatMap(anonymousGroupsAndUsers)
        case (_, _, _) =>
          userGroupService
            .byOrganisationIds(organisationIds)
            .flatMap(anonymousGroupsAndUsers)
            .map { case (users, allApplications) =>
              val applications = allApplications.filter { application =>
                application.isWithoutInvitedGroupIdsLegacyCase ||
                application.invitedGroups.intersect(groupIds.toSet).nonEmpty
              }
              (users, applications)
            }
      }

    // Filter creation dates
    def isBeforeOrEqual(d1: LocalDate, d2: LocalDate): Boolean = !d1.isAfter(d2)

    val applicationsFuture = usersAndApplications.map { case (_, applications) =>
      applications.filter(application =>
        isBeforeOrEqual(creationMinDate, application.creationDate.toLocalDate) &&
          isBeforeOrEqual(application.creationDate.toLocalDate, creationMaxDate)
      )
    }

    // Users whose id is in the `Application`
    val relatedUsersFuture = applicationsFuture.map { applications =>
      val ids: List[UUID] = applications.flatMap { application =>
        application.creatorUserId :: application.invitedUsers.keys.toList
      }
      userService.byIds(ids, includeDisabled = true)
    }

    // Note: `users` are Users on which we make stats (count, ...)
    // `relatedUsers` are Users to help Applications stats (linked orgs, ...)
    for {
      users <- usersAndApplications.map { case (users, _) => users }
      applications <- applicationsFuture
      relatedUsers <- relatedUsersFuture
    } yield views.html.stats.charts(Authorization.isAdmin(request.rights))(
      statsAggregates(applications, relatedUsers),
      users
    )
  }

  // A `def` for the LocalDate.now()
  private def statsForm =
    Form(
      tuple(
        "areas" -> default(list(uuid), List()),
        "organisations" -> default(list(of[Organisation.Id]), List()),
        "groups" -> default(list(uuid), List()),
        "creationMinDate" -> default(localDate, LocalDate.now().minusDays(30)),
        "creationMaxDate" -> default(localDate, LocalDate.now())
      )
    )

  def stats: Action[AnyContent] =
    loginAction.async { implicit request =>
      // TODO: remove `.get`
      val (areaIds, organisationIds, groupIds, creationMinDate, creationMaxDate) =
        statsForm.bindFromRequest().value.get

      val observableOrganisationIds = if (Authorization.isAdmin(request.rights)) {
        organisationIds
      } else {
        organisationIds.filter(id => Authorization.canObserveOrganisation(id)(request.rights))
      }

      val observableGroupIds = if (Authorization.isAdmin(request.rights)) {
        groupIds
      } else {
        groupIds.intersect(request.currentUser.groupIds)
      }

      val cacheKey =
        Authorization.isAdmin(request.rights).toString +
          ".stats." +
          Hash.sha256(
            areaIds.toString + observableOrganisationIds.toString + observableGroupIds.toString +
              creationMinDate.toString + creationMaxDate.toString
          )

      userGroupService.byIdsFuture(request.currentUser.groupIds).flatMap { currentUserGroups =>
        cache
          .getOrElseUpdate[Html](cacheKey, 1.hours)(
            generateStats(
              areaIds,
              observableOrganisationIds,
              observableGroupIds,
              creationMinDate,
              creationMaxDate
            )
          )
          .map { html =>
            eventService.log(StatsShowed, "Visualise les stats")
            Ok(
              views.html.stats.page(request.currentUser, request.rights)(
                html,
                groupsThatCanBeFilteredBy = currentUserGroups,
                areaIds,
                organisationIds,
                groupIds,
                creationMinDate,
                creationMaxDate
              )
            )
          }
      }
    }

  def allAs(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      val userOption = userService.byId(userId)
      (request.currentUser.admin, userOption) match {
        case (false, Some(user)) =>
          eventService.log(
            AllAsUnauthorized,
            s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur $userId",
            involvesUser = Some(user)
          )
          Future(
            Unauthorized(
              s"Vous n'avez pas le droit de faire ça, vous n'êtes pas administrateur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
          )
        case (true, Some(user)) if user.admin =>
          eventService.log(
            AllAsUnauthorized,
            s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur admin $userId",
            involvesUser = Some(user)
          )
          Future(
            Unauthorized(
              s"Vous n'avez pas le droit de faire ça avec un compte administrateur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
          )
        case (true, Some(user)) if request.currentUser.areas.intersect(user.areas).nonEmpty =>
          LoginAction.readUserRights(user).map { userRights =>
            val targetUserId = user.id
            val applicationsFromTheArea = List.empty[Application]
            eventService
              .log(
                AllAsShowed,
                s"Visualise la vue de l'utilisateur $userId",
                involvesUser = Some(user)
              )
            val applications = applicationService.allForUserId(
              userId = targetUserId,
              anonymous = request.currentUser.admin
            )
            val (closedApplications, openApplications) = applications.partition(_.closed)
            Ok(
              views.html.myApplications(user, userRights)(
                myOpenApplications = openApplications,
                myClosedApplications = closedApplications,
                applicationsFromTheArea = applicationsFromTheArea
              )
            )
          }
        case _ =>
          eventService.log(AllAsNotFound, s"L'utilisateur $userId n'existe pas")
          Future(
            BadRequest(
              s"L'utilisateur n'existe pas ou vous n'avez pas le droit d'accéder à cette page. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
            )
          )
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
        application.status,
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

      eventService.log(MyCSVShowed, s"Visualise le CSV de mes demandes")
      Ok(csvContent)
        .withHeaders(
          "Content-Disposition" -> s"""attachment; filename="aplus-demandes-$date.csv""""
        )
        .as("text/csv")
    }

  def allCSV(areaId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      val area = if (areaId === Area.allArea.id) Option.empty else Area.fromId(areaId)
      val exportedApplicationsFuture =
        if (request.currentUser.admin || request.currentUser.groupAdmin) {
          allApplicationVisibleByUserAdmin(request.currentUser, area)
        } else {
          Future(Nil)
        }

      exportedApplicationsFuture.map { exportedApplications =>
        val date = Time.formatPatternFr(Time.nowParis(), "YYY-MM-dd-HH'h'mm")
        val csvContent = applicationsToCSV(exportedApplications)

        eventService.log(AllCSVShowed, s"Visualise un CSV pour la zone ${area}")
        val filenameAreaPart: String = area.map(_.name.stripSpecialChars).getOrElse("tous")
        Ok(csvContent)
          .withHeaders(
            "Content-Disposition" -> s"""attachment; filename="aplus-demandes-$date-${filenameAreaPart}.csv""""
          )
          .as("text/csv")
      }
    }

  private def answerForm(currentUser: User) =
    Form(
      mapping(
        "answer_type" -> nonEmptyText.verifying(maxLength(20)),
        "message" -> optional(nonEmptyText),
        "irrelevant" -> boolean,
        "usagerOptionalInfos" -> FormsPlusMap.map(text.verifying(maxLength(30))),
        "privateToHelpers" -> boolean,
        "signature" -> (
          if (currentUser.sharedAccount)
            nonEmptyText.transform[Option[String]](Some.apply, _.orEmpty)
          else ignored(Option.empty[String])
        )
      )(AnswerFormData.apply)(AnswerFormData.unapply)
    )

  private def usersWhoCanBeInvitedOn[A](
      application: Application
  )(implicit request: RequestWithUserData[A]): Future[List[User]] =
    (if (request.currentUser.expert) {
       //TODO : remove when the group invitation has been tested in prod by the expert
       userGroupService.byArea(currentAreaLegacy.id).map { groupsOfArea =>
         userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
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
         userGroupService.byArea(application.area).map { groupsOfArea =>
           val invitedGroups: Set[UUID] =
             groupsOfInvitedUsers.intersect(groupsOfArea.map(_.id).toSet)
           userService.byGroupIds(invitedGroups.toList).filter(_.instructor)
         }
       }
       coworkers.combine(instructorsCoworkers)
     }).map(
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
    userGroupService.byArea(forAreaId).map { groupsOfArea =>
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

  def show(id: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(id) { application =>
        val selectedAreaId =
          if (request.currentUser.expert) currentAreaLegacy.id else application.area
        usersWhoCanBeInvitedOn(application).flatMap { usersWhoCanBeInvited =>
          groupsWhichCanBeInvited(selectedAreaId, application).map { invitableGroups =>
            val groups = userGroupService
              .byIds(usersWhoCanBeInvited.flatMap(_.groupIds))
              .filter(_.areaIds.contains[UUID](application.area))
            val groupsWithUsersThatCanBeInvited = groups.map { group =>
              group -> usersWhoCanBeInvited.filter(_.groupIds.contains[UUID](group.id))
            }

            val openedTab = request.flash.get("opened-tab").getOrElse("answer")
            eventService.log(ApplicationShowed, s"Demande $id consultée", Some(application))
            Ok(
              views.html.showApplication(request.currentUser, request.rights)(
                groupsWithUsersThatCanBeInvited,
                invitableGroups,
                application,
                answerForm(request.currentUser),
                openedTab,
                currentAreaLegacy,
                readSharedAccountUserSignature(request.session),
                fileExpiryDayCount = filesExpirationInDays
              )
            )
          }
        }
      }
    }

  def answerFile(applicationId: UUID, answerId: UUID, filename: String): Action[AnyContent] =
    file(applicationId, Some(answerId), filename)

  def applicationFile(applicationId: UUID, filename: String): Action[AnyContent] =
    file(applicationId, None, filename)

  private def sendFile(localPath: Path, filename: String, remoteUrlPath: String)(implicit
      request: actions.RequestWithUserData[_]
  ): Future[Result] =
    if (Files.exists(localPath)) {
      Future(
        Ok.sendPath(
          localPath,
          true,
          { _: Path =>
            Some(filename)
          }
        )
      )
    } else {
      filesSecondInstanceHost match {
        case None =>
          eventService.log(
            FileNotFound,
            s"Le fichier n'existe pas sur le serveur"
          )
          Future(NotFound("Nous n'avons pas trouvé ce fichier"))
        case Some(domain) =>
          val cookies = request.headers.getAll(COOKIE)
          val url = domain + remoteUrlPath
          ws.url(url)
            .addHttpHeaders(cookies.map(cookie => (COOKIE, cookie)): _*)
            .get()
            .map { response =>
              if (response.status / 100 === 2) {
                val body = response.bodyAsSource
                val contentLength: Option[Long] =
                  response.header(CONTENT_LENGTH).flatMap(raw => Try(raw.toLong).toOption)
                // Note: `streamed` should set `Content-Disposition`
                // https://github.com/playframework/playframework/blob/2.8.x/core/play/src/main/scala/play/api/mvc/Results.scala#L523
                Ok.streamed(
                  content = body,
                  contentLength = contentLength,
                  `inline` = true,
                  fileName = Some(filename)
                )
              } else {
                eventService.log(
                  FileNotFound,
                  s"La requête vers le serveur distant $url a échoué (status ${response.status})"
                )
                NotFound("Nous n'avons pas trouvé ce fichier")
              }
            }
      }
    }

  private def file(applicationId: UUID, answerIdOption: Option[UUID], filename: String) =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application: Application =>
        answerIdOption match {
          case Some(answerId)
              if Authorization.answerFileCanBeShowed(filesExpirationInDays)(application, answerId)(
                request.currentUser,
                request.rights
              ) =>
            application.answers.find(_.id === answerId) match {
              case Some(answer) if answer.files.getOrElse(Map.empty).contains(filename) =>
                eventService.log(
                  FileOpened,
                  s"Le fichier de la réponse $answerId sur la demande $applicationId a été ouvert"
                )
                sendFile(
                  Paths.get(s"$filesPath/ans_$answerId-$filename"),
                  filename,
                  s"/toutes-les-demandes/$applicationId/messages/$answerId/fichiers/$filename"
                )
              case _ =>
                eventService.log(
                  FileNotFound,
                  s"Le fichier de la réponse $answerId sur la demande $applicationId n'existe pas"
                )
                Future(NotFound("Nous n'avons pas trouvé ce fichier"))
            }
          case None
              if Authorization.applicationFileCanBeShowed(filesExpirationInDays)(application)(
                request.currentUser,
                request.rights
              ) =>
            if (application.files.contains(filename)) {
              eventService
                .log(FileOpened, s"Le fichier de la demande $applicationId a été ouvert")
              sendFile(
                Paths.get(s"$filesPath/app_$applicationId-$filename"),
                filename,
                s"/toutes-les-demandes/$applicationId/fichiers/$filename"
              )
            } else {
              eventService.log(
                FileNotFound,
                s"Le fichier de la demande $applicationId n'existe pas"
              )
              Future(NotFound("Nous n'avons pas trouvé ce fichier"))
            }
          case _ =>
            eventService.log(
              FileUnauthorized,
              s"L'accès aux fichiers sur la demande $applicationId n'est pas autorisé",
              Some(application)
            )
            Future(
              Unauthorized(
                s"Vous n'avez pas les droits suffisants pour voir les fichiers sur cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
              )
            )
        }
      }
    }

  private def buildAnswerMessage(message: String, signature: Option[String]) =
    signature.map(s => message + "\n\n" + s).getOrElse(message)

  private val ApplicationProcessedMessage = "J’ai traité la demande."
  private val WorkInProgressMessage = "Je m’en occupe."
  private val WrongInstructorMessage = "Je ne suis pas le bon interlocuteur."

  def answer(applicationId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application =>
        val form = answerForm(request.currentUser).bindFromRequest()
        val answerId = AttachmentHelper.retrieveOrGenerateAnswerId(form.data)
        val (pendingAttachments, newAttachments) =
          AttachmentHelper.computeStoreAndRemovePendingAndNewAnswerAttachment(
            answerId,
            computeAttachmentsToStore(request),
            filesPath
          )
        form.fold(
          formWithErrors => {
            val error =
              s"Erreur dans le formulaire de réponse (${formWithErrors.errors.map(_.message).mkString(", ")})."
            eventService.log(AnswerNotCreated, s"$error")
            Future(
              Redirect(
                routes.ApplicationController.show(applicationId).withFragment("answer-error")
              )
                .flashing("answer-error" -> error, "opened-tab" -> "anwser")
            )
          },
          answerData => {
            val answerType = AnswerType.fromString(answerData.answerType)
            val currentAreaId = application.area

            val message = (answerType, answerData.message) match {
              case (AnswerType.Custom, Some(message)) =>
                buildAnswerMessage(message, answerData.signature)
              case (AnswerType.ApplicationProcessed, _) =>
                buildAnswerMessage(ApplicationProcessedMessage, answerData.signature)
              case (AnswerType.WorkInProgress, _) =>
                buildAnswerMessage(WorkInProgressMessage, answerData.signature)
              case (AnswerType.WrongInstructor, _) =>
                buildAnswerMessage(WrongInstructorMessage, answerData.signature)
              case (AnswerType.Custom, None) => buildAnswerMessage("", answerData.signature)
            }

            val answer = Answer(
              answerId,
              applicationId,
              Time.nowParis(),
              answerType,
              message,
              request.currentUser.id,
              contextualizedUserName(request.currentUser, currentAreaId),
              Map.empty[UUID, String],
              not(answerData.privateToHelpers),
              answerData.applicationIsDeclaredIrrelevant,
              answerData.usagerOptionalInfos.collect {
                case (NonEmptyTrimmedString(infoName), NonEmptyTrimmedString(infoValue)) =>
                  (infoName, infoValue)
              }.some,
              files = (newAttachments ++ pendingAttachments).some,
              invitedGroupIds = List.empty[UUID]
            )
            // If the new answer creator is the application creator, we force the application reopening
            val shouldBeOpened = answer.creatorUserID === application.creatorUserId
            val answerAdded =
              applicationService.addAnswer(applicationId, answer, false, shouldBeOpened)

            if (answerAdded === 1) {
              eventService.log(
                AnswerCreated,
                s"La réponse ${answer.id} a été créée sur la demande $applicationId",
                application.some
              )
              notificationsService.newAnswer(application, answer)
              Future(
                Redirect(s"${routes.ApplicationController.show(applicationId)}#answer-${answer.id}")
                  .withSession(
                    answerData.signature.fold(removeSharedAccountUserSignature(request.session))(
                      signature => saveSharedAccountUserSignature(request.session, signature)
                    )
                  )
                  .flashing(success -> "Votre réponse a bien été envoyée")
              )
            } else {
              eventService.log(
                AnswerNotCreated,
                s"La réponse ${answer.id} n'a pas été créée sur la demande $applicationId : problème BDD",
                Some(application)
              )
              Future(InternalServerError("Votre réponse n'a pas pu être envoyée"))
            }
          }
        )
      }
    }

  private val inviteForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid),
      "groups" -> list(uuid),
      "privateToHelpers" -> boolean
    )(InvitationFormData.apply)(InvitationFormData.unapply)
  )

  def invite(applicationId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application =>
        inviteForm
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val error =
                s"Erreur dans le formulaire d’invitation (${formWithErrors.errors.map(_.message).mkString(", ")})."
              eventService.log(InviteNotCreated, error)
              Future(
                Redirect(
                  routes.ApplicationController.show(applicationId).withFragment("answer-error")
                )
                  .flashing("answer-error" -> error, "opened-tab" -> "invite")
              )
            },
            inviteData =>
              if (inviteData.invitedUsers.isEmpty && inviteData.invitedGroups.isEmpty) {
                val error =
                  s"Erreur dans le formulaire d’invitation (une personne ou un organisme doit être sélectionné)."
                eventService.log(InviteNotCreated, error)
                Future(
                  Redirect(
                    routes.ApplicationController.show(applicationId).withFragment("answer-error")
                  )
                    .flashing("answer-error" -> error, "opened-tab" -> "invite")
                )
              } else {
                val selectedAreaId =
                  if (request.currentUser.expert) currentAreaLegacy.id else application.area
                usersWhoCanBeInvitedOn(application).flatMap { singleUsersWhoCanBeInvited =>
                  groupsWhichCanBeInvited(selectedAreaId, application).map { invitableGroups =>
                    val usersWhoCanBeInvited: List[User] =
                      singleUsersWhoCanBeInvited ::: userService
                        .byGroupIds(invitableGroups.map(_.id))
                    val invitedUsers: Map[UUID, String] = usersWhoCanBeInvited
                      .filter(user =>
                        inviteData.invitedUsers.contains[UUID](user.id) ||
                          inviteData.invitedGroups.toSet.intersect(user.groupIds.toSet).nonEmpty
                      )
                      .map(user => (user.id, contextualizedUserName(user, selectedAreaId)))
                      .toMap

                    val answer = Answer(
                      UUID.randomUUID(),
                      applicationId,
                      Time.nowParis(),
                      AnswerType.Custom,
                      inviteData.message,
                      request.currentUser.id,
                      contextualizedUserName(request.currentUser, selectedAreaId),
                      invitedUsers,
                      not(inviteData.privateToHelpers),
                      declareApplicationHasIrrelevant = false,
                      Map.empty[String, String].some,
                      invitedGroupIds = inviteData.invitedGroups
                    )

                    if (applicationService.addAnswer(applicationId, answer) === 1) {
                      notificationsService.newAnswer(application, answer)
                      eventService.log(
                        AgentsAdded,
                        s"L'ajout d'utilisateur ${answer.id} a été créé sur la demande $applicationId",
                        Some(application)
                      )
                      Redirect(routes.ApplicationController.myApplications())
                        .flashing(success -> "Les utilisateurs ont été invités sur la demande")
                    } else {
                      eventService.log(
                        AgentsNotAdded,
                        s"L'ajout d'utilisateur ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD",
                        Some(application)
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
      withApplication(applicationId) { application: Application =>
        val currentAreaId = application.area
        if (application.canHaveExpertsInvitedBy(request.currentUser)) {
          userService.allExperts.map { expertUsers =>
            val experts: Map[UUID, String] = expertUsers
              .map(user => user.id -> contextualizedUserName(user, currentAreaId))
              .toMap
            val answer = Answer(
              UUID.randomUUID(),
              applicationId,
              Time.nowParis(),
              AnswerType.Custom,
              "J'ajoute un expert",
              request.currentUser.id,
              contextualizedUserName(request.currentUser, currentAreaId),
              experts,
              visibleByHelpers = true,
              declareApplicationHasIrrelevant = false,
              Map.empty[String, String].some,
              invitedGroupIds = List.empty[UUID]
            )
            if (applicationService.addAnswer(applicationId, answer, expertInvited = true) === 1) {
              notificationsService.newAnswer(application, answer)
              eventService.log(
                AddExpertCreated,
                s"La réponse ${answer.id} a été créée sur la demande $applicationId",
                Some(application)
              )
              Redirect(routes.ApplicationController.myApplications())
                .flashing(success -> "Un expert a été invité sur la demande")
            } else {
              eventService.log(
                AddExpertNotCreated,
                s"L'invitation d'experts ${answer.id} n'a pas été créée sur la demande $applicationId : problème BDD",
                Some(application)
              )
              InternalServerError("L'expert n'a pas pu être invité")
            }
          }
        } else {
          eventService.log(
            AddExpertUnauthorized,
            s"L'invitation d'experts pour la demande $applicationId n'est pas autorisée",
            Some(application)
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
      withApplication(applicationId) { application: Application =>
        import eventService._
        successful(application.canBeOpenedBy(request.currentUser)).flatMap {
          case true =>
            applicationService
              .reopen(applicationId)
              .filter(identity)
              .map { _ =>
                val message = "La demande a bien été réouverte"
                log(ReopenCompleted, message, application.some)
                Redirect(routes.ApplicationController.myApplications()).flashing(success -> message)
              }
              .recover { _ =>
                val message = "La demande n'a pas pu être réouverte"
                log(ReopenError, message, application.some)
                InternalServerError(message)
              }
          case false =>
            val message = s"Non autorisé à réouvrir la demande $applicationId"
            log(ReopenUnauthorized, message, application.some)
            successful(Unauthorized(message))
        }
      }
    }

  def terminate(applicationId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application: Application =>
        request.getQueryString(Keys.QueryParam.usefulness) match {
          case None =>
            eventService
              .log(
                TerminateIncompleted,
                s"La demande de clôture pour $applicationId est incomplète"
              )
            Future(
              BadGateway(
                s"L'utilité de la demande n'est pas présente, il s'agit sûrement d'une erreur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
              )
            )
          case Some(usefulness) =>
            val finalUsefulness =
              usefulness.some.filter(_ => request.currentUser.id === application.creatorUserId)
            if (application.canBeClosedBy(request.currentUser)) {
              if (
                applicationService
                  .close(applicationId, finalUsefulness, Time.nowParis())
              ) {
                eventService
                  .log(
                    TerminateCompleted,
                    s"La demande $applicationId est archivée",
                    Some(application)
                  )
                val successMessage =
                  s"""|La demande "${application.subject}" a bien été archivée. 
                    |Bravo et merci pour la résolution de cette demande !""".stripMargin
                Future(
                  Redirect(routes.ApplicationController.myApplications())
                    .flashing(success -> successMessage)
                )
              } else {
                eventService.log(
                  TerminateError,
                  s"La demande $applicationId n'a pas pu être archivée en BDD",
                  Some(application)
                )
                Future(
                  InternalServerError(
                    "Erreur interne: l'application n'a pas pu être indiquée comme archivée"
                  )
                )
              }
            } else {
              eventService.log(
                TerminateUnauthorized,
                s"L'utilisateur n'a pas le droit de clôturer la demande $applicationId",
                Some(application)
              )
              Future(
                Unauthorized("Seul le créateur de la demande ou un expert peut archiver la demande")
              )
            }
        }
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
