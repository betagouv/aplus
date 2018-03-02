package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import actions.LoginAction
import extentions.{Hash, UUIDHelper}
import forms.FormsPlusMap
import models.User.date
import models.{Area, User}
import org.joda.time.{DateTime, DateTimeZone}
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, InjectedController}
import services.{NotificationService, UserService}

@Singleton
class UserController @Inject()(loginAction: LoginAction,
                               userService: UserService,
                               notificationsService: NotificationService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {

  def all = loginAction { implicit request =>
    if(request.currentUser.admin != true) {
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      Ok(views.html.allUsers(request.currentUser, request.currentArea)(userService.byArea(request.currentArea.id)))
    }
  }

  private val timeZone = DateTimeZone.forID("Europe/Paris")

  def usersForm(implicit area: Area) = Form(
    single(
      "users" -> list(mapping(
        "id" -> optional(uuid).transform[UUID]({
          case None => UUID.randomUUID()
          case Some(id) => id
        }, { Some(_) }),
        "key" -> ignored("key"),  //TODO refactoring security
        "name" -> nonEmptyText.verifying(maxLength(100)),
        "qualite" -> nonEmptyText.verifying(maxLength(100)),
        "email" -> email.verifying(maxLength(200), nonEmpty),
        "helper" -> boolean,
        "instructor" -> boolean,
        "admin" -> boolean,
        "areas" -> ignored(List(area.id)),
        "creationDate" -> ignored(DateTime.now(timeZone)),
        "hasAcceptedCharte" -> boolean,
        "delegations" -> seq(tuple(
            "name" -> nonEmptyText,
            "email" -> email
          )
        ).transform[Map[String,String]]({ _.toMap }, { _.toSeq })
      )(User.apply)(User.unapply))
    )
  )

  def edit = loginAction { implicit request =>
    if(request.currentUser.admin != true) {
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      implicit val area = request.currentArea
      val users = userService.allDBOnlybyArea(area.id)
      val form = usersForm.fill(users)
      Ok(views.html.editUsers(request.currentUser, request.currentArea)(form, users.length, routes.UserController.editPost()))
    }
  }

  def editPost = loginAction { implicit request =>
    if(request.currentUser.admin != true) {
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      implicit val area = request.currentArea
      usersForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.editUsers(request.currentUser, request.currentArea)(formWithErrors, 0, routes.UserController.editPost()))
        },
        users => {
          if (users.foldRight(true)({ (user, result) => userService.update(user) && result })) {
            Redirect(routes.UserController.all()).flashing("success" -> "Modification sauvegardé")
          } else {
            val form = usersForm.fill(users).withGlobalError("Impossible de mettre à jour certains utilisateurs (Erreur interne)")
            InternalServerError(views.html.editUsers(request.currentUser, request.currentArea)(form, users.length, routes.UserController.editPost()))
          }
        }
      )
    }
  }

  def add = loginAction { implicit request =>
    if(request.currentUser.admin != true) {
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      implicit val area = request.currentArea
      val rows = request.getQueryString("rows").map(_.toInt).getOrElse(1)
      Ok(views.html.editUsers(request.currentUser, request.currentArea)(usersForm, rows, routes.UserController.addPost()))
    }
  }

  def addPost = loginAction { implicit request =>
    if(request.currentUser.admin != true) {
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      implicit val area = request.currentArea
      usersForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.editUsers(request.currentUser, request.currentArea)(formWithErrors, 0, routes.UserController.addPost()))
        },
        users => {
          if (userService.add(users)) {
            Redirect(routes.UserController.all()).flashing("success" -> "Utilisateurs ajouté")
          } else {
            val form = usersForm.fill(users).withGlobalError("Impossible d'ajouté les utilisateurs (Erreur interne)")
            InternalServerError(views.html.editUsers(request.currentUser, request.currentArea)(form, users.length, routes.UserController.addPost()))
          }
        }
      )
    }
  }

  def showCharte() = loginAction { implicit request =>
    Ok(views.html.showCharte(request.currentUser, request.currentArea))
  }

  def validateCharte() = loginAction { implicit request =>
    Form(
      tuple(
        "redirect" -> optional(text),
        "validate" -> boolean
      )
    ).bindFromRequest.fold(
      formWithErrors => {
        BadRequest(s"Formulaire invalide, prévenez l'administrateur du service. ${formWithErrors.errors.mkString(", ")}")
      },
      form => {
        if(form._2) {
          userService.acceptCharte(request.currentUser.id)
        }
        form._1 match {
          case Some(redirect) =>
            Redirect(Call("GET", redirect)).flashing("success" -> "Merci d\'avoir accepté la charte")
          case _ =>
            Redirect(routes.ApplicationController.all()).flashing("success" -> "Merci d\'avoir accepté la charte")
        }
      }
    )

  }
}
