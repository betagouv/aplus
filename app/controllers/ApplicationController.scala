package controllers

import java.util.UUID
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
import services.{ApplicationService, NotificationService, UserService}
import extentions.UUIDHelper

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class ApplicationController @Inject()(loginAction: LoginAction,
                                      userService: UserService,
                                      applicationService: ApplicationService,
                                      notificationsService: NotificationService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {
  import forms.Models._

  private val timeZone = Time.timeZone

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
    Ok(views.html.createApplication(request.currentUser, request.currentArea)(userService.byArea(request.currentArea.id).filter(_.instructor), applicationForm))
  }

  def createPost = loginAction { implicit request =>
    applicationForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        val instructors = userService.byArea(request.currentArea.id).filter(_.instructor)
        BadRequest(views.html.createApplication(request.currentUser, request.currentArea)(instructors, formWithErrors))
      },
      applicationData => {
        val invitedUsers: Map[UUID, String] = applicationData.users.flatMap {  id =>
            userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val application = Application(UUIDHelper.randomUUID,
          "En cours",
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
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre demande a bien été envoyée")
        }  else {
          InternalServerError("Error Interne: Votre demande n'a pas pu être envoyé. Merci de rééssayer ou contacter l'administrateur")
        }
      }
    )
  }

  def all = loginAction { implicit request =>
    val currentUserId = request.currentUser.id
    val applicationsFromTheArea = if(request.currentUser.admin) { applicationService.allByArea(request.currentArea.id) } else { List[Application]() }
    Ok(views.html.allApplication(request.currentUser, request.currentArea)(applicationService.allForCreatorUserId(currentUserId), applicationService.allForInvitedUserId(currentUserId), applicationsFromTheArea))
  }

  def show(id: UUID) = loginAction { implicit request =>
    //TODO : check access right
    applicationService.byId(id) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val answers = applicationService.answersByApplicationId(id)
        val users = userService.byArea(request.currentArea.id).filterNot(_.id == request.currentUser.id)
          .filter(_.instructor)
        Ok(views.html.showApplication(request.currentUser, request.currentArea)(users, application, answers))
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
        BadRequest("Erreur interne, contacter l'administrateur A+ : contact@aplus.beta.gouv.fr")
      },
      answerData => {
        applicationService.byId(applicationId) match {
          case None =>
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
              answerData.applicationIsDeclaredIrrelevant)
            if (applicationService.add(answer) == 1) {
              notificationsService.newAnswer(application, answer)
              Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre réponse a bien été envoyée")
            } else {
              InternalServerError("Votre réponse n'a pas pu être envoyé")
            }
        }
      })
  }

  val answerToAgentsForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid)
    )(AnswerToAgentsData.apply)(AnswerToAgentsData.unapply)
  )

  def answerAgents(applicationId: UUID) = loginAction { implicit request =>
    val answerData = answerToAgentsForm.bindFromRequest.get

    applicationService.byId(applicationId) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val notifiedUsers: Map[UUID, String] = answerData.notifiedUsers.flatMap { id =>
          userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val answer = Answer(UUID.randomUUID(),
          applicationId, DateTime.now(timeZone),
          answerData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          notifiedUsers,
          request.currentUser.id == application.creatorUserId,
          request.currentArea.id,
          false)
        if (applicationService.add(answer) == 1) {
          notificationsService.newAnswer(application, answer)
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre réponse a bien été envoyée")
        } else {
          InternalServerError("Votre réponse n'a pas pu être envoyé")
        }
    }
  }

  def invite(applicationId: UUID) = loginAction { implicit request =>
    val inviteData = answerToAgentsForm.bindFromRequest.get
    applicationService.byId(applicationId) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val invitedUsers: Map[UUID, String] = inviteData.notifiedUsers.flatMap { id =>
          userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val answer = Answer(UUID.randomUUID(),
          applicationId, DateTime.now(timeZone),
          inviteData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          invitedUsers,
          false,
          request.currentArea.id,
          false)
        if (applicationService.add(answer)  == 1) {
          notificationsService.newAnswer(application, answer)
          Redirect(routes.ApplicationController.all()).flashing ("success" -> "Les agents ont été invités sur la demande")
        } else {
          InternalServerError("Les agents n'ont pas pu être invité")
        }
    }
  }

  def changeArea(areaId: UUID) = loginAction {  implicit request =>
    Redirect(routes.ApplicationController.all()).withSession(request.session - "areaId" + ("areaId" -> areaId.toString))
  }

  def terminate(applicationId: UUID) = loginAction {  implicit request =>
    applicationService.byId(applicationId) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        if(application.creatorUserId == request.currentUser.id || request.currentUser.admin) {
          if(applicationService.changeStatus(applicationId, "Terminé")) {
            Redirect(routes.ApplicationController.all()).flashing("success" -> "L'application a été indiqué comme terminé")
          } else {
            InternalServerError("Erreur interne: l'application n'a pas pu être indiqué comme terminé")
          }
        } else {
          Unauthorized("Seul le créateur de la demande peut terminé la demande")
        }
    }
  }
}
