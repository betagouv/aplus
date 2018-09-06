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
import services.{ApplicationService, NotificationService, UserService}
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
                                      notificationsService: NotificationService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {
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
    Ok(views.html.createApplication(request.currentUser, request.currentArea)(userService.byArea(request.currentArea.id).filter(_.instructor), applicationForm))
  }

  def createPost = loginAction { implicit request =>
    request.currentUser.helper match {
       case false => {
         Unauthorized("Vous n'avez pas les droits suffisants pour créer une demande. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
       }
       case true => {
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
    }
  }

  def all = loginAction { implicit request =>
    val currentUserId = request.currentUser.id
    val applicationsFromTheArea = if(request.currentUser.admin) { applicationService.allByArea(request.currentArea.id, request.currentUser.admin) } else { List[Application]() }
    Ok(views.html.allApplication(request.currentUser, request.currentArea)(applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin), applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin), applicationsFromTheArea))
  }

  def stats = loginAction { implicit request =>
    request.currentUser.admin match {
      case false =>
        Unauthorized("Vous n'avez pas les droits suffisants pour voir les statistiques. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case true =>
        val allApplications = applicationService.all(request.currentUser.admin)
        val applicationsByArea = allApplications.groupBy(_.area).map{ case (areaId: UUID, applications: Seq[Application]) => (Area.all.find(_.id == areaId).get, applications) }

        val users = userService.all
        import Time.dateTimeOrdering
        val firstDate = allApplications.map(_.creationDate).min.weekOfWeekyear().roundFloorCopy()
        val today = DateTime.now(timeZone).weekOfWeekyear().roundFloorCopy()
        def recursion(date: DateTime): ListMap[String, String] = {
          if(date.isBefore(firstDate)) {
            ListMap()
          } else {
            recursion(date.minusWeeks(1)) + (f"${date.getYear}/${date.getWeekOfWeekyear}%02d" -> date.toString("E dd MMM YYYY", new Locale("fr")))
          }
        }
        val weeks = recursion(today)
        Ok(views.html.stats(request.currentUser, request.currentArea)(weeks, applicationsByArea, users))
    }
  }

  def allAs(userId: UUID) = loginAction { implicit request =>
    (request.currentUser.admin, userService.byId(userId))  match {
      case (false, _) =>
        Unauthorized("Vous n'avez pas le droits de faire ça, vous n'êtes pas administrateur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (true, Some(user)) if user.admin =>
        Unauthorized("Vous n'avez pas le droits de faire ça avec un compte administrateur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (true, Some(user)) if user.areas.contains(request.currentArea.id) =>
        val currentUserId = user.id
        val applicationsFromTheArea = List[Application]()
        Ok(views.html.allApplication(user, request.currentArea)(applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin), applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin), applicationsFromTheArea))
      case  _ =>
        BadRequest("L'utilisateur n'existe pas ou vous n'étes pas dans sa zone. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
    }
  }

  def allCSV = loginAction { implicit request =>
    val currentUserId = request.currentUser.id
    val exportedApplications = if(request.currentUser.admin) {
      applicationService.allByArea(request.currentArea.id, request.currentUser.admin)
    } else {
      (applicationService.allForCreatorUserId(currentUserId, request.currentUser.admin) ++
        applicationService.allForInvitedUserId(currentUserId, request.currentUser.admin)).groupBy(_.id).map(_._2.head)
    }
    val userIds = exportedApplications.flatMap(_.invitedUsers.keys).toSet.toList
    val users = userService.byIds(userIds)
    val date = DateTime.now(timeZone).toString("dd-MMM-YYY-HHhmm", new Locale("fr"))
    Ok(views.html.allApplicationCSV(exportedApplications.toSeq, request.currentUser, users)).as("text/csv").withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-${date}.csv"""" )
  }

  def show(id: UUID) = loginAction { implicit request =>
    applicationService.byId(id, request.currentUser.id, request.currentUser.admin) match {
      case None =>
        NotFound("Nous n'avons pas trouvé cette demande")
      case Some(application) =>
        val currentUser = request.currentUser
        if(currentUser.admin||
          application.invitedUsers.keys.toList.contains(request.currentUser.id)||
          application.creatorUserId==request.currentUser.id) {
            val users = userService.byArea(request.currentArea.id).filterNot(_.id == request.currentUser.id)
              .filter(_.instructor)
            Ok(views.html.showApplication(request.currentUser, request.currentArea)(users, application, answerToAgentsForm))
        }
        else {
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
        BadRequest("Erreur interne, contacter l'administrateur A+ : contact@aplus.beta.gouv.fr")
      },
      answerData => {
        applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
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
              answerData.applicationIsDeclaredIrrelevant,
              Some(Map()))
            if (applicationService.add(applicationId, answer) == 1) {
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
      "users" -> list(uuid),
      "infos" -> FormsPlusMap.map(nonEmptyText.verifying(maxLength(30))),
    )(AnswerToAgentsData.apply)(AnswerToAgentsData.unapply)
  )

  def answerAgents(applicationId: UUID) = loginAction { implicit request =>
    val answerData = answerToAgentsForm.bindFromRequest.get

    applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
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
          Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre réponse a bien été envoyée")
        } else {
          InternalServerError("Votre réponse n'a pas pu être envoyée")
        }
    }
  }

  def invite(applicationId: UUID) = loginAction { implicit request =>
    val inviteData = answerToAgentsForm.bindFromRequest.get
    applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin) match {
      case None =>
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
          Redirect(routes.ApplicationController.all()).flashing ("success" -> "Les agents ont été invités sur la demande")
        } else {
          InternalServerError("Les agents n'ont pas pu être invités")
        }
    }
  }

  def changeArea(areaId: UUID) = loginAction {  implicit request =>
    Redirect(routes.ApplicationController.all()).withSession(request.session - "areaId" + ("areaId" -> areaId.toString))
  }

  def terminate(applicationId: UUID) = loginAction {  implicit request =>
    (request.getQueryString("usefulness"), applicationService.byId(applicationId, request.currentUser.id, request.currentUser.admin)) match {
      case (_, None) =>
        NotFound("Nous n'avons pas trouvé cette demande.")
      case (None, _) =>
        BadGateway("L'utilité de la demande n'est pas présente, il s'agit surement d'une erreur. Vous pouvez contacter l'équipe A+ : contact@aplus.beta.gouv.fr")
      case (Some(usefulness), Some(application)) =>
        if(application.creatorUserId == request.currentUser.id || request.currentUser.admin) {
          if(applicationService.close(applicationId, usefulness, DateTime.now(timeZone))) {
            Redirect(routes.ApplicationController.all()).flashing("success" -> "L'application a été indiqué comme clôturée")
          } else {
            InternalServerError("Erreur interne: l'application n'a pas pu être indiqué comme clôturée")
          }
        } else {
          Unauthorized("Seul le créateur de la demande ou un administrateur peut clore la demande")
        }
    }
  }
}
