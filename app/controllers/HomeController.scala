package controllers

import javax.inject.{Inject, Singleton}
import actions.LoginAction
import org.webjars.play.WebJarsUtil
import play.api.Logger
import play.api.mvc._
import play.api.db.Database
import views.home.LoginPanel

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject() (loginAction: LoginAction, db: Database)(
    implicit webJarsUtil: WebJarsUtil
) extends InjectedController
    with play.api.i18n.I18nSupport {

  private val log = Logger(classOf[HomeController])

  def index: Action[AnyContent] = Action { implicit request =>
    if (request.session
          .get("userId")
          .orElse(request.queryString.get("token"))
          .orElse(request.queryString.get("key"))
          .isDefined)
      TemporaryRedirect(
        s"${routes.ApplicationController.myApplications()}?${request.rawQueryString}"
      )
    else
      Ok(views.html.home.page(LoginPanel.ConnectionForm))
  }

  def status: Action[AnyContent] = Action {
    val connectionValid =
      try {
        db.withConnection {
          _.isValid(1)
        }
      } catch {
        case throwable: Throwable =>
          log.error("Database check error", throwable)
          false
      }
    if (connectionValid) {
      Ok("OK")
    } else {
      ServiceUnavailable("Indisponible")
    }
  }

  def help: Action[AnyContent] = loginAction { implicit request =>
    Ok(views.html.help(request.currentUser, request.rights))
  }
}
