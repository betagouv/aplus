package services

import javax.inject.{Inject, Singleton}

import controllers.routes
import models._
import play.api.Logger
import play.api.libs.mailer.MailerClient
import play.api.libs.mailer.Email
import play.api.mvc.{AnyContent, Request}

@Singleton
class NotificationService @Inject()(configuration: play.api.Configuration,
                                    mailerClient: MailerClient,
                                    userService: UserService) {

  private val host = configuration.underlying.getString("app.host")
  private val https = configuration.underlying.getString("app.https") == "true"

  private def sendMail(email: Email) {
    val emailWithText = email.copy(bodyText = email.bodyHtml.map(_.replaceAll("<[^>]*>", "")))
    mailerClient.send(emailWithText)
    Logger.info(s"Email sent to ${email.to.mkString(", ")}")
  }

  def newApplication(application: Application): Unit = {
      application.invitedUsers.keys
        .flatMap(userService.byId)
          .map(generateInvitationEmail(application))
            .foreach(sendMail)
  }

  def newAnswer(application: Application, answer: Answer) = {
    answer.invitedUsers.keys
      .flatMap(userService.byId)
        .map {  user =>
          if(application.invitedUsers.contains(user.id)) {
            generateAnswerEmail(application, answer)(user)
          } else {
            generateInvitationEmail(application, Some(answer))(user)
          }
        }
          .foreach(sendMail)
    if(answer.visibleByHelpers) {
      userService.byId(application.creatorUserId)
       .map(generateAnswerEmail(application, answer))
        .foreach(sendMail)
    }
  }

  def newLoginRequest(request: Request[AnyContent], user: User) = {
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
    val email = play.api.libs.mailer.Email(
      s"Connexion à A+",
      from = "A+ Ne pas répondre <ne-pas-repondre-aplus@beta.gouv.fr>",
      Seq(s"${user.name} <${user.email}>"),
      bodyHtml = Some(bodyHtml)
    )
    sendMail(email)
  }


  private def generateFooter(user: User): String = {
    val delegates = if(user.delegations.nonEmpty) {
      s"- Les personnes suivantes ont une délégation sur votre compte agent : <b>${user.delegations.map { case (name, email) => s"$name &#x3C;$email&#x3E;" }.mkString(", ")}</b>. (Elles peuvent agir en votre nom sur le réseau A+)<br>"
    } else {
      ""
    }
    return s"""<br><br>
       |<b>Ne transférez pas cet email et n'y répondez pas directement.</b><br><i>
       |$delegates
       |- Vous pouvez transférer la demande à un autre agent en ouvrant le lien ci-dessus<br>
       |- Si vous avez un problème ou besoins d'aide à propos de l'outil A+, contactez-nous sur contact@aplus.beta.gouv.fr</i>
     """.stripMargin
  }

  private def generateInvitationEmail(application: Application, answer: Option[Answer] = None)(invitedUser: User): Email = {
    val url = s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}?key=${invitedUser.key}"
    val footer = generateFooter(invitedUser)
    val bodyHtml = s"""Bonjour ${invitedUser.name},<br>
                      |<br>
                      |<p>${answer.map(_.creatorUserName).getOrElse(application.creatorUserName)} a besoin de vous.<br>
                      |Cette personne vous a invité sur la demande suivante: "${application.subject}"
                      |<br><q><i>${application.description}</i></q></p>
                      |Vous pouvez voir la demande et y répondre en suivant ce lien: <br>
                      |<a href="${url}">${url}</a><br>
                      |$footer
                      """.stripMargin
    Email(subject = s"[A+] Nouvelle demande d'aide : ${application.subject}",
      from = "A+ Ne pas répondre <ne-pas-repondre-aplus@beta.gouv.fr>",
      to = List(s"${invitedUser.name} <${invitedUser.email}>"),
      cc = invitedUser.delegations.map { case (name, email) => s"$name <$email>" }.toSeq,
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateAnswerEmail(application: Application, answer: Answer)(user: User): Email = {
    val url = s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}?key=${user.key}"
    val footer = generateFooter(user)
    val bodyHtml = s"""Bonjour ${user.name},<br>
                      |<br>
                      |<p>${answer.creatorUserName} a donné une réponse sur la demande: "${application.subject}"
                      |<br><q><i>${answer.message}</i></q></p>
                      |Vous pouvez voir la demande et y répondre en suivant ce lien: <br>
                      |<a href="${url}">${url}</a>
                      |$footer
                      """.stripMargin
    Email(subject = s"[A+] Nouvelle réponse pour: ${application.subject}",
      from = "A+ Ne pas répondre <ne-pas-repondre-aplus@beta.gouv.fr>",
      to = List(s"${user.name} <${user.email}>"),
      cc = user.delegations.map { case (name, email) => s"$name <$email>" }.toSeq,
      bodyHtml = Some(bodyHtml)
    )
  }
}
