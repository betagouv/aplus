package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class ApplicationController @Inject()(implicit val webJarAssets: WebJarAssets) extends Controller {


  def create = Action { implicit request =>
    Ok(views.html.createApplication())
  }

  def createPost = Action { implicit request =>
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre demande a bien été envoyé")
  }

  def all = Action { implicit request =>
    Ok(views.html.allApplication())
  }

  def show = Action { implicit request =>
    Ok(views.html.showApplication())
  }

  def answer = Action { implicit request =>
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Votre commentaire a bien été envoyé")
  }

  def invite = Action { implicit request =>
    Redirect(routes.ApplicationController.all()).flashing("success" -> "Les agents A+ ont été invité sur la demande")
  }
}
