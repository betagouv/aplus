package services

import java.util.UUID

import constants.Constants
import javax.inject.{Inject, Singleton}
import controllers.routes
import models._
import play.api.Logger
import play.api.libs.mailer.MailerClient
import play.api.libs.mailer.Email
import play.api.mvc.{AnyContent, Request}

@Singleton
class NotificationService @Inject() (
    configuration: play.api.Configuration,
    mailerClient: MailerClient,
    userService: UserService,
    eventService: EventService,
    groupService: UserGroupService
) {

  private lazy val tokenExpirationInMinutes =
    configuration.underlying.getInt("app.tokenExpirationInMinutes")

  private val host = configuration.underlying.getString("app.host")
  private val https = configuration.underlying.getString("app.https") == "true"

  private val from = s"Administration+ <${Constants.supportEmail}>"

  private def sendMail(email: Email) {
    val emailWithText = email.copy(
      bodyText = email.bodyHtml.map(_.replaceAll("<[^>]*>", "")),
      headers = email.headers ++ Set(
        "X-MJ-MonitoringCategory" -> "aplus",
        "X-Mailjet-TrackClick" -> "0",
        "X-MAILJET-TRACKOPEN" -> "0"
      )
    )
    mailerClient.send(emailWithText)
    Logger.info(s"Email sent to ${email.to.mkString(", ")}")
  }

  def newApplication(application: Application): Unit = {
    val userIds = (application.invitedUsers).keys
    val users = userService.byIds(userIds.toList)
    val groups = groupService
      .byIds(users.flatMap(_.groupIds))
      .filter(_.email.nonEmpty)
      .filter(_.areaIds.contains(application.area))

    users
      .map(generateInvitationEmail(application))
      .foreach(sendMail)

    groups
      .map(generateNotificationBALEmail(application, None, users))
      .foreach(sendMail)
  }

  def newAnswer(application: Application, answer: Answer) = {
    // Retrieve data
    val userIds = (application.invitedUsers ++ answer.invitedUsers).keys
    val users = userService.byIds(userIds.toList)
    val groups = groupService
      .byIds(users.flatMap(_.groupIds))
      .filter(_.email.nonEmpty)
      .filter(_.areaIds.contains(application.area))
    val groupIds = groups.map(_.id)

    // Send emails to users
    users
      .flatMap { user =>
        if (user.id == answer.creatorUserID) {
          None
        } else if (answer.invitedUsers.contains(user.id)) {
          Some(generateInvitationEmail(application, Some(answer))(user))
        } else {
          Some(generateAnswerEmail(application, answer)(user))
        }
      }
      .foreach(sendMail)

    // Send emails to groups
    val oldGroupIds: List[UUID] =
      users.filter(user => application.invitedUsers.contains(user.id)).flatMap(_.groupIds)
    groups
      .filter(group => oldGroupIds.contains(group.id))
      .map(generateNotificationBALEmail(application, Some(answer), users))
      .foreach(sendMail)
    groups
      .filter(group => !oldGroupIds.contains(group.id))
      .map(generateNotificationBALEmail(application, None, users))
      .foreach(sendMail)

    if (answer.visibleByHelpers && answer.creatorUserID != application.creatorUserId) {
      userService
        .byId(application.creatorUserId)
        .map(generateAnswerEmail(application, answer))
        .foreach(sendMail)
    }
  }

  def newUser(newUser: User) =
    sendMail(generateWelcomeEmail(newUser))

  def newLoginRequest(absoluteUrl: String, path: String, user: User, loginToken: LoginToken) = {
    val url = s"${absoluteUrl}?token=${loginToken.token}&path=$path"
    val bodyHtml = s"""Bonjour ${user.name},<br>
                      |<br>
                      |Vous pouvez maintenant accèder au service Administration+ en cliquant sur le lien suivant :<br>
                      |<a href="${url}">${url}</a>
                      |<br>
                      |<br>
                      |Si vous avez des questions ou vous rencontrez un problème, n'hésitez pas à nous contacter sur <a href="mailto:${Constants.supportEmail}">${Constants.supportEmail}</a><br>
                      |Equipe Administration+""".stripMargin
    val email = play.api.libs.mailer.Email(
      s"Connexion à Administration+",
      from = from,
      Seq(s"${user.name} <${user.email}>"),
      bodyHtml = Some(bodyHtml)
    )
    sendMail(email)
  }

  private def generateFooter(user: User): String =
    s"""<br><br>
       |<b>Ne transférez pas cet email et n'y répondez pas directement.</b><br><i>
       |
       |- Vous pouvez transférer la demande à un autre utilisateur en ouvrant le lien ci-dessus<br>
       |- Si vous avez un problème ou besoin d'aide à propos de l'outil Administration+, contactez-nous sur <a href="mailto:${Constants.supportEmail}">${Constants.supportEmail}</a></i>
     """.stripMargin

  private def generateNotificationBALEmail(
      application: Application,
      answerOption: Option[Answer] = None,
      users: List[User]
  )(group: UserGroup): Email = {
    val (subject, url) = answerOption match {
      case Some(answer) =>
        (
          s"[A+] Nouvelle réponse : ${application.subject}",
          s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}#answer-${answer.id}"
        )
      case None =>
        (
          s"[A+] Nouvelle demande : ${application.subject}",
          s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}"
        )
    }
    val bodyHtml =
      views.html.emails.notificationBAL(application, answerOption, group, users, url).toString()
    Email(
      subject = subject,
      from = from,
      to = List(s"${group.name} <${group.email.get}>"),
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateWelcomeEmail(user: User): Email = {
    val bodyHtml = views.html.emails.welcome(user, tokenExpirationInMinutes).toString()
    Email(
      subject = "[A+] Bienvenue sur Administration+",
      from = from,
      to = List(s"${user.name} <${user.email}>"),
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateInvitationEmail(application: Application, answer: Option[Answer] = None)(
      invitedUser: User
  ): Email = {
    val url =
      s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}?key=${invitedUser.key}"
    val footer = generateFooter(invitedUser)
    val bodyHtml = s"""Bonjour ${invitedUser.name},<br>
                      |<br>
                      |<p>${answer
                        .map(_.creatorUserName)
                        .getOrElse(application.creatorUserName)} a besoin de vous.<br>
                      |Cette personne vous a invité sur la demande suivante : "${application.subject}"
                      |Vous pouvez voir la demande et y répondre en suivant ce lien : <br>
                      |<a href="${url}">${url}</a><br>
                      |$footer
                      """.stripMargin
    Email(
      subject = s"[A+] Nouvelle demande d'aide : ${application.subject}",
      from = from,
      to = List(s"${invitedUser.name} <${invitedUser.email}>"),
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateAnswerEmail(application: Application, answer: Answer)(user: User): Email = {
    val url =
      s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}?key=${user.key}#answer-${answer.id}"
    val footer = generateFooter(user)
    val defaultProcessText = if (answer.declareApplicationHasIrrelevant) {
      s"<br>${answer.creatorUserName.split('(').headOption.getOrElse("L'agent")} a indiqué qu'<b>il existe une procédure standard que vous pouvez utiliser pour cette demande</b>, vous aurez plus de détails dans sa réponse.<br><br>"
    } else {
      ""
    }
    val bodyHtml = s"""Bonjour ${user.name},<br>
                      |<br>
                      |<p>${answer.creatorUserName} a donné une réponse sur la demande: "${application.subject}"
                      |${defaultProcessText}
                      |Vous pouvez consulter la réponse, y répondre ou la clôturer en suivant le lien suivant: <br>
                      |<a href="${url}">${url}</a>
                      |$footer
                      """.stripMargin
    Email(
      subject = s"[A+] Nouvelle réponse pour : ${application.subject}",
      from = from,
      to = List(s"${user.name} <${user.email}>"),
      bodyHtml = Some(bodyHtml)
    )
  }
}
