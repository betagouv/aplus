package services

import java.util.UUID

import akka.stream.scaladsl.{RestartSource, Sink, Source}
import akka.stream.{ActorAttributes, Materializer, RestartSettings, Supervision}
import cats.syntax.all._
import constants.Constants
import controllers.routes
import helper.EmailHelper.quoteEmailPhrase
import java.time.ZoneId
import javax.inject.{Inject, Singleton}
import models._
import models.mandat.Mandat
import play.api.Logger
import play.api.libs.concurrent.MaterializerProvider
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.Request
import views.emails.{common, WeeklyEmailInfos}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

  implicit val materializer: Materializer = materializerProvider.get

  private val log = Logger(classOf[NotificationService])

  private lazy val tokenExpirationInMinutes =
    configuration.get[Int]("app.tokenExpirationInMinutes")

  // This blacklist if mainly for experts who do not need emails
  // Note: be careful with the empty string
  private lazy val notificationEmailBlacklist: Set[String] =
    configuration
      .get[String]("app.notificationEmailBlacklist")
      .split(",")
      .map(_.trim)
      .filterNot(_.isEmpty)
      .toSet

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

  private val https = configuration.underlying.getString("app.https") === "true"

  private val from = s"Administration+ <${Constants.supportEmail}>"

  private def emailIsBlacklisted(email: Email): Boolean =
    notificationEmailBlacklist.exists(black => email.to.exists(_.contains(black)))

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
    if (emailIsBlacklisted(email) && (email.subject =!= common.magicLinkSubject)) {
      log.info(s"Did not send email to ${email.to.mkString(", ")} because it is in the blacklist")
    } else {
      mailerClient.send(emailWithText)
      log.info(s"Email sent to ${email.to.mkString(", ")}")
    }
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
        RestartSettings(
          minBackoff = 10.seconds,
          maxBackoff = 40.seconds,
          randomFactor = 0.2
        )
          .withMaxRestarts(count = 3, within = 10.seconds)
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
      .byIds(application.invitedGroupIdsAtCreation)
      .filter(_.email.nonEmpty)

    users
      .map(generateInvitationEmail(application))
      .foreach(sendMail)

    groups
      .map(generateNotificationBALEmail(application, None, users))
      .foreach(sendMail)
  }

  // Note: application does not contain answer at this point
  def newAnswer(application: Application, answer: Answer) = {
    // Retrieve data
    val userIds = (application.invitedUsers ++ answer.invitedUsers).keys
    val users = userService.byIds(userIds.toList)
    val (allGroups, alreadyPresentGroupIds): (List[UserGroup], Set[UUID]) =
      // This legacy case can be removed once data has been fixed
      if (application.isWithoutInvitedGroupIdsLegacyCase) {
        (
          groupService
            .byIds(users.flatMap(_.groupIds))
            .filter(_.email.nonEmpty)
            .filter(_.areaIds.contains(application.area)),
          users.filter(user => application.invitedUsers.contains(user.id)).flatMap(_.groupIds).toSet
        )
      } else {
        val allGroupIds = application.invitedGroups.union(answer.invitedGroupIds.toSet)
        (
          groupService
            .byIds(allGroupIds.toList)
            .filter(_.email.nonEmpty),
          application.invitedGroups
        )
      }

    // Send emails to users
    users
      .flatMap { user =>
        if (user.id === answer.creatorUserID) {
          None
        } else if (answer.invitedUsers.contains(user.id)) {
          Some(generateInvitationEmail(application, Some(answer))(user))
        } else {
          Some(generateAnswerEmail(application, answer)(user))
        }
      }
      .foreach(sendMail)

    // Send emails to groups
    allGroups
      .collect {
        case group if alreadyPresentGroupIds.contains(group.id) =>
          generateNotificationBALEmail(application, answer.some, users)(group)
        case group if !alreadyPresentGroupIds.contains(group.id) =>
          generateNotificationBALEmail(application, Option.empty[Answer], users)(group)
      }
      .foreach(sendMail)

    if (answer.visibleByHelpers && answer.creatorUserID =!= application.creatorUserId) {
      userService
        .byId(application.creatorUserId)
        .map(generateAnswerEmail(application, answer))
        .foreach(sendMail)
    }
  }

  def newUser(newUser: User) =
    sendMail(generateWelcomeEmail(newUser.name.some, newUser.email))

  def newSignup(signup: SignupRequest)(implicit request: Request[_]) =
    Try(sendMail(generateWelcomeEmail(none, signup.email)))
      .recover { case e =>
        eventService.logErrorNoUser(
          Error.MiscException(
            EventType.SignupEmailError,
            s"Impossible d'envoyer l'email de bienvenue à ${signup.email} pour la préinscription ${signup.id}",
            e
          )
        )
      }

  def newMagicLinkEmail(
      userName: Option[String],
      userEmail: String,
      userTimeZone: ZoneId,
      loginToken: LoginToken,
      pathToRedirectTo: String
  ) = {
    val absoluteUrl: String =
      routes.LoginController.magicLinkAntiConsumptionPage.absoluteURL(https, host)
    val bodyInner = common.magicLinkBody(
      userName,
      userTimeZone,
      loginToken,
      absoluteUrl,
      pathToRedirectTo,
      tokenExpirationInMinutes
    )
    val email = Email(
      subject = common.magicLinkSubject,
      from = from,
      to = List(
        userName
          .filter(_.nonEmpty)
          .map(name => s"${quoteEmailPhrase(name)} <${userEmail}>")
          .getOrElse(userEmail)
      ),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    sendMail(email)
  }

  def mandatSmsSent(mandatId: Mandat.Id, user: User): Unit = {
    val absoluteUrl: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val bodyInner = common.mandatSmsSentBody(absoluteUrl)
    val email = Email(
      subject = common.mandatSmsSentSubject,
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    sendMail(email)
  }

  def mandatSmsClosed(mandatId: Mandat.Id, user: User): Unit = {
    val absoluteUrl: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val bodyInner = common.mandatSmsSentBody(absoluteUrl)
    val email = Email(
      subject = common.mandatSmsClosedSubject,
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    sendMail(email)
  }

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

  private def generateWelcomeEmail(userName: Option[String], userEmail: String): Email = {
    val bodyHtml = views.html.emails.welcome(userEmail, tokenExpirationInMinutes).toString
    Email(
      subject = "[A+] Bienvenue sur Administration+",
      from = from,
      to = List(
        userName
          .filter(_.nonEmpty)
          .map(name => s"${quoteEmailPhrase(name)} <${userEmail}>")
          .getOrElse(userEmail)
      ),
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateInvitationEmail(application: Application, answer: Option[Answer] = None)(
      invitedUser: User
  ): Email = {
    val absoluteUrl =
      routes.ApplicationController.show(application.id).absoluteURL(https, host)
    val bodyInner = common.invitationBody(application, answer, invitedUser, absoluteUrl)
    Email(
      subject = s"[A+] Nouvelle demande d'aide : ${application.subject}",
      from = from,
      to = List(s"${quoteEmailPhrase(invitedUser.name)} <${invitedUser.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
  }

  private def generateAnswerEmail(application: Application, answer: Answer)(user: User): Email = {
    val absoluteUrl =
      routes.ApplicationController.show(application.id).absoluteURL(https, host)
    val bodyInner = common.answerBody(application, answer, user, absoluteUrl)
    Email(
      subject = s"[A+] Nouvelle réponse pour : ${application.subject}",
      from = from,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
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
    val bodyInner = common.weeklyEmailBody(
      infos,
      application => routes.ApplicationController.show(application.id).absoluteURL(https, host),
      daySinceLastAgentAnswerForApplicationsThatShouldBeClosed
    )
    val email = Email(
      subject = common.weeklyEmailSubject,
      from = from,
      to = List(s"${quoteEmailPhrase(infos.user.name)} <${infos.user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
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
            if (lastAnswer.creatorUserID === user.id) {
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
