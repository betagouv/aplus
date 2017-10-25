package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(implicit val webJarAssets: WebJarAssets) extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.home(webJarAssets))
  }
}
