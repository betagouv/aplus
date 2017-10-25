package controllers

import javax.inject.{Inject, Singleton}

import actions.LoginAction
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(loginAction: LoginAction, implicit val webJarAssets: WebJarAssets) extends Controller {

  def index = loginAction { implicit request =>
    Redirect(routes.ApplicationController.all())
  }
}
