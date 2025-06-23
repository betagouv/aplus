package controllers

import actions.LoginAction
import cats.syntax.all._
import helper.ScalatagsHelpers.writeableOf_Modifier
import javax.inject.{Inject, Singleton}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.Logger
import play.api.db.Database
import play.api.i18n.I18nSupport
import play.api.http.HeaderNames.CONTENT_SECURITY_POLICY
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import play.filters.csp.{CSPConfig, CSPDirective}
import serializers.Keys
import views.home.LoginPanel

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */
@Singleton
class HomeController @Inject() (
    val config: AppConfig,
    val controllerComponents: ControllerComponents,
    cspConfig: CSPConfig,
    db: Database,
    loginAction: LoginAction,
)(implicit webJarsUtil: WebJarsUtil)
    extends BaseController
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
    Action {
      PermanentRedirect("https://docs.aplus.beta.gouv.fr")
    }

  def welcome: Action[AnyContent] =
    loginAction { implicit request =>
      Ok(views.html.welcome(request.currentUser, request.rights))
    }

  def declarationAccessibilite: Action[AnyContent] =
    Action {
      Ok(views.accessibility.declaration())
    }

  def mentionsLegales: Action[AnyContent] =
    Action {
      Ok(views.legal.information())
    }

  def cgu: Action[AnyContent] =
    Action {
      Ok(views.legal.cgu())
    }

  def privacy: Action[AnyContent] =
    Action {
      Ok(views.legal.privacy())
    }

  val contactCSPConfig = {
    val modifiedDirectives: Seq[CSPDirective] = config.zammadChatDomain match {
      case None                   => cspConfig.directives
      case Some(zammadChatDomain) =>
        cspConfig.directives.map {
          case CSPDirective(name, value) if name === "script-src" =>
            val newValue = s"$value https://$zammadChatDomain"
            CSPDirective(name, newValue)
          case CSPDirective(name, value) if name === "style-src" =>
            val newValue = s"$value data: https://$zammadChatDomain"
            CSPDirective(name, newValue)
          case CSPDirective(name, value) if name === "connect-src" =>
            val newValue = s"$value wss://$zammadChatDomain"
            CSPDirective(name, newValue)
          case csp: CSPDirective =>
            csp
        }
    }

    cspConfig.copy(directives = modifiedDirectives)
  }

  /** From
    * https://github.com/playframework/playframework/blob/b6ab62433ba5d63737a913d549a8b58c7fefa6cd/web/play-filters-helpers/src/main/scala/play/filters/csp/CSPProcessor.scala#L80
    */
  val contactCSPHeaderValue = contactCSPConfig.directives
    .map(d => s"${d.name} ${d.value}")
    .mkString("; ")

  def contact: Action[AnyContent] = Action {
    Ok(views.contact.page(config)).withHeaders(
      CONTENT_SECURITY_POLICY -> contactCSPHeaderValue
    )
  }

  def wellKnownSecurityTxt: Action[AnyContent] =
    Action {
      // This is a String, so Play should send back Content-Type: text/plain; charset=UTF-8
      Ok(views.wellKnown.securityTxt())
    }

}
