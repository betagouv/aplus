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

  private val daySinceLastAgentAnswerForApplicationsThatShouldBeClosed = 10

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
        minBackoff = 10.seconds,
        maxBackoff = 40.seconds,
        randomFactor = 0.2,
        maxRestarts = 3
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

  def newUser(newUser: Either[UnvalidatedUser, User]) =
    sendMail(generateWelcomeEmail(newUser))

  def newLoginRequest(absoluteUrl: String, path: String, user: User, loginToken: LoginToken) = {
    val url = s"${absoluteUrl}?token=${loginToken.token}&path=$path"
    val bodyHtml = s"""Bonjour ${user.name},<br>
                      |<br>
                      |Vous pouvez maintenant accéder au service Administration+ en cliquant sur le lien suivant :<br>
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

  private def generateWelcomeEmail(user: Either[UnvalidatedUser, User]): Email = {
    val (email, to) = user match {
      case Left(user) =>
        (user.email, List(s"${user.email}"))
      case Right(user) =>
        (user.email, List(s"${quoteEmailPhrase(user.name)} <${user.email}>"))
    }

    val bodyHtml = views.html.emails.welcome(email, tokenExpirationInMinutes).toString()
    Email(
      subject = "[A+] Bienvenue sur Administration+",
      from = from,
      to = to,
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateInvitationEmail(application: Application, answer: Option[Answer] = None)(
      invitedUser: User
  ): Email =
    Email(
      subject = s"[A+] Nouvelle demande d'aide : ${application.subject}",
      from = from,
      to = List(s"${quoteEmailPhrase(invitedUser.name)} <${invitedUser.email}>"),
      bodyHtml = Some(renderEmail(invitationEmailBody(application, answer, invitedUser)))
    )

  private def generateAnswerEmail(application: Application, answer: Answer)(user: User): Email =
    Email(
      subject = s"[A+] Nouvelle réponse pour : ${application.subject}",
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(renderEmail(answerEmailBody(application, answer, user)))
    )

  import scalatags.Text.all._

  private def generateMandatSmsSentEmail(
      mandatId: Mandat.Id,
      user: User
  ): Email = {
    val url: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val subject = s"[A+] Mandat initié par SMS"
    val bodyInner =
      List[Modifier](
        span(
          "Le SMS a bien été envoyé ! Vous recevrez un e-mail dès que l’usager aura répondu. ",
          "Vous pouvez suivre l’échange en cliquant sur ce lien : ",
          a(href := url, url)
        )
      ) ::: applicationEmailFooter
    Email(
      subject = subject,
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(renderEmail(bodyInner))
    )
  }

  private def generateMandatSmsClosedEmail(
      mandatId: Mandat.Id,
      user: User
  ): Email = {
    val url: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val subject = s"[A+] Vous avez reçu une réponse par SMS à votre mandat"
    val bodyInner =
      List[Modifier](
        span(
          "L’usager a répondu à votre demande de mandat. ",
          "L’échange est disponible en cliquant sur ce lien : ",
          a(href := url, url)
        )
      ) ::: applicationEmailFooter
    Email(
      subject = subject,
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(renderEmail(bodyInner))
    )
  }

  private def applicationEmailFooter: List[Modifier] =
    List(
      br,
      br,
      b("Ne transférez pas cet email et n’y répondez pas directement."),
      br,
      i(
        "- Vous pouvez transférer la demande à un autre utilisateur en ouvrant le lien ci-dessus",
        br,
        "- Si vous avez un problème ou besoin d’aide à propos de l’outil Administration+, contactez-nous sur ",
        a(
          href := s"mailto:${Constants.supportEmail}",
          s"${Constants.supportEmail}"
        ),
        br,
        "- Le navigateur Internet Explorer peut rencontrer des difficultés à accéder au site. Microsoft conseille depuis février 2019 de ne plus utiliser son navigateur historique qui n’est plus mis à jour depuis la sortie de Edge en 2015 et ne supporte donc plus les standards actuels du Web. ",
        a(
          href := "https://docs.aplus.beta.gouv.fr/faq/pourquoi-ne-plus-utiliser-le-navigateur-internet-explorer-de-microsoft",
          "Pour en savoir plus"
        )
      )
    )

  private def invitationEmailBody(
      application: Application,
      answer: Option[Answer],
      invitedUser: User
  ): List[Modifier] = {
    val url =
      s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}?key=${invitedUser.key}"
    List[Modifier](
      s"Bonjour ${invitedUser.name},",
      br,
      br,
      p(
        answer.map(_.creatorUserName).getOrElse[String](application.creatorUserName),
        " a besoin de vous.",
        br,
        "Cette personne vous a invité sur la demande suivante : ",
        application.subject,
        br,
        "Vous pouvez voir la demande et y répondre en suivant ce lien : ",
        br,
        a(
          href := url,
          url
        ),
        br
      )
    ) ::: applicationEmailFooter
  }

  private def answerEmailBody(
      application: Application,
      answer: Answer,
      user: User
  ): List[Modifier] = {
    val url =
      s"${routes.ApplicationController.show(application.id).absoluteURL(https, host)}?key=${user.key}#answer-${answer.id}"
    val irrelevantDefaultText: List[Modifier] =
      if (answer.declareApplicationHasIrrelevant)
        List[Modifier](
          answer.creatorUserName.split('(').headOption.getOrElse[String]("L'agent"),
          " a indiqué qu'",
          b("il existe une procédure standard que vous pouvez utiliser pour cette demande"),
          " vous aurez plus de détails dans sa réponse.",
          br,
          br
        )
      else
        Nil
    List[Modifier](
      s"Bonjour ${user.name},",
      br,
      br,
      p(
        answer.creatorUserName,
        " a donné une réponse sur la demande : ",
        application.subject,
        br,
        irrelevantDefaultText,
        "Vous pouvez consulter la réponse, y répondre ou la clôturer en suivant le lien suivant: ",
        br,
        a(
          href := url,
          url
        )
      )
    ) ::: applicationEmailFooter
  }

  private def renderEmail(inner: List[Modifier]): String =
    "<!DOCTYPE html>" + html(
      head(
        meta(
          name := "viewport",
          content := "width=device-width"
        ),
        meta(
          httpEquiv := "Content-Type",
          content := "text/html; charset=UTF-8"
        )
      ),
      body(
        inner: _*
      )
    )

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
          """en appuyant sur le bouton « Clore l’échange »."""
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
        ),
        br,
        br,
        p(
          "Comment avez-vous trouvé cet email ? ",
          "Nous expérimentons. ",
          "Pour nous aider, vous pouvez répondre à ce questionnaire : ",
          a(
            href := "https://startupdetat.typeform.com/to/PicUnQx4",
            "https://startupdetat.typeform.com/to/PicUnQx4"
          )
        ),
        br,
        br,
        p(
          i(
            "Si vous avez un problème ou besoin d’aide à propos de l’outil Administration+, contactez-nous sur ",
            a(
              href := s"mailto:${Constants.supportEmail}",
              s"${Constants.supportEmail}"
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
    // All Application created by this User that are still open
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
