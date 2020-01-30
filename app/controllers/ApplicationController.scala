package controllers

import java.nio.file.{Files, Path, Paths}
import java.util.{Locale, UUID}

import actions._
import constants.Constants
import extentions.Time
import extentions.Time.dateTimeOrdering
import forms.FormsPlusMap
import helper.AttachmentHelper
import javax.inject.{Inject, Singleton}
import models._
import org.joda.time.DateTime
import org.webjars.play.WebJarsUtil
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.mvc._
import services._
import extentions.BooleanHelper.not
import models.EventType.{AddExpertCreated, AddExpertNotCreated, AddExpertNotFound, AddExpertUnauthorized, AgentsAdded, AgentsNotAdded, AllApplicationsShowed, AllApplicationsUnauthorized, AllAsNotFound, AllAsShowed, AllAsUnauthorized, AllCSVShowed, AnswerCreated, AnswerNotCreated, ApplicationCreated, ApplicationCreationError, ApplicationCreationInvalid, ApplicationCreationUnauthorized, ApplicationFormShowed, ApplicationNotFound, ApplicationShowed, ApplicationUnauthorized, FileNotFound, FileOpened, FileUnauthorized, InviteNotCreated, MyApplicationsShowed, MyCSVShowed, StatsIncorrectSetup, StatsShowed, StatsUnauthorized, TerminateCompleted, TerminateError, TerminateIncompleted, TerminateNotFound, TerminateUnauthorized}

