package services

import javax.inject.{Inject, Singleton}

import controllers.routes
import models._
import play.api.Logger
import play.api.libs.mailer.MailerClient
import play.api.libs.mailer.Email

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

  private def generateFooter(user: User): String = {
    val delegates = if(user.delegations.nonEmpty) {
      s"Les personnes suivantes ont une délégations sur votre compte agent : ${user.delegations.map { case (name, email) => s"$name <$email>" }.mkString(", ")}. Elles reçoivent une copie de ce mail et peuvent agir en votre nom sur le réseau A+.<br>"
    } else {
      ""
    }
    return s"""<br>
       |<b>Ne transférez pas cet email, il vous est personnellement destiné, il permettrait à quelqu'un d'autre d'utiliser votre identité sur le réseau A+.
       | Si vous souhaitez transférer la demande à quelqu'un d'autre vous avez la possibilité de le faire en ouvrant le lien ci-dessus.</b>
       | $delegates
       |<br>
       |<br>
       |Merci de votre aide.<br>
       |Si vous avez des questions à propos de l'outil A+, n'hésitez pas à contacter l'équipe A+ sur contact@aplus.beta.gouv.fr<br>
       |Equipe A+
     """.stripMargin
  }

  private def generateInvitationEmail(application: Application, answer: Option[Answer] = None)(invitedUser: User): Email = {
    val url = s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}?key=${invitedUser.key}"
    val footer = generateFooter(invitedUser)
    val bodyHtml = s"""Bonjour ${invitedUser.name},<br>
                      |<br>
                      |<p>${answer.map(_.creatorUserName).getOrElse(application.creatorUserName)} a besoin de vous.<br>
                      |Cette personne vous a invité sur la demande suivante: "${application.subject}"<br>
                      |<q>${application.description}</q><p><br>
                      |<br>
                      |Vous pouvez accéder à la demande en suivant ce lien: <br>
                      |<a href="${url}">${url}</a><br>
                      |<br>
                      |$footer
                      """.stripMargin
    Email(subject = s"[A+] Nouvelle demande d'aide : ${application.subject}",
      from = "A+ <contact@aplus.beta.gouv.fr>",
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
                      |<p>${answer.creatorUserName} a donné une réponse sur la demande: "${application.subject}"<br>
                      |<q>${answer.message}</q></p>
                      |<br>
                      |Vous pouvez accéder à l'ensemble de la demande en suivant ce lien: <br>
                      |<a href="${url}">${url}</a>
                      |<br>
                      |$footer
                      """.stripMargin
    Email(subject = s"[A+] Nouvelle réponse pour: ${application.subject}",
      from = "A+ <contact@aplus.beta.gouv.fr>",
      to = List(s"${user.name} <${user.email}>"),
      cc = user.delegations.map { case (name, email) => s"$name <$email>" }.toSeq,
      bodyHtml = Some(bodyHtml)
    )
  }
}
