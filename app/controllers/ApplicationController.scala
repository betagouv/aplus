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
import services.{ApplicationService, NotificationsService, UserService}
import utils.{DemoData, UUIDHelper}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class ApplicationController @Inject()(loginAction: LoginAction,
                                      userService: UserService,
                                      applicationService: ApplicationService,
                                      notificationsService: NotificationsService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {
  import forms.Models._

  val applicationForm = Form(
    mapping(
      "subject" -> nonEmptyText,
      "description" -> nonEmptyText,
      "infos" -> FormsPlusMap.map(nonEmptyText),
      "users" -> list(uuid)
    )(ApplicationData.apply)(ApplicationData.unapply)
  )

  def create = loginAction { implicit request =>
    Ok(views.html.createApplication(request.currentUser)(userService.all().filter(_.instructor), applicationForm))
  }

  def createPost = loginAction { implicit request =>
    applicationForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.createApplication(request.currentUser)(userService.all().filter(_.instructor), formWithErrors))
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
          DemoData.argenteuilAreaId)
        if(applicationService.createApplication(application) == 1) {
          notificationsService.newApplication(application)
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre demande a bien été envoyé")
        }  else {
          Redirect(routes.ApplicationController.all()).flashing("error" -> "Votre demande n'a pas pu être envoyé")
        }
      }
    )
  }

  def all = loginAction { implicit request =>
    val currentUserId = request.currentUser.id
    Ok(views.html.allApplication(request.currentUser)(applicationService.allForCreatorUserId(currentUserId), applicationService.allForInvitedUserId(currentUserId)))
  }

  def show(id: UUID) = loginAction { implicit request =>
    //TODO : check access right
    applicationService.byId(id) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        var answers = applicationService.answersByApplicationId(id)
        Ok(views.html.showApplication(request.currentUser)(userService.all().filter(_.instructor), application, answers))
    }
  }

  val answerForm = Form(
    mapping(
      "message" -> nonEmptyText
    )(AnwserData.apply)(AnwserData.unapply)
  )

  def answer(applicationId: UUID) = loginAction { implicit request =>
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
          DemoData.argenteuilAreaId)
        if (applicationService.add(answer) == 1) {
          notificationsService.newAnswer(application, answer)
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre commentaire a bien été envoyé")
        } else {
          InternalServerError("Votre commentaire n'a pas pu être envoyé")
        }
    }
  }

  val inviteForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid)
    )(InviteData.apply)(InviteData.unapply)
  )

  def invite(applicationId: UUID) = loginAction { implicit request =>
    val inviteData = inviteForm.bindFromRequest.get
    applicationService.byId(applicationId) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val invitedUsers: Map[UUID, String] = inviteData.invitedUsers.flatMap { id =>
          userService.byId(id).map(id -> _.nameWithQualite)
        }.toMap
        val answer = Answer(UUID.randomUUID(),
          applicationId, DateTime.now(),
          inviteData.message,
          request.currentUser.id,
          request.currentUser.nameWithQualite,
          invitedUsers,
          false,
          DemoData.argenteuilAreaId)
        if (applicationService.add(answer)  == 1) {
          notificationsService.newAnswer(application, answer)
          Redirect (routes.ApplicationController.all () ).flashing ("success" -> "Les agents A+ ont été invité sur la demande")
        } else {
          InternalServerError("Les agents A+ n'ont pas pu être invité")
        }
    }
  }
}
