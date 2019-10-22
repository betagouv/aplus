package controllers

import java.nio.file.{Files, Path, Paths}
import java.util.{Locale, UUID}

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation._
import play.api.data.validation.Constraints._
import actions._
import forms.FormsPlusMap
import models._
import org.joda.time.DateTime
import org.webjars.play.WebJarsUtil
import services._
import extentions.{Time, UUIDHelper}
import extentions.Time.dateTimeOrdering

import scala.concurrent.ExecutionContext

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class ApplicationController @Inject()(loginAction: LoginAction,
                                      userService: UserService,
                                      applicationService: ApplicationService,
                                      notificationsService: NotificationService,
                                      eventService: EventService,
                                      organisationService: OrganisationService,
                                      userGroupService: UserGroupService,
                                      configuration: play.api.Configuration)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {
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
      "users" -> list(uuid).verifying("Vous devez sélectionner au moins un agent", _.nonEmpty),
      "organismes" -> list(text),
      "category" -> optional(text),
      "selected-subject" -> optional(text)
    )(ApplicationData.apply)(ApplicationData.unapply)
  )

  def create = loginAction { implicit request =>
    eventService.info("APPLICATION_FORM_SHOWED", s"Visualise le formulaire de création de demande")
    val instructors = userService.byArea(request.currentArea.id).filter(_.instructor)
    val groupIds = instructors.flatMap(_.groupIds).distinct
    val organismeGroups = userGroupService.groupByIds(groupIds).filter(_.area == request.currentArea.id)
    Ok(views.html.createApplication(request.currentUser,request.currentArea)(instructors, organismeGroups, applicationForm))
  }

  def createSimplified = loginAction { implicit request =>
    eventService.info("APPLICATION_FORM_SHOWED", s"Visualise le formulaire simplifié de création de demande")
    val instructors = userService.byArea(request.currentArea.id).filter(_.instructor)
    val groupIds = instructors.flatMap(_.groupIds).distinct
    val organismeGroups = userGroupService.groupByIds(groupIds).filter(userGroup => userGroup.organisationSetOrDeducted.nonEmpty && userGroup.area == request.currentArea.id)
    val categories = organisationService.categories
    Ok(views.html.simplifiedCreateApplication(request.currentUser, request.currentArea)(instructors, organismeGroups, categories, None, applicationForm))
  }

  def createPost = createPostBis(false)

  def createSimplifiedPost = createPostBis(true)


  private def createPostBis(simplified: Boolean) = loginAction { implicit request =>
    request.currentUser.helper match {
       case false => {
         eventService.warn("APPLICATION_CREATION_UNAUTHORIZED", s"L'utilisateur n'a pas de droit de créer une demande")
         Unauthorized("Vous n'avez pas les droits suffisants pour créer une demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
       }
       case true => {
         applicationForm.bindFromRequest.fold(
           formWithErrors => {
             // binding failure, you retrieve the form containing errors:
             val instructors = userService.byArea(request.currentArea.id).filter(_.instructor)
             eventService.info(s"APPLICATION_CREATION_INVALID", s"L'utilisateur essai de créé une demande invalide ${formWithErrors.errors.map(_.message)}")
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
               val organismeGroups = userGroupService.groupByIds(groupIds).filter(userGroup => userGroup.organisationSetOrDeducted.nonEmpty && userGroup.area == request.currentArea.id)
               BadRequest(views.html.simplifiedCreateApplication(request.currentUser, request.currentArea)(instructors, organismeGroups, categories, formWithErrors("category").value, formWithErrorsfinal))
             } else {
               val organismeGroups = userGroupService.groupByIds(groupIds).filter(_.area == request.currentArea.id)
               BadRequest(views.html.createApplication(request.currentUser, request.currentArea)(instructors, organismeGroups, formWithErrorsfinal))
             }
           },
           applicationData => {
             val invitedUsers: Map[UUID, String] = applicationData.users.flatMap {  id =>
               userService.byId(id).map(id -> _.nameWithQualite)
             }.toMap
             val applicationId = UUIDHelper.randomUUID
             val file = request.body.asMultipartFormData.flatMap(_.file("file")).flatMap { uploadedFile =>
               if(!uploadedFile.filename.isEmpty) {
                 val filename = Paths.get(uploadedFile.filename).getFileName
                 val fileDestination = Paths.get(s"$filesPath/app_$applicationId-$filename")
                 Files.copy(uploadedFile.ref, fileDestination)
                 Some(filename.toString -> 0L)  // ToDo filesize
               } else {
                 None
               }
             }
             val application = Application(applicationId,
               DateTime.now(timeZone),
               request.currentUser.nameWithQualite,
               request.currentUser.id,
               applicationData.subject,
               applicationData.description,
               applicationData.infos,
               invitedUsers,
               request.currentArea.id,
               false,
               hasSelectedSubject = applicationData.selectedSubject.contains(applicationData.subject),
               category = applicationData.category,
               files = file.toMap)
             if(applicationService.createApplication(application)) {
               notificationsService.newApplication(application)
               eventService.info("APPLICATION_CREATED", s"La demande ${application.id} a été créé", Some(application))
               Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre demande a bien été envoyée")
             }  else {
               eventService.error("APPLICATION_CREATION_ERROR", s"La demande ${application.id} n'a pas pu être créé", Some(application))
               InternalServerError("Error Interne: Votre demande n'a pas pu être envoyé. Merci de rééssayer ou contacter l'administrateur")
             }
           }
         )
       }
    }
  }


  def allApplicationVisibleByUserAdmin(user: User) = if(user.admin) {
    applicationService.allForAreas(user.areas, true)
  } else if(user.groupAdmin) {
    val users = userService.byArea(user.id)
    val groupUserIds = users.filter(_.groupIds.intersect(user.groupIds).nonEmpty).map(_.id)
    applicationService.allForUserIds(groupUserIds, true)
  } else { List[Application]() }


  def all = loginAction { implicit request =>
    val myApplications = applicationService.allForUserId(request.currentUser.id, request.currentUser.admin)
    val myOpenApplications = myApplications.filter(!_.closed)
    val myClosedApplications = myApplications.filter(_.closed)
    
    val applicationsFromTheArea = allApplicationVisibleByUserAdmin(request.currentUser)

    eventService.info("ALL_APPLICATIONS_SHOWED",
      s"Visualise la liste des applications : open=${myOpenApplications.size}/closed=${myClosedApplications.size}/zone=${applicationsFromTheArea.size}")
    Ok(views.html.allApplication(request.currentUser, request.currentArea)(myOpenApplications, myClosedApplications, applicationsFromTheArea))
  }
  

  def stats = loginAction { implicit request =>
    (request.currentUser.admin || request.currentUser.groupAdmin) match {
      case false =>
        eventService.warn("STATS_UNAUTHORIZED", s"L'utilisateur n'a pas de droit d'afficher les stats")
        Unauthorized("Vous n'avez pas les droits suffisants pour voir les statistiques. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case true =>
        val users = if(request.currentUser.admin) {
          userService.all
        } else if(request.currentUser.groupAdmin) {
          userService.byGroupIds(request.currentUser.groupIds)
        } else {
          eventService.warn("STATS_INCORRECT_SETUP", s"Erreur d'accès aux utilisateurs pour les stats")
          List()
        }

        val allApplications = if(request.currentUser.admin) {
          applicationService.allForAreas(request.currentUser.areas, true)
        } else if(request.currentUser.groupAdmin) {
          applicationService.allForUserIds(users.map(_.id), true)
        } else {
          eventService.warn("STATS_INCORRECT_SETUP", s"Erreur d'accès aux demandes pour les stats")
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
        val weeks = Time.weeksMap(firstDate, today)
        val months = Time.monthsMap(firstDate, today)
        eventService.info("STATS_SHOWED", s"Visualise les stats")
        Ok(views.html.stats(request.currentUser, request.currentArea)(months, applicationsByArea, users, currentAreaOnly))
    }
  }

  def allAs(userId: UUID) = loginAction { implicit request =>
    val userOption = userService.byId(userId)
    (request.currentUser.admin, userOption)  match {
      case (false, Some(user)) =>
        eventService.warn("ALL_AS_UNAUTHORIZED", s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur $userId", user=Some(user))
        Unauthorized("Vous n'avez pas le droits de faire ça, vous n'êtes pas administrateur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (true, Some(user)) if user.admin =>
        eventService.warn("ALL_AS_UNAUTHORIZED", s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur admin $userId", user=Some(user))
        Unauthorized("Vous n'avez pas le droits de faire ça avec un compte administrateur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (true, Some(user)) if request.currentUser.areas.intersect(user.areas).nonEmpty =>
        val currentUserId = user.id
        val applicationsFromTheArea = List[Application]()
        eventService.info("ALL_AS_SHOWED", s"Visualise la vue de l'utilisateur $userId", user= Some(user))
        Ok(views.html.allApplication(user, request.currentArea)(applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin), applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin), applicationsFromTheArea))
      case  _ =>
        eventService.error("ALL_AS_NOT_FOUND", s"L'utilisateur $userId n'existe pas")
        BadRequest("L'utilisateur n'existe pas ou vous n'avez pas le droit d'accèder à cette page. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
    }
  }

  def allCSV = loginAction { implicit request =>
    val currentUserId = request.currentUser.id
    val users = userService.byArea(request.currentArea.id)
    val exportedApplications = if(request.currentUser.admin || request.currentUser.groupAdmin) {
      allApplicationVisibleByUserAdmin(request.currentUser)
    } else  {
      applicationService.allForUserId(currentUserId, request.currentUser.admin)
    }
    val date = DateTime.now(timeZone).toString("dd-MMM-YYY-HHhmm", new Locale("fr"))

    eventService.info("CSV_SHOWED", s"Visualise un CSV")
    Ok(views.html.allApplicationCSV(exportedApplications.toSeq, request.currentUser, users)).as("text/csv").withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-${date}.csv"""" )
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
      userService.byArea(request.currentArea.id).filter(_.instructor)
    } else if(request.currentUser.helper && application.creatorUserId == request.currentUser.id) {
      userService.byGroupIds(request.currentUser.groupIds).filter(_.helper)
    } else {
      List[User]()
    }).filterNot(user => user.id == request.currentUser.id || application.invitedUsers.contains(user.id))
  }

  def show(id: UUID) = loginAction { implicit request =>
    applicationService.byId(id, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.error("APPLICATION_NOT_FOUND", s"La demande $id n'existe pas")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        if(application.canBeShowedBy(request.currentUser)) {
            val usersThatCanBeInvited =  usersThatCanBeInvitedOn(application)

            val renderedApplication = if((application.haveUserInvitedOn(request.currentUser) || request.currentUser.id == application.creatorUserId) && request.currentUser.expert && request.currentUser.admin && !application.closed) {
              // If user is expert, admin and invited to the application we desanonymate
              applicationService.byId(id, request.currentUser.id, false).get
            } else {
              application
            }

            eventService.info("APPLICATION_SHOWED", s"Demande $id consulté", Some(application))
            Ok(views.html.showApplication(request.currentUser, request.currentArea)(usersThatCanBeInvited, renderedApplication, answerForm))
        }
        else {
          eventService.warn("APPLICATION_UNAUTHORIZED", s"L'accès à la demande $id n'est pas autorisé", Some(application))
          Unauthorized("Vous n'avez pas les droits suffisants pour voir cette demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
        }
    }
  }

  def answerFile(applicationId: UUID, answerId: UUID, filename: String) =  file(applicationId, Some(answerId), filename)

  def applicationFile(applicationId: UUID, filename: String) = file(applicationId, None, filename)

  def file(applicationId: UUID, answerIdOption: Option[UUID], filename: String) = loginAction { implicit request =>
    (answerIdOption,applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin)) match {
      case (_, None) =>
        eventService.error("APPLICATION_NOT_FOUND", s"La demande $applicationId n'existe pas")
        NotFound("Nous n'avons pas trouvé ce fichier")
      case (Some(answerId), Some(application)) if application.fileCanBeShowed(request.currentUser, answerId) =>
          application.answers.find(_.id == answerId) match {
            case Some(answer) if answer.files.getOrElse(Map()).contains(filename) =>
              eventService.info("FILE_OPEN", s"Le fichier de la réponse $answerId sur la demande $applicationId a été ouvert")
              Ok.sendPath(Paths.get(s"$filesPath/ans_$answerId-$filename"))
            case _ =>
              eventService.error("FILE_NOT_FOUND", s"Le fichier de la réponse $answerId sur la demande $applicationId n'existe pas")
              NotFound("Nous n'avons pas trouvé ce fichier")
          }
      case (None, Some(application)) if application.fileCanBeShowed(request.currentUser) =>
        if(application.files.contains(filename)) {
            eventService.info("FILE_OPEN", s"Le fichier de la demande $applicationId a été ouvert")
            Ok.sendPath (Paths.get (s"$filesPath/app_$applicationId-$filename") )
        } else {
            eventService.error("FILE_NOT_FOUND", s"Le fichier de la demande $application sur la demande $applicationId n'existe pas")
            NotFound("Nous n'avons pas trouvé ce fichier")
        }
      case (_, Some(application)) =>
          eventService.warn("FILE_UNAUTHORIZED", s"L'accès aux fichiers sur la demande $applicationId n'est pas autorisé", Some(application))
          Unauthorized("Vous n'avez pas les droits suffisants pour voir les fichiers sur cette demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")

    }
  }

  def answer(applicationId: UUID) = loginAction { implicit request =>
    answerForm.bindFromRequest.fold(
      formWithErrors => {
        eventService.error("ANSWER_NOT_CREATED", s"Impossible d'ajouter une réponse sur la demande $applicationId : problème formulaire")
        BadRequest("Erreur interne, contacter l'administrateur A+ : contact@aplus.beta.gouv.fr")
      },
      answerData => {
        applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
          case None =>
            eventService.error("ADD_ANSWER_NOT_FOUND", s"La demande $applicationId n'existe pas pour ajouter une réponse")
            NotFound("Nous n'avons pas trouvé cette demande")
          case Some(application) =>
            if(application.canBeAnsweredBy(request.currentUser)) {
              val answerId = UUID.randomUUID()
              val file = request.body.asMultipartFormData.flatMap(_.file("file")).flatMap { uploadedFile =>
                if(!uploadedFile.filename.isEmpty) {
                  val filename = Paths.get(uploadedFile.filename).getFileName
                  val fileDestination = Paths.get(s"$filesPath/ans_$answerId-$filename")
                  Files.copy(uploadedFile.ref, fileDestination)
                  Some(filename.toString -> 0L)  // ToDo filesize
                } else {
                  None
                }
              }
              val answer = Answer(answerId,
                applicationId, DateTime.now(timeZone),
                answerData.message,
                request.currentUser.id,
                request.currentUser.nameWithQualite,
                Map(),
                answerData.privateToHelpers == false,
                request.currentArea.id,
                answerData.applicationIsDeclaredIrrelevant,
                Some(answerData.infos),
                files = Some(file.toMap))
              if (applicationService.add(applicationId, answer) == 1) {
                eventService.info("ANSWER_CREATED", s"La réponse ${answer.id} a été créé sur la demande $applicationId", Some(application))
                notificationsService.newAnswer(application, answer)
                Redirect(s"${routes.ApplicationController.show(applicationId)}#answer-${answer.id}").flashing("success" -> "Votre réponse a bien été envoyée")
              } else {
                eventService.error("ANSWER_NOT_CREATED", s"La réponse ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
                InternalServerError("Votre réponse n'a pas pu être envoyé")
              }
            } else {
              eventService.warn("ADD_ANSWER_UNAUTHORIZED", s"La réponse à l'aidant pour la demande $applicationId n'est pas autorisé", Some(application))
              Unauthorized("Vous n'avez pas les droits suffisants pour répondre à cette demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
            }
        }
      })
  }

  val inviteForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid)   ,
      "privateToHelpers" -> boolean
    )(InvitationData.apply)(InvitationData.unapply)
  )

  def invite(applicationId: UUID) = loginAction { implicit request =>
    val inviteData = inviteForm.bindFromRequest.get
    applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.error("ADD_ANSWER_NOT_FOUND", s"La demande $applicationId n'existe pas pour ajouter des experts")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val usersThatCanBeInvited = usersThatCanBeInvitedOn(application)

        val invitedUsers: Map[UUID, String] = usersThatCanBeInvited
          .filter(user => inviteData.invitedUsers.contains(user.id))
          .map(user => (user.id,user.nameWithQualite)).toMap

        if(application.canBeShowedBy(request.currentUser) && invitedUsers.nonEmpty) {
          val answer = Answer(UUID.randomUUID(),
            applicationId,
            DateTime.now(timeZone),
            inviteData.message,
            request.currentUser.id,
            request.currentUser.nameWithQualite,
            invitedUsers,
            inviteData.privateToHelpers == false,
            request.currentArea.id,
            false,
            Some(Map()))
          if (applicationService.add(applicationId, answer)  == 1) {
            notificationsService.newAnswer(application, answer)
            eventService.info("AGENTS_ADDED", s"L'ajout d'agent ${answer.id} a été créé sur la demande $applicationId", Some(application))
            Redirect(routes.ApplicationController.all()).flashing ("success" -> "Les agents ont été invités sur la demande")
          } else {
            eventService.error("AGENTS_NOT_ADDED", s"L'ajout d'agent ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
            InternalServerError("Les agents n'ont pas pu être invités")
          }
        } else {
          eventService.warn("ADD_AGENTS_UNAUTHORIZED", s"L'invitation d'agents pour la demande $applicationId n'est pas autorisé", Some(application))
          Unauthorized("Vous n'avez pas les droits suffisants pour inviter des agents à cette demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
        }
    }
  }
  
  def inviteExpert(applicationId: UUID) = loginAction { implicit request =>
    applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.error("ADD_EXPERT_NOT_FOUND", s"La demande $applicationId n'existe pas pour ajouter un expert")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        if(application.canHaveExpertsInvitedBy(request.currentUser)) {
          val experts: Map[UUID, String] = User.admins.filter(_.expert).map(user => user.id -> user.nameWithQualite).toMap
          val answer = Answer(UUID.randomUUID(),
            applicationId,
            DateTime.now(timeZone),
            "J'ajoute un expert",
            request.currentUser.id,
            request.currentUser.nameWithQualite,
            experts,
            true,
            request.currentArea.id,
            false,
            Some(Map()))
          if (applicationService.add(applicationId, answer, true)  == 1) {
            notificationsService.newAnswer(application, answer)
            eventService.info("ADD_EXPERT_CREATED", s"La réponse ${answer.id} a été créé sur la demande $applicationId", Some(application))
            Redirect(routes.ApplicationController.all()).flashing ("success" -> "Un expert a été invité sur la demande")
          } else {
            eventService.error("ADD_EXPERT_NOT_CREATED", s"L'invitation d'agents ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
            InternalServerError("L'expert n'a pas pu être invité")
          }
        } else {
          eventService.warn("ADD_EXPERT_UNAUTHORIZED", s"L'invitation d'agents pour la demande $applicationId n'est pas autorisé", Some(application))
          Unauthorized("Vous n'avez pas les droits suffisants pour inviter des agents à cette demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
        }
    }
  }

  def terminate(applicationId: UUID) = loginAction {  implicit request =>
    (request.getQueryString("usefulness"), applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin)) match {
      case (_, None) =>
        eventService.error("TERMINATE_NOT_FOUND", s"La demande $applicationId n'existe pas pour la clôturer")
        NotFound("Nous n'avons pas trouvé cette demande.")
      case (None, _) =>
        eventService.error("TERMINATE_INCOMPLETED", s"La demande de clôture pour $applicationId est incompléte")
        BadGateway("L'utilité de la demande n'est pas présente, il s'agit surement d'une erreur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (Some(usefulness), Some(application)) =>
        val finalUsefulness = if(request.currentUser.id == application.creatorUserId) {
          Some(usefulness)
        } else {
          None
        }
        if(application.canBeClosedBy(request.currentUser)) {
          if(applicationService.close(applicationId, finalUsefulness, DateTime.now(timeZone))) {
            eventService.info("TERMINATE_COMPLETED", s"La demande $applicationId est clôturé", Some(application))
            Redirect(routes.ApplicationController.all()).flashing("success" -> "L'application a été indiqué comme clôturée")
          } else {
            eventService.error("TERMINATE_ERROR", s"La demande $applicationId n'a pas pu être clôturé en BDD", Some(application))
            InternalServerError("Erreur interne: l'application n'a pas pu être indiqué comme clôturée")
          }
        } else {
          eventService.warn("TERMINATE_UNAUTHORIZED", s"L'utilisateur n'a pas le droit de clôturer la demande $applicationId", Some(application))
          Unauthorized("Seul le créateur de la demande ou un expert peut clôre la demande")
        }
    }
  }
}
