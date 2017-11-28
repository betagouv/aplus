package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import actions.LoginAction
import models.User
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc.InjectedController
import services.{NotificationsService, UserService}

@Singleton
class UserController @Inject()(loginAction: LoginAction,
                               userService: UserService,
                               notificationsService: NotificationsService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {

  def all = loginAction { implicit request =>
    Ok(views.html.allUsers(request.currentUser)(userService.all()))
  }

  val usersForm = Form(
    tuple(
      "users" -> list(mapping(
        "id" -> default(uuid, UUID.randomUUID()),
        "key" -> ignored("key"),  //TODO refactoring security
        "name" -> nonEmptyText,
        "qualite" -> nonEmptyText,
        "email" -> email.verifying(nonEmpty),
        "helper" -> boolean,
        "instructor" -> boolean,
        "admin" -> boolean,
        "areas" -> list(uuid)
      )(User.apply)(User.unapply)),
      "add-rows" -> optional(number)
    )
  )

  def edit = loginAction { implicit request =>
    val users = userService.all()
    val form = usersForm.fill((users, None))
    Ok(views.html.editUsers(request.currentUser)(form, users.length))
  }

  def editPost = loginAction { implicit request =>
    usersForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.editUsers(request.currentUser)(formWithErrors, 0))
      },
      {
        case (users, Some(newRow)) =>
          val form = usersForm.fill((users, None))
          Ok(views.html.editUsers(request.currentUser)(form, newRow + users.length))
        case (users, _) =>
        Redirect(routes.UserController.all()).flashing("success" -> "Modification sauvegardé")
      }
    )
  }
}
