package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation._
import models._
import actions._
import forms.FormsPlusMap
import org.joda.time.DateTime
import org.webjars.play.WebJarsUtil
import services.UserService

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class ApplicationController @Inject()(loginAction: LoginAction, userService: UserService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {
  val sabineAuthor = "Sabine, Assistante Sociale de la ville d'Argenteuil"

  var applications = List(
    Application(
      "1",
      "En cours",
      DateTime.now(),
      sabineAuthor,
      "Etat dossier CAF de Mr MARTIN John",
      "Bonjour,\nMr MARTIN John a fait transférer son dossier de la CAF de Marseille à la CAF d'Argenteuil, il ne sait pas où en est sa demande et voudrait savoir à qui envoyer ces documents de suivie.\nSon numéro à la CAF de Marseille est le 98767687, il est né le 16 juin 1985.\n\nMerci de votre aide",
      Map("Numéro de CAF" -> "98767687", "Nom de famille" -> "MARTIN", "Prénom" -> "John", "Date de naissance" -> "16 juin 1985"),
      List("jean"),
      "argenteuil"
    ),
    Application(
      "0",
      "Terminé",
      DateTime.now(),
      sabineAuthor,
      "Demande d'APL de Mme DUPOND Martine",
      "Bonjour,\nMme DUPOND Martine né le 12 juin 1978 a déposé une demande d'APL le 1 octobre.\nPouvez-vous m'indiquez l'état de sa demande ?\n\nMerci de votre réponse",
      Map("Numéro de CAF" -> "38767687", "Nom de famille" -> "DUPOND", "Prénom" -> "Martine", "Date de naissance" -> "12 juin 1978"),
      List("jean"),
      "argenteuil"
    )
  )

  def create = loginAction { implicit request =>
    Ok(views.html.createApplication(request.currentUser)(userService.all().filter(_.instructor)))
  }

  case class ApplicatonData(subject: String, description: String, infos: Map[String, String], users: List[String])
  val applicationForm = Form(
    mapping(
      "subject" -> nonEmptyText,
      "description" -> nonEmptyText,
      "infos" -> FormsPlusMap.map(nonEmptyText),
      "users" -> list(nonEmptyText)
    )(ApplicatonData.apply)(ApplicatonData.unapply)
  )


  def createPost = loginAction { implicit request =>
    val applicationData = applicationForm.bindFromRequest.get
    val application = Application(applications.length.toString, "En cours", DateTime.now(), request.currentUser.name, applicationData.subject, applicationData.description, applicationData.infos, applicationData.users, "argenteuil")
    applications = application :: applications
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre demande a bien été envoyé")
  }

  def all = loginAction { implicit request =>
    Ok(views.html.allApplication(request.currentUser)(applications))
  }

  def show(id: String) = loginAction { implicit request =>
    applications.find(_.id == id) match {
      case None =>
        NotFound("")
      case Some(application) =>
        Ok(views.html.showApplication(request.currentUser)(userService.all().filter(_.instructor), application))
    }
  }

  case class AnwserData(message: String)
  val answerForm = Form(
    mapping(
      "message" -> nonEmptyText
    )(AnwserData.apply)(AnwserData.unapply)
  )

  def answer(applicationId: String) = loginAction { implicit request =>
    val answerData = answerForm.bindFromRequest.get
    val anwser = Answer(applicationId, DateTime.now(), answerData.message, request.currentUser, List(), true, "argenteuil")
    Answer.add(anwser)
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre commentaire a bien été envoyé")
  }

  case class InviteData(message: String, invitedUsers: List[String])
  val inviteForm = Form(
    mapping(
      "message" -> nonEmptyText,
      "users" -> list(nonEmptyText)
    )(InviteData.apply)(InviteData.unapply)
  )

  def invite(applicationId: String) = loginAction { implicit request =>
    val inviteData = inviteForm.bindFromRequest.get
    val invitedUsers = inviteData.invitedUsers.flatMap { userService.byId }
    val answer = Answer(applicationId, DateTime.now(), inviteData.message, request.currentUser, invitedUsers, false, "argenteuil")
    Answer.add(answer)
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Les agents A+ ont été invité sur la demande")
  }
}
