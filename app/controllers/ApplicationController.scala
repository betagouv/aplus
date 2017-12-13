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
import org.joda.time.DateTime
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

  val applicationForm = Form(
    mapping(
      "subject" -> nonEmptyText,
      "description" -> nonEmptyText,
      "infos" -> FormsPlusMap.map(nonEmptyText),
      "users" -> list(uuid).verifying("Vous devez sélectionnez au moins un agent", _.nonEmpty)
    )(ApplicationData.apply)(ApplicationData.unapply)
  )

  def create = loginAction { implicit request =>
    Ok(views.html.createApplication(request.currentUser, request.currentArea)(userService.byArea(request.currentArea.id).filter(_.instructor), applicationForm))
  }

  def createPost = loginAction { implicit request =>
    applicationForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.createApplication(request.currentUser, request.currentArea)(userService.byArea(request.currentArea.id).filter(_.instructor), formWithErrors))
      },
      applicationData => {
        val invitedUsers: Map[UUID, String] = applicationData.users.flatMap {  id =>
            userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val application = Application(UUIDHelper.randomUUID,
          "En cours",
          DateTime.now(),
          request.currentUser.nameWithQualite,
          request.currentUser.id,
          applicationData.subject,
          applicationData.description,
          applicationData.infos,
          invitedUsers,
          request.currentArea.id)
        if(applicationService.createApplication(application)) {
          notificationsService.newApplication(application)
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre demande a bien été envoyé")
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

  val answerForm = Form(
    mapping(
      "message" -> nonEmptyText,
      "users" -> list(uuid)
    )(AnswerData.apply)(AnswerData.unapply)
  )

  def answerHelper(applicationId: UUID) = loginAction { implicit request =>
    val answerData = answerForm.bindFromRequest.get

    applicationService.byId(applicationId) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val answer = Answer(UUID.randomUUID(),
          applicationId, DateTime.now(),
          answerData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          Map(),
          true,
          request.currentArea.id)
        if (applicationService.add(answer) == 1) {
          notificationsService.newAnswer(application, answer)
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre réponse a bien été envoyé")
        } else {
          InternalServerError("Votre réponse n'a pas pu être envoyé")
        }
    }
  }

  def answerAgents(applicationId: UUID) = loginAction { implicit request =>
    val answerData = answerForm.bindFromRequest.get

    applicationService.byId(applicationId) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val notifiedUsers: Map[UUID, String] = answerData.notifiedUsers.flatMap { id =>
          userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val answer = Answer(UUID.randomUUID(),
          applicationId, DateTime.now(),
          answerData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          notifiedUsers,
          request.currentUser.id != application.creatorUserId,
          request.currentArea.id)
        if (applicationService.add(answer) == 1) {
          notificationsService.newAnswer(application, answer)
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre réponse a bien été envoyé")
        } else {
          InternalServerError("Votre réponse n'a pas pu être envoyé")
        }
    }
  }

  def invite(applicationId: UUID) = loginAction { implicit request =>
    val inviteData = answerForm.bindFromRequest.get
    applicationService.byId(applicationId) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val invitedUsers: Map[UUID, String] = inviteData.notifiedUsers.flatMap { id =>
          userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val answer = Answer(UUID.randomUUID(),
          applicationId, DateTime.now(),
          inviteData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          invitedUsers,
          false,
          request.currentArea.id)
        if (applicationService.add(answer)  == 1) {
          notificationsService.newAnswer(application, answer)
          Redirect(routes.ApplicationController.all()).flashing ("success" -> "Les agents A+ ont été invité sur la demande")
        } else {
          InternalServerError("Les agents A+ n'ont pas pu être invité")
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
        if(application.creatorUserId == request.currentUser.id) {
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
