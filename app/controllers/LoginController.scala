package controllers

import javax.inject.{Inject, Singleton}

import models.User
import org.webjars.play.WebJarsUtil
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent, InjectedController, Request}
import services.UserService

@Singleton
class LoginController @Inject()(userService: UserService,
                                mailerClient: MailerClient)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController {

   def home() = Action { implicit request =>
     Ok(views.html.loginHome(None))
   }

   def login() = Action {implicit request =>
     request.body.asFormUrlEncoded.get.get("email").flatMap(_.headOption).flatMap(email => userService.byEmail(email)).fold {
       Redirect(routes.LoginController.home()).flashing("error" -> "Il n'y a pas d'utilisateur avec cette adresse email")
     } { user =>
       sendLoginEmailToUser(request, user)
       Ok(views.html.loginHome(Some(user)))
     }
   }

    private def sendLoginEmailToUser(request: Request[AnyContent], user: User) = {
      val url = s"${routes.HomeController.index().absoluteURL()(request)}?key=${user.key}"
      val bodyHtml = s"""Bonjour ${user.name},<br>
                        |<br>
                        |Vous pouvez vous connecter au service A+ en ouvrant l'adresse suivante :<br>
                        |<a href="${url}">${url}</a>
                        |<br>
                        |<br>
                        |<b>Ce mail est personnel, ne le transférez pas, il permettrait à quelqu'un d'autre d'utiliser votre identité sur le réseau A+.</b>
                        |<br>
                        |<br>
                        |Merci de votre aide,<br>
                        |Si vous avez des questions, n'hésitez pas à nous contacter sur contact@aplus.beta.gouv.fr<br>
                        |Equipe A+""".stripMargin
      val bodyText = bodyHtml.replaceAll("<[^>]*>", "")
      val email = play.api.libs.mailer.Email(
        s"Connexion à A+",
        "A+ <contact@aplus.beta.gouv.fr>",
        Seq(s"${user.name} <${user.email}>"),
        bodyHtml = Some(bodyHtml),
        bodyText = Some(bodyText)
      )
      mailerClient.send(email)
    }

  def disconnect() = Action { implicit request =>
     Redirect(routes.LoginController.home()).withSession(request.session - "userId")
  }
}