package controllers

import javax.inject.{Inject, Singleton}
import actions.LoginAction
import play.api.Logger
import play.api.mvc._
import play.api.db.Database

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(loginAction: LoginAction, db: Database) extends InjectedController {
  def index = loginAction { implicit request =>
    Redirect(routes.ApplicationController.myApplications())
  }

  def status = Action { implicit request =>
    val connectionValid = try {
      db.withConnection {
        _.isValid(1)
      }
    } catch {
      case throwable: Throwable =>
        Logger.error("Database check error", throwable)
        false
    }
    if(connectionValid){
      Ok("OK")
    } else {
      ServiceUnavailable("Indisponible")
    }
  }
}
