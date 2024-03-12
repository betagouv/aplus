package controllers

import actions.LoginAction
import helper.ScalatagsHelpers.writeableOf_Modifier
import javax.inject.{Inject, Singleton}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.Logger
import play.api.db.Database
import play.api.i18n.I18nSupport
import play.api.mvc._
import serializers.Keys
import views.home.LoginPanel

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */
@Singleton
class HomeController @Inject() (
    val config: AppConfig,
    loginAction: LoginAction,
    db: Database,
)(implicit webJarsUtil: WebJarsUtil)
    extends InjectedController
    with I18nSupport
    with Operators.Common {

  private val log = Logger(classOf[HomeController])

  def index: Action[AnyContent] =
    Action { implicit request =>
      val needsRedirect =
        request.session.get(Keys.Session.userId).isDefined ||
          request.queryString.get(Keys.QueryParam.token).isDefined ||
          request.queryString.get(Keys.QueryParam.key).isDefined
      if (needsRedirect)
        TemporaryRedirect(
          s"${routes.ApplicationController.myApplications}?${request.rawQueryString}"
        )
      else
        request.flash.get("email") match {
          case None =>
            Ok(views.html.home.page(LoginPanel.ConnectionForm))
          case Some(email) =>
            Ok(
              views.html.home.page(LoginPanel.SendbackEmailForm(email, request.flash.get("error")))
            )
        }
    }

  def status: Action[AnyContent] =
    Action {
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

  def help: Action[AnyContent] =
    loginAction { implicit request =>
      Ok(views.html.help(request.currentUser, request.rights))
    }

  def welcome: Action[AnyContent] =
    loginAction { implicit request =>
      Ok(views.html.welcome(request.currentUser, request.rights))
    }

  def declarationAccessibilite: Action[AnyContent] =
    Action {
      Ok(views.accessibility.declaration())
    }

}
