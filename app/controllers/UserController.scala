package controllers

import javax.inject.{Inject, Singleton}

import actions.LoginAction
import org.webjars.play.WebJarsUtil
import play.api.mvc.InjectedController
import services.{NotificationsService, UserService}

@Singleton
class UserController @Inject()(loginAction: LoginAction,
                               userService: UserService,
                               notificationsService: NotificationsService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {

  def all = loginAction { implicit request =>
    Ok(views.html.allUsers(request.currentUser)(userService.all()))
  }
}
