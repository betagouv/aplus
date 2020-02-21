package controllers

import java.nio.file.{Files, Path, Paths}
import java.util.{Locale, UUID}

import actions._
import constants.Constants
import helper.Time.dateTimeOrdering
import forms.FormsPlusMap
import helper.Time
import javax.inject.{Inject, Singleton}
import models._
import org.joda.time.DateTime
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
  AddExpertNotFound,
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
  ApplicationCreationUnauthorized,
  ApplicationFormShowed,
  ApplicationNotFound,
  ApplicationShowed,
  ApplicationUnauthorized,
  FileNotFound,
  FileOpened,
  FileUnauthorized,
  InviteNotCreated,
  MyApplicationsShowed,
  MyCSVShowed,
  StatsIncorrectSetup,
  StatsShowed,
  StatsUnauthorized,
  TerminateCompleted,
  TerminateError,
  TerminateIncompleted,
  TerminateNotFound,
  TerminateUnauthorized
}
import play.api.cache.AsyncCacheApi
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
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
  import formModels._

  private implicit val timeZone = Time.dateTimeZone

  private val filesPath = configuration.underlying.getString("app.filesPath")

  private val dir = Paths.get(s"$filesPath")
  if (!Files.isDirectory(dir)) {
    Files.createDirectories(dir)
  }

  val applicationForm = Form(
    mapping(
      "subject" -> nonEmptyText.verifying(maxLength(150)),
      "description" -> nonEmptyText,
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
      "users" -> list(uuid).verifying("Vous devez sélectionner au moins une structure", _.nonEmpty),
      "organismes" -> list(text),
      "category" -> optional(text),
      "selected-subject" -> optional(text)
    )(ApplicationData.apply)(ApplicationData.unapply)
  )

  private def fetchGroupsWithInstructors(
      areaId: UUID,
      currentUser: User
  ): (List[UserGroup], List[User], List[User]) = {
    val groupsOfArea = userGroupService.byArea(areaId)
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

  def create = loginAction { implicit request =>
    eventService.log(ApplicationFormShowed, "Visualise le formulaire de création de demande")
    val (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =
      fetchGroupsWithInstructors(request.currentArea.id, request.currentUser)
    Ok(
      views.html.createApplication(request.currentUser, request.currentArea)(
        instructorsOfGroups,
        groupsOfAreaWithInstructor,
        coworkers,
        applicationForm
      )
    )
  }

  def createSimplified = loginAction { implicit request =>
    eventService
      .log(ApplicationFormShowed, "Visualise le formulaire simplifié de création de demande")
    val (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =
      fetchGroupsWithInstructors(request.currentArea.id, request.currentUser)
    val groupsOfAreaWithInstructorWithOrganisationSet = groupsOfAreaWithInstructor.filter({
      userGroup =>
        userGroup.organisationSetOrDeducted.nonEmpty
    })
    val categories = organisationService.categories
    Ok(
      views.html.simplifiedCreateApplication(request.currentUser, request.currentArea)(
        instructorsOfGroups,
        groupsOfAreaWithInstructorWithOrganisationSet,
        coworkers,
        categories,
        None,
        applicationForm
      )
    )
  }

  def createPost = createPostBis(false)

  def createSimplifiedPost = createPostBis(true)

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

  private def createPostBis(simplified: Boolean) = loginAction { implicit request =>
    val form = applicationForm.bindFromRequest
    val applicationId = AttachmentHelper.retrieveOrGenerateApplicationId(form.data)
    val (pendingAttachments, newAttachments) =
      AttachmentHelper.computeStoreAndRemovePendingAndNewApplicationAttachment(
        applicationId,
        form.data,
        computeAttachmentsToStore(request),
        filesPath
      )
    form.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        val (groupsOfAreaWithInstructor, instructorsOfGroups, coworkers) =
          fetchGroupsWithInstructors(request.currentArea.id, request.currentUser)
        eventService.log(
          ApplicationCreationInvalid,
          s"L'utilisateur essaie de créer une demande invalide ${formWithErrors.errors.map(_.message)}"
        )

        if (simplified) {
          val categories = organisationService.categories
          val groupsOfAreaWithInstructorWithOrganisationSet =
            groupsOfAreaWithInstructor.filter(_.organisationSetOrDeducted.nonEmpty)
          BadRequest(
            views.html.simplifiedCreateApplication(request.currentUser, request.currentArea)(
              instructorsOfGroups,
              groupsOfAreaWithInstructorWithOrganisationSet,
              coworkers,
              categories,
              formWithErrors("category").value,
              formWithErrors,
              pendingAttachments.keys ++ newAttachments.keys
            )
          )
        } else {
          BadRequest(
            views.html.createApplication(request.currentUser, request.currentArea)(
              instructorsOfGroups,
              groupsOfAreaWithInstructor,
              coworkers,
              formWithErrors,
              pendingAttachments.keys ++ newAttachments.keys
            )
          )
        }
      },
      applicationData => {
        // Note: we will deprecate .currentArea as a variable stored in the cookies
        val currentAreaId: UUID = request.currentArea.id
        val invitedUsers: Map[UUID, String] = applicationData.users.flatMap { id =>
          userService.byId(id).map(user => id -> contextualizedUserName(user, currentAreaId))
        }.toMap

        val application = Application(
          applicationId,
          DateTime.now(timeZone),
          contextualizedUserName(request.currentUser, currentAreaId),
          request.currentUser.id,
          applicationData.subject,
          applicationData.description,
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

  def allApplicationVisibleByUserAdmin(user: User, areaOption: Option[Area]) =
    (user.admin, areaOption) match {
      case (true, None) =>
        applicationService.allForAreas(user.areas, true)
      case (true, Some(area)) =>
        applicationService.allForAreas(List(area.id), true)
      case (false, None) if user.groupAdmin =>
        val userIds = userService.byGroupIds(user.groupIds).map(_.id)
        applicationService.allForUserIds(userIds, true)
      case (false, Some(area)) if user.groupAdmin =>
        val userGroupIds =
          userGroupService.byIds(user.groupIds).filter(_.areaIds.contains[UUID](area.id)).map(_.id)
        val userIds = userService.byGroupIds(userGroupIds).map(_.id)
        applicationService.allForUserIds(userIds, true)
      case _ =>
        List()
    }

  def all(areaId: UUID) = loginAction { implicit request =>
    (request.currentUser.admin, request.currentUser.groupAdmin) match {
      case (false, false) =>
        eventService.log(
          AllApplicationsUnauthorized,
          "L'utilisateur n'a pas de droit d'afficher toutes les demandes"
        )
        Unauthorized(
          "Vous n'avez pas les droits suffisants pour voir les statistiques. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
        )
      case _ =>
        val area = if (areaId == Area.allArea.id) None else Area.fromId(areaId)
        val applications = allApplicationVisibleByUserAdmin(request.currentUser, area)
        eventService.log(
          AllApplicationsShowed,
          s"Visualise la liste des applications de $areaId - taille = ${applications.size}"
        )
        Ok(
          views.html
            .allApplications(request.currentUser)(applications, area.getOrElse(Area.allArea))
        )
    }
  }

  def myApplications = loginAction { implicit request =>
    val myApplications = applicationService.allOpenOrRecentForUserId(
      request.currentUser.id,
      request.currentUser.admin,
      DateTime.now(Time.dateTimeZone)
    )
    val myOpenApplications = myApplications.filter(!_.closed)
    val myClosedApplications = myApplications.filter(_.closed)

    eventService.log(
      MyApplicationsShowed,
      s"Visualise la liste des applications : open=${myOpenApplications.size}/closed=${myClosedApplications.size}"
    )
    Ok(views.html.myApplications(request.currentUser)(myOpenApplications, myClosedApplications))
  }

  private def generateStats(currentUser: User, selectedArea: Area, restrictToSelectedArea: Boolean)(
      implicit webJarsUtil: org.webjars.play.WebJarsUtil,
      flash: Flash,
      request: RequestHeader
  ): Html = {
    // We prefilter `byAreaId` when `currentUser.admin` because an admin is not necessarily
    // admin on all the areas
    // An admin is implicitly in all groups
    val (users, applications): (List[User], List[Application]) =
      if (currentUser.admin) {
        if (restrictToSelectedArea) {
          (
            userService.byAreaIds(List(selectedArea.id)),
            applicationService.allForAreas(List(selectedArea.id), true)
          )
        } else {
          val adminAreaIds: List[UUID] = currentUser.areas
          (
            userService.byAreaIds(adminAreaIds),
            applicationService.allForAreas(adminAreaIds, true)
          )
        }
      } else {
        if (restrictToSelectedArea) {
          val userGroups: List[UserGroup] = userGroupService.byIds(currentUser.groupIds)
          val areaGroups = userGroups.filter(_.areaIds.contains[UUID](selectedArea.id))
          val areaGroupsUsers = userService.byGroupIds(areaGroups.map(_.id))
          (
            areaGroupsUsers,
            applicationService
              .allForUserIds(areaGroupsUsers.map(_.id), true)
              .filter(application => (application.area: UUID) == (selectedArea.id: UUID))
          )
        } else {
          val sameGroupsUsers = userService.byGroupIds(currentUser.groupIds)
          (
            sameGroupsUsers,
            applicationService.allForUserIds(sameGroupsUsers.map(_.id), true)
          )
        }
      }

    val applicationsByArea: Map[Area, List[Application]] =
      applications
        .groupBy(_.area)
        .flatMap {
          case (areaId: UUID, applications: Seq[Application]) =>
            Area.all
              .find(area => (area.id: UUID) == (areaId: UUID))
              .map(area => (area, applications))
        }

    val firstDate = if (applications.isEmpty) {
      DateTime.now()
    } else {
      applications.map(_.creationDate).min.weekOfWeekyear().roundFloorCopy()
    }
    val today = DateTime.now(timeZone)
    val months = Time.monthsMap(firstDate, today)
    views.html.stats(currentUser, selectedArea)(
      months,
      applicationsByArea,
      users,
      restrictToSelectedArea
    )(webJarsUtil, flash, request)
  }

  def stats = loginAction.async { implicit request =>
    val selectedAreaOnly: Boolean =
      request.getQueryString("currentAreaOnly").map(_.toBoolean).getOrElse(false)
    // Note: this is deprecated
    val selectedArea = request.currentArea

    val cacheKey =
      if (selectedAreaOnly)
        s"stats.user_${request.currentUser.id}.area_${selectedArea.id}"
      else
        s"stats.user_${request.currentUser.id}"

    cache
      .getOrElseUpdate[Html](cacheKey, 1 hours)(
        Future(generateStats(request.currentUser, selectedArea, selectedAreaOnly))
      )
      .map { html =>
        eventService.log(StatsShowed, "Visualise les stats")
        Ok(html)
      }
  }

  def allAs(userId: UUID) = loginAction { implicit request =>
    val userOption = userService.byId(userId)
    (request.currentUser.admin, userOption) match {
      case (false, Some(user)) =>
        eventService.log(
          AllAsUnauthorized,
          s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur $userId",
          user = Some(user)
        )
        Unauthorized(
          "Vous n'avez pas le droit de faire ça, vous n'êtes pas administrateur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
        )
      case (true, Some(user)) if user.admin =>
        eventService.log(
          AllAsUnauthorized,
          s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur admin $userId",
          user = Some(user)
        )
        Unauthorized(
          "Vous n'avez pas le droit de faire ça avec un compte administrateur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
        )
      case (true, Some(user)) if request.currentUser.areas.intersect(user.areas).nonEmpty =>
        val currentUserId = user.id
        val applicationsFromTheArea = List[Application]()
        eventService
          .log(AllAsShowed, s"Visualise la vue de l'utilisateur $userId", user = Some(user))
        // Bug To Fix
        Ok(
          views.html.myApplications(user)(
            applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin),
            applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin),
            applicationsFromTheArea
          )
        )
      case _ =>
        eventService.log(AllAsNotFound, s"L'utilisateur $userId n'existe pas")
        BadRequest(
          "L'utilisateur n'existe pas ou vous n'avez pas le droit d'accéder à cette page. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
        )
    }
  }

  def showExportMyApplicationsCSV = loginAction { implicit request =>
    Ok(views.html.CSVExport(request.currentUser))
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
        application.creationDate.toString("YYY-MM-dd"), // Precision limited for stats,
        creatorUserGroupNames,
        invitedUserGroupNames,
        Area.all.find(_.id == application.area).map(_.name).head,
        application.closedDate.map(_.toString("YYY-MM-dd")).getOrElse(""),
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

  def myCSV = loginAction { implicit request =>
    val currentDate = DateTime.now(timeZone)
    val exportedApplications = applicationService
      .allOpenOrRecentForUserId(request.currentUser.id, request.currentUser.admin, currentDate)

    val date = currentDate.toString("YYY-MM-dd-HH'h'mm", new Locale("fr"))
    val csvContent = applicationsToCSV(exportedApplications)

    eventService.log(MyCSVShowed, s"Visualise le CSV de mes demandes")
    Ok(csvContent)
      .withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-demandes-$date.csv"""")
      .as("text/csv")
  }

  def allCSV(areaId: UUID) = loginAction { implicit request =>
    val area = if (areaId == Area.allArea.id) None else Area.fromId(areaId)
    val exportedApplications = if (request.currentUser.admin || request.currentUser.groupAdmin) {
      allApplicationVisibleByUserAdmin(request.currentUser, area)
    } else {
      List()
    }

    val date = DateTime.now(Time.dateTimeZone).toString("YYY-MM-dd-HH'h'mm", new Locale("fr"))
    val csvContent = applicationsToCSV(exportedApplications)

    eventService.log(AllCSVShowed, s"Visualise un CSV pour la zone ${area}")
    Ok(csvContent)
      .withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-demandes-$date-${area
        .map(_.name.stripSpecialChars)
        .getOrElse("tous")}.csv"""")
      .as("text/csv")
  }

  val answerForm = Form(
    mapping(
      "message" -> nonEmptyText,
      "irrelevant" -> boolean,
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
      "privateToHelpers" -> boolean
    )(AnswerData.apply)(AnswerData.unapply)
  )

  def usersThatCanBeInvitedOn[A](
      application: Application
  )(implicit request: RequestWithUserData[A]) =
    (if (request.currentUser.instructor || request.currentUser.expert) {
       val groupsOfArea = userGroupService.byArea(application.area)
       userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
     } else if (request.currentUser.helper && application.creatorUserId == request.currentUser.id) {
       userService.byGroupIds(request.currentUser.groupIds).filter(_.helper)
     } else {
       List[User]()
     }).filterNot(user =>
      user.id == request.currentUser.id || application.invitedUsers.contains(user.id)
    )

  def show(id: UUID) = loginAction { implicit request =>
    applicationService.byId(id, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.log(ApplicationNotFound, s"La demande $id n'existe pas")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        if (application.canBeShowedBy(request.currentUser)) {
          val usersThatCanBeInvited = usersThatCanBeInvitedOn(application)
          val groups = userGroupService
            .byIds(usersThatCanBeInvited.flatMap(_.groupIds))
            .filter(_.areaIds.contains[UUID](application.area))
          val groupsWithUsersThatCanBeInvited = groups.map { group =>
            group -> usersThatCanBeInvited.filter(_.groupIds.contains[UUID](group.id))
          }
          val renderedApplication =
            if ((application
                  .haveUserInvitedOn(request.currentUser) || request.currentUser.id == application.creatorUserId) && request.currentUser.expert && request.currentUser.admin && !application.closed) {
              // If user is expert, admin and invited to the application we desanonymate
              applicationService.byId(id, request.currentUser.id, false).get
            } else {
              application
            }
          val openedTab = request.flash.get("opened-tab").getOrElse("answer")

          eventService.log(ApplicationShowed, s"Demande $id consultée", Some(application))
          Ok(
            views.html.showApplication(request.currentUser)(
              groupsWithUsersThatCanBeInvited,
              renderedApplication,
              answerForm,
              openedTab,
              request.currentArea
            )
          )
        } else {
          eventService.log(
            ApplicationUnauthorized,
            s"L'accès à la demande $id n'est pas autorisé",
            Some(application)
          )
          Unauthorized(
            s"Vous n'avez pas les droits suffisants pour voir cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
          )
        }
    }
  }

  def answerFile(applicationId: UUID, answerId: UUID, filename: String) =
    file(applicationId, Some(answerId), filename)

  def applicationFile(applicationId: UUID, filename: String) = file(applicationId, None, filename)

  def file(applicationId: UUID, answerIdOption: Option[UUID], filename: String) = loginAction {
    implicit request =>
      (
        answerIdOption,
        applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin)
      ) match {
        case (_, None) =>
          eventService.log(ApplicationNotFound, s"La demande $applicationId n'existe pas")
          NotFound("Nous n'avons pas trouvé ce fichier")
        case (Some(answerId), Some(application))
            if application.fileCanBeShowed(request.currentUser, answerId) =>
          application.answers.find(_.id == answerId) match {
            case Some(answer) if answer.files.getOrElse(Map.empty).contains(filename) =>
              eventService.log(
                FileOpened,
                s"Le fichier de la réponse $answerId sur la demande $applicationId a été ouvert"
              )
              Ok.sendPath(Paths.get(s"$filesPath/ans_$answerId-$filename"), true, { _: Path =>
                filename
              })
            case _ =>
              eventService.log(
                FileNotFound,
                s"Le fichier de la réponse $answerId sur la demande $applicationId n'existe pas"
              )
              NotFound("Nous n'avons pas trouvé ce fichier")
          }
        case (None, Some(application)) if application.fileCanBeShowed(request.currentUser) =>
          if (application.files.contains(filename)) {
            eventService.log(FileOpened, s"Le fichier de la demande $applicationId a été ouvert")
            Ok.sendPath(Paths.get(s"$filesPath/app_$applicationId-$filename"), true, { _: Path =>
              filename
            })
          } else {
            eventService.log(
              FileNotFound,
              s"Le fichier de la demande $application sur la demande $applicationId n'existe pas"
            )
            NotFound("Nous n'avons pas trouvé ce fichier")
          }
        case (_, Some(application)) =>
          eventService.log(
            FileUnauthorized,
            s"L'accès aux fichiers sur la demande $applicationId n'est pas autorisé",
            Some(application)
          )
          Unauthorized(
            "Vous n'avez pas les droits suffisants pour voir les fichiers sur cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
          )
      }
  }

  def answer(applicationId: UUID) = loginAction { implicit request =>
    withApplication(applicationId) { application =>
      val form = answerForm.bindFromRequest
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
          Redirect(routes.ApplicationController.show(applicationId).withFragment("answer-error"))
            .flashing("answer-error" -> error, "opened-tab" -> "anwser")
        },
        answerData => {
          val currentAreaId = application.area
          val answer = Answer(
            answerId,
            applicationId,
            DateTime.now(timeZone),
            answerData.message,
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
            Redirect(s"${routes.ApplicationController.show(applicationId)}#answer-${answer.id}")
              .flashing("success" -> "Votre réponse a bien été envoyée")
          } else {
            eventService.log(
              AnswerNotCreated,
              s"La réponse ${answer.id} n'a pas été créée sur la demande $applicationId : problème BDD",
              Some(application)
            )
            InternalServerError("Votre réponse n'a pas pu être envoyée")
          }
        }
      )
    }
  }

  val inviteForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid).verifying("Vous devez inviter au moins une personne", _.nonEmpty),
      "privateToHelpers" -> boolean
    )(InvitationData.apply)(InvitationData.unapply)
  )

  def invite(applicationId: UUID) = loginAction { implicit request =>
    withApplication(applicationId) { application =>
      inviteForm.bindFromRequest.fold(
        formWithErrors => {
          val error =
            s"Erreur dans le formulaire d'invitation (${formWithErrors.errors.map(_.message).mkString(", ")})."
          eventService.log(InviteNotCreated, error)
          Redirect(routes.ApplicationController.show(applicationId).withFragment("answer-error"))
            .flashing("answer-error" -> error, "opened-tab" -> "invite")
        },
        inviteData => {
          val currentAreaId = application.area
          val usersThatCanBeInvited = usersThatCanBeInvitedOn(application)
          val invitedUsers: Map[UUID, String] = usersThatCanBeInvited
            .filter(user => inviteData.invitedUsers.contains[UUID](user.id))
            .map(user => (user.id, contextualizedUserName(user, currentAreaId)))
            .toMap

          val answer = Answer(
            UUID.randomUUID(),
            applicationId,
            DateTime.now(timeZone),
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
      )
    }
  }

  def inviteExpert(applicationId: UUID) = loginAction { implicit request =>
    applicationService
      .byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService
          .log(AddExpertNotFound, s"La demande $applicationId n'existe pas pour ajouter un expert")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val currentAreaId = application.area
        if (application.canHaveExpertsInvitedBy(request.currentUser)) {
          val experts: Map[UUID, String] = User.admins
            .filter(_.expert)
            .map(user => user.id -> contextualizedUserName(user, currentAreaId))
            .toMap
          val answer = Answer(
            UUID.randomUUID(),
            applicationId,
            DateTime.now(timeZone),
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
            Redirect(routes.ApplicationController.myApplications())
              .flashing("success" -> "Un expert a été invité sur la demande")
          } else {
            eventService.log(
              AddExpertNotCreated,
              s"L'invitation d'experts ${answer.id} n'a pas été créée sur la demande $applicationId : problème BDD",
              Some(application)
            )
            InternalServerError("L'expert n'a pas pu être invité")
          }
        } else {
          eventService.log(
            AddExpertUnauthorized,
            s"L'invitation d'experts pour la demande $applicationId n'est pas autorisée",
            Some(application)
          )
          Unauthorized(
            "Vous n'avez pas les droits suffisants pour inviter des agents à cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
          )
        }
    }
  }

  def terminate(applicationId: UUID) = loginAction { implicit request =>
    (
      request.getQueryString("usefulness"),
      applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin)
    ) match {
      case (_, None) =>
        eventService
          .log(TerminateNotFound, s"La demande $applicationId n'existe pas pour la clôturer")
        NotFound("Nous n'avons pas trouvé cette demande.")
      case (None, _) =>
        eventService
          .log(TerminateIncompleted, s"La demande de clôture pour $applicationId est incomplète")
        BadGateway(
          "L'utilité de la demande n'est pas présente, il s'agit sûrement d'une erreur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}"
        )
      case (Some(usefulness), Some(application)) =>
        val finalUsefulness = if (request.currentUser.id == application.creatorUserId) {
          Some(usefulness)
        } else {
          None
        }
        if (application.canBeClosedBy(request.currentUser)) {
          if (applicationService.close(applicationId, finalUsefulness, DateTime.now(timeZone))) {
            eventService
              .log(TerminateCompleted, s"La demande $applicationId est clôturée", Some(application))
            Redirect(routes.ApplicationController.myApplications())
              .flashing("success" -> "L'application a été indiquée comme clôturée")
          } else {
            eventService.log(
              TerminateError,
              s"La demande $applicationId n'a pas pu être clôturée en BDD",
              Some(application)
            )
            InternalServerError(
              "Erreur interne: l'application n'a pas pu être indiquée comme clôturée"
            )
          }
        } else {
          eventService.log(
            TerminateUnauthorized,
            s"L'utilisateur n'a pas le droit de clôturer la demande $applicationId",
            Some(application)
          )
          Unauthorized("Seul le créateur de la demande ou un expert peut clore la demande")
        }
    }
  }
}
