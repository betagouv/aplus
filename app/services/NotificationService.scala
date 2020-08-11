package services

import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import constants.Constants
import java.util.UUID
import javax.inject.{Inject, Singleton}
import controllers.routes
import helper.EmailHelper.quoteEmailPhrase
import helper.Time
import models._
import models.mandat.Mandat
import play.api.Logger
import play.api.libs.concurrent.MaterializerProvider
import play.api.libs.mailer.MailerClient
import play.api.libs.mailer.Email
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object NotificationService {
  case class WeeklyEmailInfos(user: User, applicationsThatShouldBeClosed: List[Application])
}

@Singleton
class NotificationService @Inject() (
    applicationService: ApplicationService,
    configuration: play.api.Configuration,
    dependencies: ServicesDependencies,
    eventService: EventService,
    groupService: UserGroupService,
    mailerClient: MailerClient,
    materializerProvider: MaterializerProvider,
    userService: UserService
)(implicit executionContext: ExecutionContext) {
  import NotificationService._

  implicit val materializer: Materializer = materializerProvider.get

  private val log = Logger(classOf[NotificationService])

  private lazy val tokenExpirationInMinutes =
    configuration.underlying.getInt("app.tokenExpirationInMinutes")

  private val daySinceLastAgentAnswerForApplicationsThatShouldBeClosed = 15

  private val maxNumberOfWeeklyEmails: Long =
    configuration.get[Long]("app.weeklyEmailsMaxNumber")

  private val host: String = {
    def readHerokuAppNameOrThrow: String =
      configuration.get[Option[String]]("app.herokuAppName") match {
        case None =>
          throw new Exception(
            "Impossible de lire l'une des variables APP_HOST ou HEROKU_APP_NAME"
          )
        case Some(herokuAppName) => s"${herokuAppName}.herokuapp.com"
      }
    configuration.get[Option[String]]("app.host") match {
      case None => readHerokuAppNameOrThrow
      // Temporary check, remove when APP_HOST is not mandatory on Heroku
      case Some(appHost) if appHost.contains("invalid") => readHerokuAppNameOrThrow
      case Some(appHost)                                => appHost
    }
  }

  private val https = configuration.underlying.getString("app.https") == "true"

  private val from = s"Administration+ <${Constants.supportEmail}>"

  // TODO: seems to be blocking
  // https://github.com/playframework/play-mailer/blob/7.0.x/play-mailer/src/main/scala/play/api/libs/mailer/MailerClient.scala#L15
  private def sendMail(email: Email): Unit = {
    val emailWithText = email.copy(
      bodyText = email.bodyHtml.map(_.replaceAll("<[^>]*>", "")),
      headers = email.headers ++ Set(
        "X-MJ-MonitoringCategory" -> "aplus",
        "X-Mailjet-TrackClick" -> "0",
        "X-MAILJET-TRACKOPEN" -> "0"
      )
    )
    mailerClient.send(emailWithText)
    log.info(s"Email sent to ${email.to.mkString(", ")}")
  }

  // Non blocking, will apply a backoff if the `Future` is `Failed`
  // (ie if `mailerClient.send` throws)
  //
  // Doc for the exponential backoff
  // https://doc.akka.io/docs/akka/current/stream/stream-error.html#delayed-restarts-with-a-backoff-operator
  //
  // Note: we might want to use a queue as the inner source, and enqueue emails in it.
  private def sendEmail(email: Email): Future[Unit] =
    RestartSource
      .onFailuresWithBackoff(
        minBackoff = 3.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2,
        maxRestarts = 5
      ) { () =>
        Source.future {
          // `sendMail` is executed on the `dependencies.mailerExecutionContext` thread pool
          Future(sendMail(email))(dependencies.mailerExecutionContext)
        }
      }
      .runWith(Sink.last)

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
      Seq(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(bodyHtml)
    )
    sendMail(email)
  }

  def mandatSmsSent(mandatId: Mandat.Id, user: User): Unit =
    sendMail(generateMandatSmsSentEmail(mandatId, user))

  def mandatSmsClosed(mandatId: Mandat.Id, user: User): Unit =
    sendMail(generateMandatSmsClosedEmail(mandatId, user))

  private def generateFooter(user: User): String =
    s"""<br><br>
       |<b>Ne transférez pas cet email et n'y répondez pas directement.</b><br><i>
       |
       |- Vous pouvez transférer la demande à un autre utilisateur en ouvrant le lien ci-dessus<br>
       |- Si vous avez un problème ou besoin d'aide à propos de l'outil Administration+, contactez-nous sur <a href="mailto:${Constants.supportEmail}">${Constants.supportEmail}</a></i>
     """.stripMargin

  private def generateNotificationBALEmail(
      application: Application,
      answerOption: Option[Answer],
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
      to = List(s"${quoteEmailPhrase(group.name)} <${group.email.get}>"),
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateWelcomeEmail(user: User): Email = {
    val bodyHtml = views.html.emails.welcome(user, tokenExpirationInMinutes).toString()
    Email(
      subject = "[A+] Bienvenue sur Administration+",
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
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
      to = List(s"${quoteEmailPhrase(invitedUser.name)} <${invitedUser.email}>"),
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
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(bodyHtml)
    )
  }

  // TODO: footer
  import scalatags.Text.all._

  private def generateMandatSmsSentEmail(
      mandatId: Mandat.Id,
      user: User
  ): Email = {
    val url: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val subject = s"[A+] Mandat initié par SMS"
    val bodyHtml =
      span(
        "Le SMS a bien été envoyé ! Vous recevrez un e-mail dès que l’usager aura répondu. ",
        "Vous pouvez suivre l’échange en cliquant sur ce lien : ",
        a(href := url, url)
      )
    Email(
      subject = subject,
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(bodyHtml.toString)
    )
  }

  private def generateMandatSmsClosedEmail(
      mandatId: Mandat.Id,
      user: User
  ): Email = {
    val url: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val subject = s"[A+] Vous avez reçu une réponse par SMS à votre mandat"
    val bodyHtml =
      span(
        "L’usager a répondu à votre demande de mandat. ",
        "L’échange est disponible en cliquant sur ce lien : ",
        a(href := url, url)
      )
    Email(
      subject = subject,
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(bodyHtml.toString)
    )
  }

  def weeklyEmails(): Future[Unit] =
    Source
      .future(userService.allNotDisabled)
      // Stream[List[User]] => Stream[User]
      .mapConcat(identity)
      // Sequential
      .mapAsync(parallelism = 1)(fetchWeeklyEmailInfos)
      .filter(infos => infos.applicationsThatShouldBeClosed.nonEmpty)
      .take(maxNumberOfWeeklyEmails)
      .mapAsync(parallelism = 1)(sendWeeklyEmail)
      // On `Failure` continue with next element
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      // Count
      .runWith(Sink.fold(0)((acc, _) => acc + 1))
      .map { count: Int =>
        eventService.info(
          User.systemUser,
          "",
          EventType.WeeklyEmailsSent.code,
          s"Les emails hebdomadaires ont été envoyés ($count)",
          None,
          None,
          None
        )
      }

  private def sendWeeklyEmail(infos: WeeklyEmailInfos): Future[Unit] = {
    val subject = "[A+] Votre récapitulatif hebdomadaire"
    val datePattern = "EEEE dd MMMM YYYY HH'h'mm"
    val bodyInner =
      List[Modifier](
        s"Bonjour ${infos.user.name},",
        br,
        br,
        p(
          s"Vous avez ${infos.applicationsThatShouldBeClosed.size} ",
          (
            if (infos.applicationsThatShouldBeClosed.size <= 1)
              "demande qui a reçu une réponse il y a "
            else
              "demandes qui ont reçu une réponse il y a ",
          ),
          s"plus de $daySinceLastAgentAnswerForApplicationsThatShouldBeClosed jours. ",
          "Si votre échange est terminé, n’hésitez pas à ",
          (
            if (infos.applicationsThatShouldBeClosed.size <= 1)
              "la clore, "
            else
              "les clore, "
          ),
          """en appuyant sur le bouton "Clore la demande"."""
        ),
        ul(
          infos.applicationsThatShouldBeClosed.map(application =>
            li(
              a(
                href := routes.ApplicationController.show(application.id).absoluteURL(https, host),
                s"Demande du ${Time.formatPatternFr(application.creationDate, datePattern)}"
              )
            )
          )
        )
      )
    val email = Email(
      subject = subject,
      from = from,
      to = List(s"${quoteEmailPhrase(infos.user.name)} <${infos.user.email}>"),
      bodyHtml = Some(div(bodyInner).toString)
    )
    sendEmail(email)
  }

  private def fetchWeeklyEmailInfos(user: User): Future[WeeklyEmailInfos] =
    applicationService.allOpenAndCreatedByUserIdAnonymous(user.id).map { opened =>
      val applicationsThatShouldBeClosed = opened.filter(application =>
        application.answers.lastOption match {
          case None => false
          case Some(lastAnswer) =>
            if ((lastAnswer.creatorUserID: UUID) == (user.id: UUID)) {
              false
            } else {
              lastAnswer.ageInDays > daySinceLastAgentAnswerForApplicationsThatShouldBeClosed
            }
        }
      )
      WeeklyEmailInfos(
        user = user,
        applicationsThatShouldBeClosed = applicationsThatShouldBeClosed
      )
    }

}
