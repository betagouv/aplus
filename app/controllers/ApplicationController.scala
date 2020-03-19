package controllers

import java.nio.file.{Files, Path, Paths}
import java.time.ZonedDateTime
import java.util.UUID

import actions._
import constants.Constants
import helper.Time.zonedDateTimeOrdering
import forms.FormsPlusMap
import helper.{Hash, Time}
import javax.inject.{Inject, Singleton}
import models.{Answer, Application, Area, Authorization, Organisation, User, UserGroup}
import models.formModels.{AnswerFormData, ApplicationFormData, InvitationData}
import org.webjars.play.WebJarsUtil
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.mvc._
import services._
import helper.BooleanHelper.not
import helper.CSVUtil.escape
import models.EventType.{
  AddExpertCreated,
  AddExpertNotCreated,
  AddExpertUnauthorized,
  AgentsAdded,
  AgentsNotAdded,
  AllApplicationsShowed,
  AllApplicationsUnauthorized,
  AllAsNotFound,
  AllAsShowed,
  AllAsUnauthorized,
  AllCSVShowed,
  AnswerCreated,
  AnswerNotCreated,
  ApplicationCreated,
  ApplicationCreationError,
  ApplicationCreationInvalid,
  ApplicationFormShowed,
  ApplicationShowed,
  FileNotFound,
  FileOpened,
  FileUnauthorized,
  InviteNotCreated,
  MyApplicationsShowed,
  MyCSVShowed,
  StatsShowed,
  TerminateCompleted,
  TerminateError,
  TerminateIncompleted,
  TerminateUnauthorized
}
import play.api.cache.AsyncCacheApi
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import helper.StringHelper.CanonizeString
import serializers.AttachmentHelper

import scala.concurrent.duration._