import scala.concurrent.ExecutionContext

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
case class ApplicationController @Inject()(loginAction: LoginAction,
                                      userService: UserService,
                                      applicationService: ApplicationService,
                                      notificationsService: NotificationService,
                                      eventService: EventService,
                                      organisationService: OrganisationService,
                                      userGroupService: UserGroupService,
                                      configuration: play.api.Configuration
                                     )(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil
                                      ) extends InjectedController with play.api.i18n.I18nSupport with extentions.Operators.ApplicationOperators {
  import forms.Models._

  private implicit val timeZone = Time.dateTimeZone

  private val filesPath = configuration.underlying.getString("app.filesPath")

  private val dir = Paths.get(s"$filesPath")
  if(!Files.isDirectory(dir)) {
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

  def create = loginAction { implicit request =>
    eventService.log(ApplicationFormShowed, "Visualise le formulaire de création de demande")
    val groupsOfArea = userGroupService.byArea(request.currentArea.id)
    val instructorsOfGroups = userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
    Ok(views.html.createApplication(request.currentUser,request.currentArea)(instructorsOfGroups, groupsOfArea, applicationForm))
  }

  def createSimplified = loginAction { implicit request =>
    eventService.log(ApplicationFormShowed, "Visualise le formulaire simplifié de création de demande")
    val groupsOfArea = userGroupService.byArea(request.currentArea.id)
    val instructorsOfGroups = userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
    val organismeGroups = groupsOfArea.filter({ userGroup => userGroup.organisationSetOrDeducted.nonEmpty })
    val categories = organisationService.categories
    Ok(views.html.simplifiedCreateApplication(request.currentUser, request.currentArea)(instructorsOfGroups, organismeGroups, categories, None, applicationForm))
  }

  def createPost = createPostBis(false)

  def createSimplifiedPost = createPostBis(true)


  private def createPostBis(simplified: Boolean) = loginAction { implicit request =>
    request.currentUser.helper match {
       case false => {
         eventService.log(ApplicationCreationUnauthorized, s"L'utilisateur n'a pas de droit de créer une demande")
         Unauthorized(s"Vous n'avez pas les droits suffisants pour créer une demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
       }
       case true => {
         val form = applicationForm.bindFromRequest
         val applicationId = AttachmentHelper.retrieveOrGenerateApplicationId(form.data)
         val (pendingAttachments, newAttachments) = AttachmentHelper.computeStoreAndRemovePendingAndNewApplicationAttachment(applicationId,
           form.data,
           computeAttachmentsToStore(request),
           filesPath)
         form.fold(
           formWithErrors => {
             // binding failure, you retrieve the form containing errors:
             val groupsOfArea = userGroupService.byArea(request.currentArea.id)
             val instructors = userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
             eventService.log(ApplicationCreationInvalid, s"L'utilisateur essai de créé une demande invalide ${formWithErrors.errors.map(_.message)}")
             val groupIds = instructors.flatMap(_.groupIds).distinct

             val formWithErrorsfinal = if(request.body.asMultipartFormData.flatMap(_.file("file")).isEmpty) {
               formWithErrors
             } else {
               formWithErrors.copy(
                 errors = formWithErrors.errors :+ FormError("file", "Vous aviez ajouté un fichier, il n'a pas pu être sauvegardé, vous devez le remettre.")
               )
             }
             if(simplified) {
               val categories = organisationService.categories
               val organismeGroups = userGroupService.byIds(groupIds).filter(userGroup => userGroup.organisationSetOrDeducted.nonEmpty && userGroup.areaIds == request.currentArea.id)
               BadRequest(views.html.simplifiedCreateApplication(request.currentUser, request.currentArea)(instructors, organismeGroups, categories, formWithErrors("category").value, formWithErrors, pendingAttachments.keys ++ newAttachments.keys))
             } else {
               val organismeGroups = userGroupService.byIds(groupIds).filter(_.areaIds.contains(request.currentArea.id))
               BadRequest(views.html.createApplication(request.currentUser, request.currentArea)(instructors, organismeGroups, formWithErrorsfinal, pendingAttachments.keys ++ newAttachments.keys))
             }
           },
           applicationData => {
             val invitedUsers: Map[UUID, String] = applicationData.users.flatMap {  id =>
               userService.byId(id).map(user => id -> userGroupService.contextualizedUserName(user))
             }.toMap

             val application = Application(applicationId,
               DateTime.now(timeZone),
               userGroupService.contextualizedUserName(request.currentUser),
               request.currentUser.id,
               applicationData.subject,
               applicationData.description,
               applicationData.infos,
               invitedUsers,
               request.currentArea.id,
               false,
               hasSelectedSubject = applicationData.selectedSubject.contains(applicationData.subject),
               category = applicationData.category,
               files = newAttachments ++ pendingAttachments)
             if(applicationService.createApplication(application)) {
               notificationsService.newApplication(application)
               eventService.log(ApplicationCreated, s"La demande ${application.id} a été créé", Some(application))
               Redirect(routes.ApplicationController.myApplications()).flashing("success" -> "Votre demande a bien été envoyée")
             }  else {
               eventService.log(ApplicationCreationError, s"La demande ${application.id} n'a pas pu être créé", Some(application))
               InternalServerError("Error Interne: Votre demande n'a pas pu être envoyé. Merci de rééssayer ou contacter l'administrateur")
             }
           }
         )
       }
    }
  }

  private def computeAttachmentsToStore(request: RequestWithUserData[AnyContent]): Iterable[(Path, String)] = {
    request
      .body
      .asMultipartFormData
      .map(_.files.filter(_.key.matches("file\\[\\d+\\]")))
      .getOrElse(Nil)
      .flatMap({ attachment =>
        if (attachment.filename.isEmpty) None
        else Some(attachment.ref.path -> attachment.filename)
      })
  }

  def allApplicationVisibleByUserAdmin(user: User, area: Area) = user.admin match {
    case true if area.id == Area.allArea.id =>
      applicationService.allForAreas(user.areas, true)
    case true =>
      applicationService.allForAreas(List(area.id), true)
    case false if user.groupAdmin && area.id == Area.allArea.id =>
      val userIds = userService.byGroupIds(user.groupIds).map(_.id)
      applicationService.allForUserIds(userIds, true)
    case false if user.groupAdmin =>
      val userGroupIds = userGroupService.byIds(user.groupIds).filter(_.areaIds.contains(area.id)).map(_.id)
      val userIds = userService.byGroupIds(userGroupIds).map(_.id)
      applicationService.allForUserIds(userIds, true)
    case _ =>
      List()
  }

  def all(areaId: UUID) = loginAction { implicit request =>
    (request.currentUser.admin, request.currentUser.groupAdmin) match {
      case (false, false) =>
        eventService.log(AllApplicationsUnauthorized, "L'utilisateur n'a pas de droit d'afficher toutes les demandes")
        Unauthorized("Vous n'avez pas les droits suffisants pour voir les statistiques. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
      case _ =>
        val area = Area.fromId(areaId).get
        val applications = allApplicationVisibleByUserAdmin(request.currentUser, area)
        eventService.log(AllApplicationsShowed,
          s"Visualise la liste des applications de $areaId - taille = ${applications.size}")
        Ok(views.html.allApplications(request.currentUser)(applications, area))
    }
  }



  def myApplications = loginAction { implicit request =>
    val myApplications = applicationService.allOpenOrRecentForUserId(request.currentUser.id, request.currentUser.admin, DateTime.now(Time.dateTimeZone))
    val myOpenApplications = myApplications.filter(!_.closed)
    val myClosedApplications = myApplications.filter(_.closed)

    eventService.log(MyApplicationsShowed,
      s"Visualise la liste des applications : open=${myOpenApplications.size}/closed=${myClosedApplications.size}")
    Ok(views.html.myApplications(request.currentUser)(myOpenApplications, myClosedApplications))
  }


  def stats = loginAction { implicit request =>
    (request.currentUser.admin || request.currentUser.groupAdmin) match {
      case false =>
        eventService.log(StatsUnauthorized, "L'utilisateur n'a pas de droit d'afficher les stats")
        Unauthorized("Vous n'avez pas les droits suffisants pour voir les statistiques. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
      case true =>
        val users = if(request.currentUser.admin) {
          userService.all
        } else if(request.currentUser.groupAdmin) {
          userService.byGroupIds(request.currentUser.groupIds)
        } else {
          eventService.log(StatsIncorrectSetup, "Erreur d'accès aux utilisateurs pour les stats")
          List()
        }

        val allApplications = if(request.currentUser.admin) {
          applicationService.allForAreas(request.currentUser.areas, true)
        } else if(request.currentUser.groupAdmin) {
          applicationService.allForUserIds(users.map(_.id), true)
        } else {
          eventService.log(StatsIncorrectSetup, "Erreur d'accès aux demandes pour les stats")
          List()
        }
        val currentAreaOnly = request.getQueryString("currentAreaOnly").map(_.toBoolean).getOrElse(false)

        val applicationsByArea = (
            if(currentAreaOnly) { allApplications.filter(_.area == request.currentArea.id) }
            else { allApplications }
          ).groupBy(_.area)
            .map{ case (areaId: UUID, applications: Seq[Application]) => (Area.all.find(_.id == areaId).get, applications) }

        val firstDate = if(allApplications.isEmpty) {
          DateTime.now()
        } else {
          allApplications.map(_.creationDate).min.weekOfWeekyear().roundFloorCopy()
        }
        val today = DateTime.now(timeZone)
        val months = Time.monthsMap(firstDate, today)
        eventService.log(StatsShowed, "Visualise les stats")
        Ok(views.html.stats(request.currentUser, request.currentArea)(months, applicationsByArea, users, currentAreaOnly))
    }
  }

  def allAs(userId: UUID) = loginAction { implicit request =>
    val userOption = userService.byId(userId)
    (request.currentUser.admin, userOption)  match {
      case (false, Some(user)) =>
        eventService.log(AllAsUnauthorized, s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur $userId", user=Some(user))
        Unauthorized("Vous n'avez pas le droits de faire ça, vous n'êtes pas administrateur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
      case (true, Some(user)) if user.admin =>
        eventService.log(AllAsUnauthorized, s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur admin $userId", user=Some(user))
        Unauthorized("Vous n'avez pas le droits de faire ça avec un compte administrateur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
      case (true, Some(user)) if request.currentUser.areas.intersect(user.areas).nonEmpty =>
        val currentUserId = user.id
        val applicationsFromTheArea = List[Application]()
        eventService.log(AllAsShowed, s"Visualise la vue de l'utilisateur $userId", user= Some(user))
        // Bug To Fix
        Ok(views.html.myApplications(user)(applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin), applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin), applicationsFromTheArea))
      case  _ =>
        eventService.log(AllAsNotFound, s"L'utilisateur $userId n'existe pas")
        BadRequest("L'utilisateur n'existe pas ou vous n'avez pas le droit d'accèder à cette page. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
    }
  }

  def showExportMyApplicationsCSV =  loginAction { implicit request =>
    Ok(views.html.CSVExport(request.currentUser))
  }

  def myCSV = loginAction { implicit request =>
    val currentDate = DateTime.now(timeZone)
    val exportedApplications = applicationService.allOpenOrRecentForUserId(request.currentUser.id, request.currentUser.admin, currentDate)
    val usersId = exportedApplications.flatMap(_.invitedUsers.keys) ++ exportedApplications.map(_.creatorUserId)
    val users = userService.byIds(usersId, includeDisabled = true)

    val date = currentDate.toString("dd-MMM-YYY-HH'h'mm", new Locale("fr"))

    eventService.log(MyCSVShowed, s"Visualise un CSV")
    Ok(views.html.allApplicationCSV(exportedApplications, request.currentUser, users)).as("text/csv").withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-${date}.csv"""" )
  }

  def allCSV(areaId: UUID) = loginAction { implicit request =>
    val area = Area.fromId(areaId).get
    val exportedApplications = if(request.currentUser.admin || request.currentUser.groupAdmin) {
      allApplicationVisibleByUserAdmin(request.currentUser, area)
    } else  {
      List()
    }
    val usersId = exportedApplications.flatMap(_.invitedUsers.keys) ++ exportedApplications.map(_.creatorUserId)
    val users = userService.byIds(usersId, includeDisabled = true)

    val date = DateTime.now(timeZone).toString("dd-MMM-YYY-HH'h'mm", new Locale("fr"))

    eventService.log(AllCSVShowed, s"Visualise un CSV pour la zone ${area.name}")
    Ok(views.html.allApplicationCSV(exportedApplications, request.currentUser, users)).as("text/csv").withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-${date}-${area.name.replace(" ","-")}.csv"""" )
  }

  val answerForm = Form(
    mapping(
      "message" -> nonEmptyText,
      "irrelevant" -> boolean,
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
      "privateToHelpers" -> boolean
    )(AnswerData.apply)(AnswerData.unapply)
  )


  def usersThatCanBeInvitedOn[A](application: Application)(implicit request: RequestWithUserData[A]) = {
    (if(request.currentUser.instructor || request.currentUser.expert) {
      val groupsOfArea = userGroupService.byArea(application.area)
      userService.byGroupIds(groupsOfArea.map(_.id)).filter(_.instructor)
    } else if(request.currentUser.helper && application.creatorUserId == request.currentUser.id) {
      userService.byGroupIds(request.currentUser.groupIds).filter(_.helper)
    } else {
      List[User]()
    }).filterNot(user => user.id == request.currentUser.id || application.invitedUsers.contains(user.id))
  }

  def show(id: UUID) = loginAction { implicit request =>
    applicationService.byId(id, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.log(ApplicationNotFound, s"La demande $id n'existe pas")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        if(application.canBeShowedBy(request.currentUser)) {
            val usersThatCanBeInvited =  usersThatCanBeInvitedOn(application)
            val groups = userGroupService.byIds(usersThatCanBeInvited.flatMap(_.groupIds)).filter(_.areaIds.contains(application.area))
            val groupsWithUsersThatCanBeInvited = groups.map { group =>
              group -> usersThatCanBeInvited.filter(_.groupIds.contains(group.id))
            }
            val renderedApplication = if((application.haveUserInvitedOn(request.currentUser) || request.currentUser.id == application.creatorUserId) && request.currentUser.expert && request.currentUser.admin && !application.closed) {
              // If user is expert, admin and invited to the application we desanonymate
              applicationService.byId(id, request.currentUser.id, false).get
            } else {
              application
            }
            val openedTab = request.flash.get("opened-tab").getOrElse("answer")
          
            eventService.log(ApplicationShowed, s"Demande $id consulté", Some(application))
            Ok(views.html.showApplication(request.currentUser)(groupsWithUsersThatCanBeInvited, renderedApplication, answerForm, openedTab))
        }
        else {
          eventService.log(ApplicationUnauthorized, s"L'accès à la demande $id n'est pas autorisé", Some(application))
          Unauthorized(s"Vous n'avez pas les droits suffisants pour voir cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
        }
    }
  }

  def answerFile(applicationId: UUID, answerId: UUID, filename: String) =  file(applicationId, Some(answerId), filename)

  def applicationFile(applicationId: UUID, filename: String) = file(applicationId, None, filename)

  def file(applicationId: UUID, answerIdOption: Option[UUID], filename: String) = loginAction { implicit request =>
    (answerIdOption,applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin)) match {
      case (_, None) =>
        eventService.log(ApplicationNotFound, s"La demande $applicationId n'existe pas")
        NotFound("Nous n'avons pas trouvé ce fichier")
      case (Some(answerId), Some(application)) if application.fileCanBeShowed(request.currentUser, answerId) =>
          application.answers.find(_.id == answerId) match {
            case Some(answer) if answer.files.getOrElse(Map()).contains(filename) =>
              eventService.log(FileOpened, s"Le fichier de la réponse $answerId sur la demande $applicationId a été ouvert")
              Ok.sendPath(Paths.get(s"$filesPath/ans_$answerId-$filename"), true, { _: Path => filename })
            case _ =>
              eventService.log(FileNotFound, s"Le fichier de la réponse $answerId sur la demande $applicationId n'existe pas")
              NotFound("Nous n'avons pas trouvé ce fichier")
          }
      case (None, Some(application)) if application.fileCanBeShowed(request.currentUser) =>
        if(application.files.contains(filename)) {
            eventService.log(FileOpened, s"Le fichier de la demande $applicationId a été ouvert")
            Ok.sendPath(Paths.get (s"$filesPath/app_$applicationId-$filename"), true, { _: Path => filename })
        } else {
            eventService.log(FileNotFound, s"Le fichier de la demande $application sur la demande $applicationId n'existe pas")
            NotFound("Nous n'avons pas trouvé ce fichier")
        }
      case (_, Some(application)) =>
          eventService.log(FileUnauthorized, s"L'accès aux fichiers sur la demande $applicationId n'est pas autorisé", Some(application))
          Unauthorized("Vous n'avez pas les droits suffisants pour voir les fichiers sur cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")

    }
  }

  def answer(applicationId: UUID) = loginAction { implicit request =>
    withApplication(applicationId) {  application =>
      val form = answerForm.bindFromRequest
      val answerId = AttachmentHelper.retrieveOrGenerateAnswerId(form.data)
      val (pendingAttachments, newAttachments) = AttachmentHelper.computeStoreAndRemovePendingAndNewAnswerAttachment(answerId, form.data, computeAttachmentsToStore(request), filesPath)
      form.fold(
        formWithErrors => {
          val error = s"Erreur dans le formulaire de réponse (${formWithErrors.errors.map(_.message).mkString(", ")})."
          eventService.log(AnswerNotCreated, s"$error")
          Redirect(routes.ApplicationController.show(applicationId).withFragment("answer-error")).flashing("answer-error" -> error, "opened-tab" -> "anwser")
        },
        answerData => {
           val answer = Answer(answerId,
              applicationId, DateTime.now(timeZone),
              answerData.message,
              request.currentUser.id,
              userGroupService.contextualizedUserName(request.currentUser),
              Map(),
              answerData.privateToHelpers == false,
              answerData.applicationIsDeclaredIrrelevant,
              Some(answerData.infos),
              files = Some(newAttachments ++ pendingAttachments))
          if (applicationService.add(applicationId, answer) == 1) {
            eventService.log(AnswerCreated, s"La réponse ${answer.id} a été créé sur la demande $applicationId", Some(application))
            notificationsService.newAnswer(application, answer)
            Redirect(s"${routes.ApplicationController.show(applicationId)}#answer-${answer.id}").flashing("success" -> "Votre réponse a bien été envoyée")
          } else {
            eventService.log(AnswerNotCreated, s"La réponse ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
            InternalServerError("Votre réponse n'a pas pu être envoyé")
          }
        }
      )
    }
  }

  val inviteForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid).verifying("Vous devez inviter au moins une personne", _.nonEmpty)   ,
      "privateToHelpers" -> boolean
    )(InvitationData.apply)(InvitationData.unapply)
  )

  def invite(applicationId: UUID) = loginAction { implicit request =>
    withApplication(applicationId) {  application =>
      inviteForm.bindFromRequest.fold(
        formWithErrors => {
          val error = s"Erreur dans le formulaire d'invitation (${formWithErrors.errors.map(_.message).mkString(", ")})."
          eventService.log(InviteNotCreated, error)
          Redirect(routes.ApplicationController.show(applicationId).withFragment("answer-error")).flashing("answer-error" -> error, "opened-tab" -> "invite" )
        },
        inviteData => {
          val usersThatCanBeInvited = usersThatCanBeInvitedOn(application)
          val invitedUsers: Map[UUID, String] = usersThatCanBeInvited
            .filter(user => inviteData.invitedUsers.contains(user.id))
            .map(user => (user.id,userGroupService.contextualizedUserName(user))).toMap

          val answer = Answer(UUID.randomUUID(),
            applicationId,
            DateTime.now(timeZone),
            inviteData.message,
            request.currentUser.id,
            userGroupService.contextualizedUserName(request.currentUser),
            invitedUsers,
            not(inviteData.privateToHelpers),
            false,
            Some(Map()))
          if (applicationService.add(applicationId, answer)  == 1) {
            notificationsService.newAnswer(application, answer)
            eventService.log(AgentsAdded, s"L'ajout d'utilisateur ${answer.id} a été créé sur la demande $applicationId", Some(application))
            Redirect(routes.ApplicationController.myApplications()).flashing ("success" -> "Les utilisateurs ont été invités sur la demande")
          } else {
            eventService.log(AgentsNotAdded, s"L'ajout d'utilisateur ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
            InternalServerError("Les utilisateurs n'ont pas pu être invités")
          }
        })
    }
  }
  
  def inviteExpert(applicationId: UUID) = loginAction { implicit request =>
    applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.log(AddExpertNotFound, s"La demande $applicationId n'existe pas pour ajouter un expert")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        if(application.canHaveExpertsInvitedBy(request.currentUser)) {
          val experts: Map[UUID, String] = User.admins.filter(_.expert).map(user => user.id -> userGroupService.contextualizedUserName(user)).toMap
          val answer = Answer(UUID.randomUUID(),
            applicationId,
            DateTime.now(timeZone),
            "J'ajoute un expert",
            request.currentUser.id,
            userGroupService.contextualizedUserName(request.currentUser),
            experts,
            true,
            false,
            Some(Map()))
          if (applicationService.add(applicationId, answer, true)  == 1) {
            notificationsService.newAnswer(application, answer)
            eventService.log(AddExpertCreated, s"La réponse ${answer.id} a été créé sur la demande $applicationId", Some(application))
            Redirect(routes.ApplicationController.myApplications()).flashing ("success" -> "Un expert a été invité sur la demande")
          } else {
            eventService.log(AddExpertNotCreated, s"L'invitation d'experts ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
            InternalServerError("L'expert n'a pas pu être invité")
          }
        } else {
          eventService.log(AddExpertUnauthorized, s"L'invitation d'experts pour la demande $applicationId n'est pas autorisé", Some(application))
          Unauthorized("Vous n'avez pas les droits suffisants pour inviter des agents à cette demande. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
        }
    }
  }

  def terminate(applicationId: UUID) = loginAction {  implicit request =>
    (request.getQueryString("usefulness"), applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin)) match {
      case (_, None) =>
        eventService.log(TerminateNotFound, s"La demande $applicationId n'existe pas pour la clôturer")
        NotFound("Nous n'avons pas trouvé cette demande.")
      case (None, _) =>
        eventService.log(TerminateIncompleted, s"La demande de clôture pour $applicationId est incompléte")
        BadGateway("L'utilité de la demande n'est pas présente, il s'agit surement d'une erreur. Vous pouvez contacter l'équipe A+ : ${Constants.supportEmail}")
      case (Some(usefulness), Some(application)) =>
        val finalUsefulness = if(request.currentUser.id == application.creatorUserId) {
          Some(usefulness)
        } else {
          None
        }
        if(application.canBeClosedBy(request.currentUser)) {
          if(applicationService.close(applicationId, finalUsefulness, DateTime.now(timeZone))) {
            eventService.log(TerminateCompleted, s"La demande $applicationId est clôturé", Some(application))
            Redirect(routes.ApplicationController.myApplications()).flashing("success" -> "L'application a été indiqué comme clôturée")
          } else {
            eventService.log(TerminateError, s"La demande $applicationId n'a pas pu être clôturé en BDD", Some(application))
            InternalServerError("Erreur interne: l'application n'a pas pu être indiqué comme clôturée")
          }
        } else {
          eventService.log(TerminateUnauthorized, s"L'utilisateur n'a pas le droit de clôturer la demande $applicationId", Some(application))
          Unauthorized("Seul le créateur de la demande ou un expert peut clôre la demande")
        }
    }
  }
}
