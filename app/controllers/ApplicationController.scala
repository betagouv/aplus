package controllers

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
import org.joda.time.{DateTime, DateTimeZone}
import org.webjars.play.WebJarsUtil
import services.{ApplicationService, EventService, NotificationService, UserService}
import extentions.UUIDHelper

import scala.collection.immutable.ListMap

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class ApplicationController @Inject()(loginAction: LoginAction,
                                      userService: UserService,
                                      applicationService: ApplicationService,
                                      notificationsService: NotificationService,
                                      eventService: EventService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {
  import forms.Models._

  private val timeZone = Time.dateTimeZone

  val applicationForm = Form(
    mapping(
      "subject" -> nonEmptyText.verifying(maxLength(150)),
      "description" -> nonEmptyText,
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
      "users" -> list(uuid).verifying("Vous devez sélectionner au moins un agent", _.nonEmpty),
      "organismes" -> list(text)
    )(ApplicationData.apply)(ApplicationData.unapply)
  )

  def create = loginAction { implicit request =>
    eventService.info("APPLICATION_FORM_SHOWED", s"Visualise le formulaire de création de demande")
    Ok(views.html.createApplication(request.currentUser, request.currentArea)(userService.byArea(request.currentArea.id).filter(_.instructor), applicationForm))
  }

  def createPost = loginAction { implicit request =>
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
             eventService.info("APPLICATION_CREATION_INVALID", s"L'utilisateur essai de créé une demande invalide")
             BadRequest(views.html.createApplication(request.currentUser, request.currentArea)(instructors, formWithErrors))
           },
           applicationData => {
             val invitedUsers: Map[UUID, String] = applicationData.users.flatMap {  id =>
               userService.byId(id).map(id -> _.nameWithQualite)
             }.toMap
             val application = Application(UUIDHelper.randomUUID,
               DateTime.now(timeZone),
               request.currentUser.nameWithQualite,
               request.currentUser.id,
               applicationData.subject,
               applicationData.description,
               applicationData.infos,
               invitedUsers,
               request.currentArea.id,
               false)
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

  def all = loginAction { implicit request =>
    val myApplications = applicationService.allForUserId(request.currentUser.id, request.currentUser.admin)
    val myOpenApplications = myApplications.filter(!_.closed)
    val myClosedApplications = myApplications.filter(_.closed)
    
    val applicationsFromTheArea = if(request.currentUser.admin) {
      applicationService.allByArea(request.currentArea.id, true)
    } else if(request.currentUser.groupAdmin) {
      val users = userService.byArea(request.currentArea.id)
      val groupUserIds = users.filter(_.groupIds.intersect(request.currentUser.groupIds).nonEmpty).map(_.id)
      applicationService.allForUserIds(groupUserIds, true)
    } else { List[Application]() }

    eventService.info("ALL_APPLICATIONS_SHOWED",
      s"Visualise la liste des applications : open=${myOpenApplications.size}/closed=${myClosedApplications.size}/sone=${applicationsFromTheArea.size}")
    Ok(views.html.allApplication(request.currentUser, request.currentArea)(myOpenApplications, myClosedApplications, applicationsFromTheArea))
  }
  
  import Time.dateTimeOrdering
  
  private def weeksMap(fromDate: DateTime, toDate: DateTime): ListMap[String, String] = {
    val weekDay = toDate.weekOfWeekyear().roundFloorCopy()
    def recursion(date: DateTime): ListMap[String, String] = {
      if(date.isBefore(fromDate)) {
        ListMap()
      } else {
        recursion(date.minusWeeks(1)) + (f"${date.getYear}/${date.getWeekOfWeekyear}%02d" -> date.toString("E dd MMM YYYY", new Locale("fr")))
      }
    }
    recursion(weekDay)
  }

  private def monthsMap(fromDate: DateTime, toDate: DateTime): ListMap[String, String] = {
    def recursion(date: DateTime): ListMap[String, String] = {
      if(date.isBefore(fromDate)) {
        ListMap()
      } else {
        recursion(date.minusMonths(1)) + (f"${date.getYear}/${date.getMonthOfYear}%02d" -> date.toString("MMMM YYYY", new Locale("fr")))
      }
    }
    recursion(toDate)
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
          applicationService.all(true)
        } else if(request.currentUser.groupAdmin) {
          applicationService.allForUserIds(users.map(_.id), true)
        } else {
          eventService.warn("STATS_INCORRECT_SETUP", s"Erreur d'accès aux demandes pour les stats")
          List()
        }
        val applicationsByArea = allApplications.groupBy(_.area).map{ case (areaId: UUID, applications: Seq[Application]) => (Area.all.find(_.id == areaId).get, applications) }

        val firstDate = if(allApplications.isEmpty) {
          DateTime.now()
        } else {
          allApplications.map(_.creationDate).min.weekOfWeekyear().roundFloorCopy()
        }
        val today = DateTime.now(timeZone)
        val weeks = weeksMap(firstDate, today)
        val months = monthsMap(firstDate, today)
        eventService.info("STATS_SHOWED", s"Visualise les stats")
        Ok(views.html.stats(request.currentUser, request.currentArea)(months, applicationsByArea, users))
    }
  }

  def allAs(userId: UUID) = loginAction { implicit request =>
    (request.currentUser.admin, userService.byId(userId))  match {
      case (false, Some(user)) =>
        eventService.warn("ALL_AS_UNAUTHORIZED", s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur $userId", user=Some(user))
        Unauthorized("Vous n'avez pas le droits de faire ça, vous n'êtes pas administrateur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (true, Some(user)) if user.admin =>
        eventService.warn("ALL_AS_UNAUTHORIZED", s"L'utilisateur n'a pas de droit d'afficher la vue de l'utilisateur admin $userId", user=Some(user))
        Unauthorized("Vous n'avez pas le droits de faire ça avec un compte administrateur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (true, Some(user)) if user.areas.contains(request.currentArea.id) =>
        val currentUserId = user.id
        val applicationsFromTheArea = List[Application]()
        eventService.info("ALL_AS_SHOWED", s"Visualise la vue de l'utilisateur $userId", user= Some(user))
        Ok(views.html.allApplication(user, request.currentArea)(applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin), applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin), applicationsFromTheArea))
      case  _ =>
        eventService.error("ALL_AS_NOT_FOUND", s"L'utilisateur $userId n'existe pas")
        BadRequest("L'utilisateur n'existe pas ou vous n'étes pas dans sa zone. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
    }
  }

  def allCSV = loginAction { implicit request =>
    val currentUserId = request.currentUser.id
    val users = userService.byArea(request.currentArea.id)
    val exportedApplications = if(request.currentUser.admin) {
      applicationService.allByArea(request.currentArea.id, true)
    } else if(request.currentUser.groupAdmin) {
      val groupUserIds = users.filter(_.groupIds.intersect(request.currentUser.groupIds).nonEmpty).map(_.id)
      applicationService.allForUserIds(groupUserIds, true)
    } else  {
      applicationService.allForUserId(currentUserId, request.currentUser.admin)
    }
    val date = DateTime.now(timeZone).toString("dd-MMM-YYY-HHhmm", new Locale("fr"))
    eventService.info("CSV_SHOWED", s"Visualise un CSV")
    Ok(views.html.allApplicationCSV(exportedApplications.toSeq, request.currentUser, users)).as("text/csv").withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-${date}.csv"""" )
  }

  def show(id: UUID) = loginAction { implicit request =>
    applicationService.byId(id, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.error("APPLICATION_NOT_FOUND", s"La demande $id n'existe pas")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val currentUser = request.currentUser
        if(currentUser.admin||
          application.invitedUsers.keys.toList.contains(request.currentUser.id)||
          application.creatorUserId==request.currentUser.id) {
            val users = userService.byArea(request.currentArea.id).filterNot(_.id == request.currentUser.id)
              .filter(_.instructor)
            eventService.info("APPLICATION_SHOWED", s"Demande $id consulté", Some(application))
            Ok(views.html.showApplication(request.currentUser, request.currentArea)(users, application, answerToAgentsForm))
        }
        else {
          eventService.warn("APPLICATION_UNAUTHORIZED", s"L'accès à la demande $id n'est pas autorisé", Some(application))
          Unauthorized("Vous n'avez pas les droits suffisants pour voir cette demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
        }
    }
  }

  val answerToHelperForm = Form(
    mapping(
      "message" -> text,
      "irrelevant" -> boolean
    )(AnswerToHelperData.apply)(AnswerToHelperData.unapply)
  )

  def answerHelper(applicationId: UUID) = loginAction { implicit request =>
    answerToHelperForm.bindFromRequest.fold(
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
            val answer = Answer(UUID.randomUUID(),
              applicationId, DateTime.now(timeZone),
              answerData.message,
              request.currentUser.id,
              request.currentUser.nameWithQualite,
              Map(),
              true,
              request.currentArea.id,
              answerData.applicationIsDeclaredIrrelevant,
              Some(Map()))
            if (applicationService.add(applicationId, answer) == 1) {
              eventService.info("ANSWER_CREATED", s"La réponse ${answer.id} a été créé sur la demande $applicationId", Some(application))
              notificationsService.newAnswer(application, answer)
              Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre réponse a bien été envoyée")
            } else {
              eventService.error("ANSWER_NOT_CREATED", s"La réponse ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
              InternalServerError("Votre réponse n'a pas pu être envoyé")
            }
        }
      })
  }

  val answerToAgentsForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid),
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
    )(AnswerToAgentsData.apply)(AnswerToAgentsData.unapply)
  )

  def answerAgents(applicationId: UUID) = loginAction { implicit request =>
    val answerData = answerToAgentsForm.bindFromRequest.get

    applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.error("ADD_ANSWER_NOT_FOUND", s"La demande $applicationId n'existe pas pour ajouter une réponse")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val notifiedUsers: Map[UUID, String] = answerData.notifiedUsers.flatMap { id =>
          userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val answer = Answer(UUID.randomUUID(),
          applicationId,
          DateTime.now(timeZone),
          answerData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          notifiedUsers,
          request.currentUser.id == application.creatorUserId,
          request.currentArea.id,
          false,
          Some(answerData.infos))
        if (applicationService.add(applicationId,answer) == 1) {
          notificationsService.newAnswer(application, answer)
          eventService.info("ANSWER_CREATED", s"La réponse ${answer.id} a été créé sur la demande $applicationId", Some(application))
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre réponse a bien été envoyée")
        } else {
          eventService.error("ANSWER_NOT_CREATED", s"La réponse ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
          InternalServerError("Votre réponse n'a pas pu être envoyée")
        }
    }
  }

  def invite(applicationId: UUID) = loginAction { implicit request =>
    val inviteData = answerToAgentsForm.bindFromRequest.get
    applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        eventService.error("ADD_ANSWER_NOT_FOUND", s"La demande $applicationId n'existe pas pour ajouter une réponse")
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val invitedUsers: Map[UUID, String] = inviteData.notifiedUsers.flatMap { id =>
          userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val answer = Answer(UUID.randomUUID(),
          applicationId,
          DateTime.now(timeZone),
          inviteData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          invitedUsers,
          false,
          request.currentArea.id,
          false,
          Some(Map()))
        if (applicationService.add(applicationId, answer)  == 1) {
          notificationsService.newAnswer(application, answer)
          eventService.info("ANSWER_CREATED", s"La réponse ${answer.id} a été créé sur la demande $applicationId", Some(application))
          Redirect(routes.ApplicationController.all()).flashing ("success" -> "Les agents ont été invités sur la demande")
        } else {
          eventService.error("ANSWER_NOT_CREATED", s"La réponse ${answer.id} n'a pas été créé sur la demande $applicationId : problème BDD", Some(application))
          InternalServerError("Les agents n'ont pas pu être invités")
        }
    }
  }

  def changeArea(areaId: UUID) = loginAction {  implicit request =>
    eventService.info("AREA_CHANGE", s"Changement vers la zone $areaId")
    Redirect(routes.ApplicationController.all()).withSession(request.session - "areaId" + ("areaId" -> areaId.toString))
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
        if(application.creatorUserId == request.currentUser.id || request.currentUser.admin) {
          if(applicationService.close(applicationId, usefulness, DateTime.now(timeZone))) {
            eventService.info("TERMINATE_COMPLETED", s"La demande $applicationId est clôturé", Some(application))
            Redirect(routes.ApplicationController.all()).flashing("success" -> "L'application a été indiqué comme clôturée")
          } else {
            eventService.error("TERMINATE_ERROR", s"La demande $applicationId n'a pas pu être clôturé en BDD", Some(application))
            InternalServerError("Erreur interne: l'application n'a pas pu être indiqué comme clôturée")
          }
        } else {
          eventService.warn("TERMINATE_UNAUTHORIZED", s"L'utilisateur n'a pas le droit de clôturer la demande $applicationId", Some(application))
          Unauthorized("Seul le créateur de la demande ou un administrateur peut clore la demande")
        }
    }
  }
}