/**
  * This controller creates an `Action` to handle HTTP requests to the
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
    organisationService: OrganisationService,
    userGroupService: UserGroupService,
    configuration: play.api.Configuration
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with play.api.i18n.I18nSupport
    with Operators.ApplicationOperators {

  private val filesPath = configuration.underlying.getString("app.filesPath")

  private val dir = Paths.get(s"$filesPath")
  if (!Files.isDirectory(dir)) {
    Files.createDirectories(dir)
  }

  private def applicationForm(currentUser: User) = Form(
    mapping(
      "subject" -> nonEmptyText.verifying(maxLength(150)),
      "description" -> nonEmptyText,
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
      "users" -> list(uuid).verifying("Vous devez sélectionner au moins une structure", _.nonEmpty),
      "organismes" -> list(text),
      "category" -> optional(text),
      "selected-subject" -> optional(text),
      "signature" -> (
        if (currentUser.sharedAccount)
          nonEmptyText.transform[Option[String]](Some.apply, _.getOrElse(""))
        else ignored(None: Option[String])
      )
    )(ApplicationFormData.apply)(ApplicationFormData.unapply)
  )

  private def fetchGroupsWithInstructors(
      areaId: UUID,
      currentUser: User
  ): Future[(List[UserGroup], List[User], List[User])] = {
    val groupsOfAreaFuture = userGroupService.byArea(areaId)
    groupsOfAreaFuture.map { groupsOfArea =>
      val usersInThoseGroups = userService.byGroupIds(groupsOfArea.map(_.id))
      val instructorsOfGroups = usersInThoseGroups.filter(_.instructor)
      // Note: we don't care about users who are in several areas
      val coworkers = usersInThoseGroups
        .filter(user =>
          user.helper && user.groupIds.toSet.intersect(currentUser.groupIds.toSet).nonEmpty
        )
        .filterNot(user => (user.id: UUID) == (currentUser.id: UUID))
      // This could be optimized by doing only one SQL query
      val groupIdsWithInstructors = instructorsOfGroups.flatMap(_.groupIds).toSet
      val groupsOfAreaWithInstructor =
        groupsOfArea.filter(user => groupIdsWithInstructors.contains(user.id))
      (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers)
    }
  }

  def create: Action[AnyContent] = loginAction.async { implicit request =>
    eventService.log(ApplicationFormShowed, "Visualise le formulaire de création de demande")
    fetchGroupsWithInstructors(request.currentArea.id, request.currentUser).map {
      case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
        Ok(
          views.html.createApplication(request.currentUser, request.rights, request.currentArea)(
            instructorsOfGroups,
            groupsOfAreaWithInstructor,
            coworkers,
            readSharedAccountUserSignature(request.session),
            applicationForm(request.currentUser)
          )
        )
    }
  }

  def createSimplified: Action[AnyContent] = loginAction.async { implicit request =>
    eventService
      .log(ApplicationFormShowed, "Visualise le formulaire simplifié de création de demande")
    fetchGroupsWithInstructors(request.currentArea.id, request.currentUser).map {
      case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
        val groupsOfAreaWithInstructorWithOrganisationSet = groupsOfAreaWithInstructor.filter({
          userGroup =>
            userGroup.organisationSetOrDeducted.nonEmpty
        })
        val categories = organisationService.categories
        Ok(
          views.html
            .simplifiedCreateApplication(request.currentUser, request.rights, request.currentArea)(
              instructorsOfGroups,
              groupsOfAreaWithInstructorWithOrganisationSet,
              coworkers,
              readSharedAccountUserSignature(request.session),
              categories,
              None,
              applicationForm(request.currentUser)
            )
        )
    }
  }

  def createPost: Action[AnyContent] = createPostBis(false)

  def createSimplifiedPost: Action[AnyContent] = createPostBis(true)

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
    val capitalizedUserName = user.name.split(' ').map(_.capitalize).mkString(" ")
    if (contexts.isEmpty)
      s"${capitalizedUserName} ( ${user.qualite} )"
    else
      s"${capitalizedUserName} ${contexts.mkString(",")}"
  }

  private def createPostBis(simplified: Boolean) = loginAction.async { implicit request =>
    val form = applicationForm(request.currentUser).bindFromRequest
    val applicationId = AttachmentHelper.retrieveOrGenerateApplicationId(form.data)
    val (pendingAttachments, newAttachments) =
      AttachmentHelper.computeStoreAndRemovePendingAndNewApplicationAttachment(
        applicationId,
        form.data,
        computeAttachmentsToStore(request),
        filesPath
      )
    form.fold(
      formWithErrors =>
        // binding failure, you retrieve the form containing errors:
        fetchGroupsWithInstructors(request.currentArea.id, request.currentUser).map {
          case (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =>
            eventService.log(
              ApplicationCreationInvalid,
              s"L'utilisateur essaie de créer une demande invalide ${formWithErrors.errors.map(_.message)}"
            )

            if (simplified) {
              val categories = organisationService.categories
              val groupsOfAreaWithInstructorWithOrganisationSet =
                groupsOfAreaWithInstructor.filter(_.organisationSetOrDeducted.nonEmpty)
              BadRequest(
                views.html.simplifiedCreateApplication(
                  request.currentUser,
                  request.rights,
                  request.currentArea
                )(
                  instructorsOfGroups,
                  groupsOfAreaWithInstructorWithOrganisationSet,
                  coworkers,
                  None,
                  categories,
                  formWithErrors("category").value,
                  formWithErrors,
                  pendingAttachments.keys ++ newAttachments.keys
                )
              )
            } else {
              BadRequest(
                views.html
                  .createApplication(request.currentUser, request.rights, request.currentArea)(
                    instructorsOfGroups,
                    groupsOfAreaWithInstructor,
                    coworkers,
                    None,
                    formWithErrors,
                    pendingAttachments.keys ++ newAttachments.keys
                  )
              )
            }
        },
      applicationData =>
        Future {
          // Note: we will deprecate .currentArea as a variable stored in the cookies
          val currentAreaId: UUID = request.currentArea.id
          val invitedUsers: Map[UUID, String] = applicationData.users.flatMap { id =>
            userService.byId(id).map(user => id -> contextualizedUserName(user, currentAreaId))
          }.toMap

          val description: String =
            applicationData.signature
              .fold(applicationData.description)(signature =>
                applicationData.description + "\n\n" + signature
              )
          val application = Application(
            applicationId,
            Time.nowParis(),
            contextualizedUserName(request.currentUser, currentAreaId),
            request.currentUser.id,
            applicationData.subject,
            description,
            applicationData.infos,
            invitedUsers,
            request.currentArea.id,
            false,
            hasSelectedSubject =
              applicationData.selectedSubject.contains[String](applicationData.subject),
            category = applicationData.category,
            files = newAttachments ++ pendingAttachments
          )
          if (applicationService.createApplication(application)) {
            notificationsService.newApplication(application)
            eventService.log(
              ApplicationCreated,
              s"La demande ${application.id} a été créée",
              Some(application)
            )
            Redirect(routes.ApplicationController.myApplications())
              .withSession(
                applicationData.signature.fold(removeSharedAccountUserSignature(request.session))(
                  signature => saveSharedAccountUserSignature(request.session, signature)
                )
              )
              .flashing("success" -> "Votre demande a bien été envoyée")
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
      .flatMap({ attachment =>
        if (attachment.filename.isEmpty) None
        else Some(attachment.ref.path -> attachment.filename)
      })

  private def allApplicationVisibleByUserAdmin(user: User, areaOption: Option[Area]) =
    (user.admin, areaOption) match {
      case (true, None) =>
        applicationService.allForAreas(user.areas)
      case (true, Some(area)) =>
        applicationService.allForAreas(List(area.id))
      case (false, None) if user.groupAdmin =>
        val userIds = userService.byGroupIds(user.groupIds).map(_.id)
        applicationService.allForUserIds(userIds)
      case (false, Some(area)) if user.groupAdmin =>
        val userGroupIds =
          userGroupService.byIds(user.groupIds).filter(_.areaIds.contains[UUID](area.id)).map(_.id)
        val userIds = userService.byGroupIds(userGroupIds).map(_.id)
        applicationService.allForUserIds(userIds)
      case _ =>
        Future(Nil)
    }

  def all(areaId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
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
        val area = if (areaId == Area.allArea.id) None else Area.fromId(areaId)
        allApplicationVisibleByUserAdmin(request.currentUser, area).map { applications =>
          eventService.log(
            AllApplicationsShowed,
            s"Visualise la liste des applications de $areaId - taille = ${applications.size}"
          )
          Ok(
            views.html
              .allApplications(request.currentUser, request.rights)(
                applications,
                area.getOrElse(Area.allArea)
              )
          )
        }
    }
  }

  def myApplications: Action[AnyContent] = loginAction { implicit request =>
    val myApplications = applicationService.allOpenOrRecentForUserId(
      request.currentUser.id,
      request.currentUser.admin,
      Time.nowParis()
    )
    val myOpenApplications = myApplications.filter(!_.closed)
    val myClosedApplications = myApplications.filter(_.closed)

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

  private def generateStats[A](
      areaIds: List[UUID],
      organisationIds: List[Organisation.Id],
      groupIds: List[UUID]
  )(
      implicit webJarsUtil: org.webjars.play.WebJarsUtil,
      request: RequestWithUserData[A]
  ): Future[Html] = {

    val (usersFuture, applicationsFuture, groupsFuture) =
      if (areaIds.isEmpty && organisationIds.isEmpty && groupIds.isEmpty) {
        (userService.all, applicationService.all, userGroupService.all)
      } else if (areaIds.nonEmpty && groupIds.isEmpty) {
        val groupsFuture = userGroupService.byAreas(areaIds)
        if (organisationIds.isEmpty) {
          val usersFuture = groupsFuture.flatMap { groups =>
            val groupIds = groups.map(_.id)
            userService.byGroupIdsAnonymous(groupIds)
          }
          (usersFuture, applicationService.allForAreas(areaIds), groupsFuture)
        } else {
          val groupsFuture = userGroupService.byOrganisationIds(organisationIds).map { groups =>
            groups.filter(group => group.areaIds.intersect(areaIds).nonEmpty)
          }
          val usersFuture =
            groupsFuture.flatMap(groups => userService.byGroupIdsAnonymous(groups.map(_.id)))
          val applicationsFuture = usersFuture
            .flatMap(users => applicationService.allForUserIds(users.map(_.id)))
            .map(_.filter(application => areaIds.contains(application.area)))
          (usersFuture, applicationsFuture, groupsFuture)
        }
      } else if (organisationIds.nonEmpty) {
        val groupsFuture = userGroupService.byOrganisationIds(organisationIds)
        val usersFuture =
          groupsFuture.flatMap(groups => userService.byGroupIdsAnonymous(groups.map(_.id)))
        val applicationsFuture =
          usersFuture.flatMap(users => applicationService.allForUserIds(users.map(_.id)))
        (usersFuture, applicationsFuture, groupsFuture)
      } else {
        val groupsFuture = userGroupService.byIdsFuture(groupIds)
        val usersFuture =
          groupsFuture.flatMap(groups => userService.byGroupIdsAnonymous(groups.map(_.id)))
        val applicationsFuture =
          usersFuture.flatMap(users => applicationService.allForUserIds(users.map(_.id)))
        (usersFuture, applicationsFuture, groupsFuture)
      }

    for {
      users <- usersFuture
      applications <- applicationsFuture
      groups <- groupsFuture
    } yield {
      val applicationsByArea: Map[Area, List[Application]] =
        applications
          .groupBy(_.area)
          .flatMap {
            case (areaId: UUID, applications: Seq[Application]) =>
              Area.all
                .find(area => (area.id: UUID) == (areaId: UUID))
                .map(area => (area, applications))
          }

      val firstDate: ZonedDateTime = if (applications.isEmpty) {
        Time.nowParis()
      } else {
        val weekFieldISO = java.time.temporal.WeekFields.of(java.util.Locale.FRANCE).dayOfWeek()
        applications.map(_.creationDate).min.`with`(weekFieldISO, 1)
      }
      val today = Time.nowParis()
      val months = Time.monthsMap(firstDate, today)
      views.html.helpers.stats(Authorization.isAdmin(request.rights))(
        months,
        applicationsByArea,
        users,
        groups,
        areaIds,
        organisationIds,
        groupIds
      )
    }
  }

  private val statsForm = Form(
    tuple(
      "areas" -> default(list(uuid), List()),
      "organisations" -> default(list(of[Organisation.Id]), List()),
      "groups" -> default(list(uuid), List())
    )
  )

  def stats: Action[AnyContent] = loginAction.async { implicit request =>
    val (areaIds, organisationIds, groupIds) = statsForm.bindFromRequest.get

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
      if (areaIds.isEmpty && organisationIds.isEmpty && groupIds.isEmpty)
        s"${Authorization.isAdmin(request.rights)}.stats.all"
      else if (observableGroupIds.isEmpty)
        s"${Authorization.isAdmin(request.rights)}.stats.${Hash
          .sha256(areaIds.toString() + observableOrganisationIds.toString())}"
      else
        s"${Authorization.isAdmin(request.rights)}.stats.${Hash.sha256(areaIds.toString() + observableOrganisationIds.toString() + observableGroupIds.toString())}"

    cache
      .getOrElseUpdate[Html](cacheKey, 1 hours)(
        generateStats(areaIds, observableOrganisationIds, observableGroupIds)
      )
      .map { html =>
        eventService.log(StatsShowed, "Visualise les stats")
        Ok(
          views.html.stats(request.currentUser, request.rights)(
            html,
            List(),
            areaIds,
            organisationIds,
            groupIds
          )
        )
      }
  }

  def allAs(userId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    val userOption = userService.byId(userId)
    (request.currentUser.admin, userOption) match {
      case (false, Some(user)) =>
        eventService.log(
          AllAsUnauthorized,
          s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur $userId",
          user = Some(user)
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
          user = Some(user)
        )
        Future(
          Unauthorized(
            s"Vous n'avez pas le droit de faire ça avec un compte administrateur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
          )
        )
      case (true, Some(user)) if request.currentUser.areas.intersect(user.areas).nonEmpty =>
        LoginAction.readUserRights(user).map { userRights =>
          val currentUserId = user.id
          val applicationsFromTheArea = List[Application]()
          eventService
            .log(AllAsShowed, s"Visualise la vue de l'utilisateur $userId", user = Some(user))
          // Bug To Fix
          Ok(
            views.html.myApplications(user, userRights)(
              applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin),
              applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin),
              applicationsFromTheArea
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

  def showExportMyApplicationsCSV: Action[AnyContent] = loginAction { implicit request =>
    Ok(views.html.CSVExport(request.currentUser, request.rights))
  }

  private def applicationsToCSV(applications: List[Application]): String = {
    val usersId = applications.flatMap(_.invitedUsers.keys) ++ applications.map(_.creatorUserId)
    val users = userService.byIds(usersId, includeDisabled = true)
    val userGroupIds = users.flatMap(_.groupIds)
    val groups = userGroupService.byIds(userGroupIds)

    def applicationToCSV(application: Application): String = {
      val creatorUser = users.find(_.id == application.creatorUserId)
      val invitedUsers =
        users.filter(user => application.invitedUsers.keys.toList.contains[UUID](user.id))
      val creatorUserGroupNames = creatorUser.toList
        .flatMap(_.groupIds)
        .flatMap { groupId: UUID =>
          groups.filter(group => group.id == groupId)
        }
        .map(_.name)
        .mkString(",")
      val invitedUserGroupNames = invitedUsers
        .flatMap(_.groupIds)
        .distinct
        .flatMap { groupId: UUID =>
          groups.filter(group => group.id == groupId)
        }
        .map(_.name)
        .mkString(",")

      List[String](
        application.id.toString,
        application.status,
        // Precision limited for stats
        Time.formatPatternFr(application.creationDate, "YYY-MM-dd"),
        creatorUserGroupNames,
        invitedUserGroupNames,
        Area.all.find(_.id == application.area).map(_.name).head,
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

  def myCSV: Action[AnyContent] = loginAction { implicit request =>
    val currentDate = Time.nowParis()
    val exportedApplications = applicationService
      .allOpenOrRecentForUserId(request.currentUser.id, request.currentUser.admin, currentDate)

    val date = Time.formatPatternFr(currentDate, "YYY-MM-dd-HH'h'mm")
    val csvContent = applicationsToCSV(exportedApplications)

    eventService.log(MyCSVShowed, s"Visualise le CSV de mes demandes")
    Ok(csvContent)
      .withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-demandes-$date.csv"""")
      .as("text/csv")
  }

  def allCSV(areaId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    val area = if (areaId == Area.allArea.id) None else Area.fromId(areaId)
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

  private def answerForm(currentUser: User) = Form(
    mapping(
      "message" -> nonEmptyText,
      "irrelevant" -> boolean,
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
      "privateToHelpers" -> boolean,
      "signature" -> (
        if (currentUser.sharedAccount)
          nonEmptyText.transform[Option[String]](Some.apply, _.getOrElse(""))
        else ignored(None: Option[String])
      )
    )(AnswerFormData.apply)(AnswerFormData.unapply)
  )

  private def usersWhoCanBeInvitedOn[A](
      application: Application
  )(implicit request: RequestWithUserData[A]): Future[List[User]] =
    (if (request.currentUser.expert) {
       //TODO : This is a temporary feature: enables the expert to invite someone in the currentArea. Will be permitted to every body later.
       userGroupService.byArea(request.currentArea.id).map { groupsOfArea =>
         userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
       }
     } else if (request.currentUser.instructor) {
       userGroupService.byArea(application.area).map { groupsOfArea =>
         userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
       }
     } else if (request.currentUser.helper && application.creatorUserId == request.currentUser.id) {
       Future(userService.byGroupIds(request.currentUser.groupIds).filter(_.helper))
     } else {
       Future(List[User]())
     }).map(
      _.filterNot(user =>
        user.id == request.currentUser.id || application.invitedUsers.contains(user.id)
      )
    )

  def show(id: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    withApplication(id) { application =>
      usersWhoCanBeInvitedOn(application).map { usersWhoCanBeInvited =>
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
            application,
            answerForm(request.currentUser),
            openedTab,
            request.currentArea,
            readSharedAccountUserSignature(request.session)
          )
        )
      }
    }
  }

  def answerFile(applicationId: UUID, answerId: UUID, filename: String): Action[AnyContent] =
    file(applicationId, Some(answerId), filename)

  def applicationFile(applicationId: UUID, filename: String): Action[AnyContent] =
    file(applicationId, None, filename)

  private def file(applicationId: UUID, answerIdOption: Option[UUID], filename: String) =
    loginAction.async { implicit request =>
      withApplication(applicationId) { application: Application =>
        answerIdOption match {
          case Some(answerId) if application.fileCanBeShowed(request.currentUser, answerId) =>
            application.answers.find(_.id == answerId) match {
              case Some(answer) if answer.files.getOrElse(Map.empty).contains(filename) =>
                eventService.log(
                  FileOpened,
                  s"Le fichier de la réponse $answerId sur la demande $applicationId a été ouvert"
                )
                Future(Ok.sendPath(Paths.get(s"$filesPath/ans_$answerId-$filename"), true, {
                  _: Path =>
                    filename
                }))
              case _ =>
                eventService.log(
                  FileNotFound,
                  s"Le fichier de la réponse $answerId sur la demande $applicationId n'existe pas"
                )
                Future(NotFound("Nous n'avons pas trouvé ce fichier"))
            }
          case None if application.fileCanBeShowed(request.currentUser) =>
            if (application.files.contains(filename)) {
              eventService
                .log(FileOpened, s"Le fichier de la demande $applicationId a été ouvert")
              Future(Ok.sendPath(Paths.get(s"$filesPath/app_$applicationId-$filename"), true, {
                _: Path =>
                  filename
              }))
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

  def answer(applicationId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    withApplication(applicationId) { application =>
      val form = answerForm(request.currentUser).bindFromRequest
      val answerId = AttachmentHelper.retrieveOrGenerateAnswerId(form.data)
      val (pendingAttachments, newAttachments) =
        AttachmentHelper.computeStoreAndRemovePendingAndNewAnswerAttachment(
          answerId,
          form.data,
          computeAttachmentsToStore(request),
          filesPath
        )
      form.fold(
        formWithErrors => {
          val error =
            s"Erreur dans le formulaire de réponse (${formWithErrors.errors.map(_.message).mkString(", ")})."
          eventService.log(AnswerNotCreated, s"$error")
          Future(
            Redirect(routes.ApplicationController.show(applicationId).withFragment("answer-error"))
              .flashing("answer-error" -> error, "opened-tab" -> "anwser")
          )
        },
        answerData => {
          val currentAreaId = application.area
          val message: String =
            answerData.signature
              .fold(answerData.message)(signature => answerData.message + "\n\n" + signature)
          val answer = Answer(
            answerId,
            applicationId,
            Time.nowParis(),
            message,
            request.currentUser.id,
            contextualizedUserName(request.currentUser, currentAreaId),
            Map(),
            answerData.privateToHelpers == false,
            answerData.applicationIsDeclaredIrrelevant,
            Some(answerData.infos),
            files = Some(newAttachments ++ pendingAttachments)
          )
          if (applicationService.add(applicationId, answer) == 1) {
            eventService.log(
              AnswerCreated,
              s"La réponse ${answer.id} a été créée sur la demande $applicationId",
              Some(application)
            )
            notificationsService.newAnswer(application, answer)
            Future(
              Redirect(s"${routes.ApplicationController.show(applicationId)}#answer-${answer.id}")
                .withSession(
                  answerData.signature.fold(removeSharedAccountUserSignature(request.session))(
                    signature => saveSharedAccountUserSignature(request.session, signature)
                  )
                )
                .flashing("success" -> "Votre réponse a bien été envoyée")
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
      "users" -> list(uuid).verifying("Vous devez inviter au moins une personne", _.nonEmpty),
      "privateToHelpers" -> boolean
    )(InvitationData.apply)(InvitationData.unapply)
  )

  def invite(applicationId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    withApplication(applicationId) { application =>
      inviteForm.bindFromRequest.fold(
        formWithErrors => {
          val error =
            s"Erreur dans le formulaire d'invitation (${formWithErrors.errors.map(_.message).mkString(", ")})."
          eventService.log(InviteNotCreated, error)
          Future(
            Redirect(routes.ApplicationController.show(applicationId).withFragment("answer-error"))
              .flashing("answer-error" -> error, "opened-tab" -> "invite")
          )
        },
        inviteData => {
          val currentAreaId = application.area
          usersWhoCanBeInvitedOn(application).map {
            usersWhoCanBeInvited =>
              val invitedUsers: Map[UUID, String] = usersWhoCanBeInvited
                .filter(user => inviteData.invitedUsers.contains[UUID](user.id))
                .map(user => (user.id, contextualizedUserName(user, currentAreaId)))
                .toMap

              val answer = Answer(
                UUID.randomUUID(),
                applicationId,
                Time.nowParis(),
                inviteData.message,
                request.currentUser.id,
                contextualizedUserName(request.currentUser, currentAreaId),
                invitedUsers,
                not(inviteData.privateToHelpers),
                false,
                Some(Map.empty)
              )

              if (applicationService.add(applicationId, answer) == 1) {
                notificationsService.newAnswer(application, answer)
                eventService.log(
                  AgentsAdded,
                  s"L'ajout d'utilisateur ${answer.id} a été créé sur la demande $applicationId",
                  Some(application)
                )
                Redirect(routes.ApplicationController.myApplications())
                  .flashing("success" -> "Les utilisateurs ont été invités sur la demande")
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
      )
    }
  }

  def inviteExpert(applicationId: UUID): Action[AnyContent] = loginAction.async {
    implicit request =>
      withApplication(applicationId) { application: Application =>
        val currentAreaId = application.area
        if (application.canHaveExpertsInvitedBy(request.currentUser)) {
          val experts: Map[UUID, String] = User.admins
            .filter(_.expert)
            .map(user => user.id -> contextualizedUserName(user, currentAreaId))
            .toMap
          val answer = Answer(
            UUID.randomUUID(),
            applicationId,
            Time.nowParis(),
            "J'ajoute un expert",
            request.currentUser.id,
            contextualizedUserName(request.currentUser, currentAreaId),
            experts,
            true,
            false,
            Some(Map())
          )
          if (applicationService.add(applicationId, answer, true) == 1) {
            notificationsService.newAnswer(application, answer)
            eventService.log(
              AddExpertCreated,
              s"La réponse ${answer.id} a été créée sur la demande $applicationId",
              Some(application)
            )
            Future(
              Redirect(routes.ApplicationController.myApplications())
                .flashing("success" -> "Un expert a été invité sur la demande")
            )
          } else {
            eventService.log(
              AddExpertNotCreated,
              s"L'invitation d'experts ${answer.id} n'a pas été créée sur la demande $applicationId : problème BDD",
              Some(application)
            )
            Future(InternalServerError("L'expert n'a pas pu être invité"))
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

  def terminate(applicationId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    withApplication(applicationId) { application: Application =>
      request.getQueryString("usefulness") match {
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
          val finalUsefulness = if (request.currentUser.id == application.creatorUserId) {
            Some(usefulness)
          } else {
            None
          }
          if (application.canBeClosedBy(request.currentUser)) {
            if (applicationService
                  .close(applicationId, finalUsefulness, Time.nowParis())) {
              eventService
                .log(
                  TerminateCompleted,
                  s"La demande $applicationId est clôturée",
                  Some(application)
                )
              val successMessage =
                s"""|La demande "${application.subject}" a bien été clôturée. 
                    |Bravo et merci pour la résolution de cette demande !""".stripMargin
              Future(
                Redirect(routes.ApplicationController.myApplications())
                  .flashing("success" -> successMessage)
              )
            } else {
              eventService.log(
                TerminateError,
                s"La demande $applicationId n'a pas pu être clôturée en BDD",
                Some(application)
              )
              Future(
                InternalServerError(
                  "Erreur interne: l'application n'a pas pu être indiquée comme clôturée"
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
              Unauthorized("Seul le créateur de la demande ou un expert peut clore la demande")
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
    if (signature.size <= 1000)
      session + (sharedAccountUserSignatureKey -> signature)
    else
      session

  private def removeSharedAccountUserSignature(session: Session): Session =
    session - sharedAccountUserSignatureKey

}
