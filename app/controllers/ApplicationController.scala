package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation._
import play.api.data.validation.Constraints._
import models._
import actions._
import forms.FormsPlusMap
import org.joda.time.DateTime
import org.webjars.play.WebJarsUtil
import services.{ApplicationService, UserService}
import utils.{DemoData, UUIDHelper}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class ApplicationController @Inject()(loginAction: LoginAction, userService: UserService, applicationService: ApplicationService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {
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
        val application = Application(UUIDHelper.randomUUID, "En cours", DateTime.now(), request.currentUser.name, request.currentUser.id, applicationData.subject, applicationData.description, applicationData.infos, applicationData.users, DemoData.argenteuilAreaId)
        applicationService.createApplication(application)
        Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre demande a bien été envoyé")
      }
    )
  }

  def all = loginAction { implicit request =>
    val currentUserId = request.currentUser.id
    Ok(views.html.allApplication(request.currentUser)(applicationService.allForHelperUserId(currentUserId), applicationService.allForInvitedUserId(currentUserId)))
  }

  def show(id: UUID) = loginAction { implicit request =>
    //TODO : check access right
    applicationService.byId(id) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        Ok(views.html.showApplication(request.currentUser)(userService.all().filter(_.instructor), application))
    }
  }

  val answerForm = Form(
    mapping(
      "message" -> nonEmptyText
    )(AnwserData.apply)(AnwserData.unapply)
  )

  def answer(applicationId: UUID) = loginAction { implicit request =>
    val answerData = answerForm.bindFromRequest.get
    val answer = Answer(applicationId, DateTime.now(), answerData.message, request.currentUser, List(), true, DemoData.argenteuilAreaId)
    applicationService.add(answer)
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre commentaire a bien été envoyé")
  }

  val inviteForm = Form(
    mapping(
      "message" -> text,
      "users" -> list(uuid)
    )(InviteData.apply)(InviteData.unapply)
  )

  def invite(applicationId: UUID) = loginAction { implicit request =>
    val inviteData = inviteForm.bindFromRequest.get
    val invitedUsers = inviteData.invitedUsers.flatMap { userService.byId }
    val answer = Answer(applicationId, DateTime.now(), inviteData.message, request.currentUser, invitedUsers, false, DemoData.argenteuilAreaId)
    applicationService.add(answer)
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Les agents A+ ont été invité sur la demande")
  }
}
