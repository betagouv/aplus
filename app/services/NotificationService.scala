package services

import cats.syntax.all._
import constants.Constants
import controllers.routes
import helper.EmailHelper.quoteEmailPhrase
import java.time.ZoneId
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models._
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.stream.{ActorAttributes, Materializer, Supervision}
import play.api.libs.concurrent.MaterializerProvider
import play.api.libs.mailer.Email
import play.api.mvc.Request
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import views.emails.{common, WeeklyEmailInfos}

@Singleton
class NotificationService @Inject() (
    applicationService: ApplicationService,
    configuration: play.api.Configuration,
    emailsService: EmailsService,
    eventService: EventService,
    groupService: UserGroupService,
    materializerProvider: MaterializerProvider,
    userService: UserService
)(implicit executionContext: ExecutionContext) {

  implicit val materializer: Materializer = materializerProvider.get

  private lazy val tokenExpirationInMinutes =
    configuration.get[Int]("app.tokenExpirationInMinutes")

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
        case Some(herokuAppName) => s"$herokuAppName.herokuapp.com"
      }
    configuration.get[Option[String]]("app.host") match {
      case None => readHerokuAppNameOrThrow
      // Temporary check, remove when APP_HOST is not mandatory on Heroku
      case Some(appHost) if appHost.contains("invalid") => readHerokuAppNameOrThrow
      case Some(appHost)                                => appHost
    }
  }

  private val https = configuration.underlying.getString("app.https") === "true"

  private val from: String = configuration
    .getOptional[String]("app.emailsFrom")
    .getOrElse(s"Administration+ <${Constants.supportEmail}>")

  private val replyTo = List(s"Administration+ <${Constants.supportEmail}>")

  def newApplication(application: Application): Unit = {
    val userIds = (application.invitedUsers).keys
    val users = userService.byIds(userIds.toList)
    val groups = groupService
      .byIds(application.invitedGroupIdsAtCreation)
      .filter(_.email.nonEmpty)

    users
      .map(generateInvitationEmail(application))
      .foreach(email => emailsService.sendBlocking(email, EmailPriority.Normal))

    groups
      .map(generateNotificationBALEmail(application, None, users))
      .foreach(email => emailsService.sendBlocking(email, EmailPriority.Normal))
  }

  // Note: application does not contain answer at this point
  def newAnswer(application: Application, answer: Answer) = {
    // Retrieve data
    val userIds = (application.invitedUsers ++ answer.invitedUsers).keys
    val users = userService.byIds(userIds.toList)
    val (allGroups, alreadyPresentGroupIds): (List[UserGroup], Set[UUID]) = {
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
        } else if (
          !Authorization.canSeeAnswer(answer, application)(Authorization.readUserRights(user))
        ) {
          None
        } else if (answer.invitedUsers.contains(user.id)) {
          Some(generateInvitationEmail(application, Some(answer))(user))
        } else {
          Some(generateAnswerEmail(application, answer)(user))
        }
      }
      .foreach(email => emailsService.sendBlocking(email, EmailPriority.Normal))

    val usersEmails: Set[String] = users.map(_.email).toSet

    // Send emails to groups
    allGroups
      .collect {
        case group @ UserGroup(id, _, _, _, _, _, _, Some(email), _, _)
            if !usersEmails.contains(email) =>
          if (alreadyPresentGroupIds.contains(id))
            generateNotificationBALEmail(application, answer.some, users)(group)
          else
            generateNotificationBALEmail(application, Option.empty[Answer], users)(group)
      }
      .foreach(email => emailsService.sendBlocking(email, EmailPriority.Normal))

    if (answer.visibleByHelpers && answer.creatorUserID =!= application.creatorUserId) {
      userService
        .byId(application.creatorUserId)
        .map(generateAnswerEmail(application, answer))
        .foreach(email => emailsService.sendBlocking(email, EmailPriority.Normal))
    }
  }

  def newUser(newUser: User): Option[String] =
    emailsService.sendBlocking(
      generateWelcomeEmail(newUser.name.some, newUser.email),
      EmailPriority.Normal
    )

  def newSignup(signup: SignupRequest)(implicit request: Request[_]): Option[String] =
    Try(emailsService.sendBlocking(generateWelcomeEmail(none, signup.email), EmailPriority.Normal))
      .fold(
        { error =>
          eventService.logErrorNoUser(
            Error.MiscException(
              EventType.SignupEmailError,
              s"Impossible d'envoyer l'email de bienvenue pour la préinscription ${signup.id}",
              error,
              s"Email ${signup.email}".some
            )
          )
          none
        },
        identity
      )

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
      replyTo = replyTo,
      to = List(
        userName
          .filter(_.nonEmpty)
          .map(name => s"${quoteEmailPhrase(name)} <$userEmail>")
          .getOrElse(userEmail)
      ),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    emailsService.sendBlocking(email, EmailPriority.Urgent)
  }

  def mandatV2Generated(mandatId: Mandat.Id, user: User): Option[String] = {
    val absoluteUrl: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val bodyInner = common.mandatV2Body(absoluteUrl)
    val email = Email(
      subject = common.mandatV2Subject,
      from = from,
      replyTo = replyTo,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    emailsService.sendBlocking(email, EmailPriority.Normal)
  }

  def mandatSmsSent(mandatId: Mandat.Id, user: User): Option[String] = {
    val absoluteUrl: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val bodyInner = common.mandatSmsSentBody(absoluteUrl)
    val email = Email(
      subject = common.mandatSmsSentSubject,
      from = from,
      replyTo = replyTo,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    emailsService.sendBlocking(email, EmailPriority.Normal)
  }

  def mandatSmsClosed(mandatId: Mandat.Id, user: User): Option[String] = {
    val absoluteUrl: String =
      routes.MandatController.mandat(mandatId.underlying).absoluteURL(https, host)
    val bodyInner = common.mandatSmsSentBody(absoluteUrl)
    val email = Email(
      subject = common.mandatSmsClosedSubject,
      from = from,
      replyTo = replyTo,
      to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    emailsService.sendBlocking(email, EmailPriority.Normal)
  }

  def fileUploadStatus(
      document: FileMetadata.Attached,
      status: FileMetadata.Status,
      user: User
  ): Unit = {
    val applicationId = document match {
      case FileMetadata.Attached.Application(id) => id
      case FileMetadata.Attached.Answer(id, _)   => id
    }
    val absoluteUrl =
      routes.ApplicationController.show(applicationId).absoluteURL(https, host)
    status match {
      case FileMetadata.Status.Quarantined =>
        val email = Email(
          subject = common.fileQuarantinedSubject,
          from = from,
          replyTo = replyTo,
          to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
          bodyHtml = Some(common.renderEmail(common.fileQuarantinedBody(absoluteUrl)))
        )
        val _ = emailsService.sendBlocking(email, EmailPriority.Normal)
      case FileMetadata.Status.Error =>
        val email = Email(
          subject = common.fileErrorSubject,
          from = from,
          replyTo = replyTo,
          to = List(s"${quoteEmailPhrase(user.name)} <${user.email}>"),
          bodyHtml = Some(common.renderEmail(common.fileErrorBody(absoluteUrl)))
        )
        val _ = emailsService.sendBlocking(email, EmailPriority.Normal)
      case _ =>
    }
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
      replyTo = replyTo,
      to = List(s"${quoteEmailPhrase(group.name)} <${group.email.get}>"),
      bodyHtml = Some(bodyHtml)
    )
  }

  private def generateWelcomeEmail(userName: Option[String], userEmail: String): Email = {
    val bodyHtml = views.html.emails.welcome(userEmail, tokenExpirationInMinutes).toString
    Email(
      subject = "[A+] Bienvenue sur Administration+",
      from = from,
      replyTo = replyTo,
      to = List(
        userName
          .filter(_.nonEmpty)
          .map(name => s"${quoteEmailPhrase(name)} <$userEmail>")
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
      replyTo = replyTo,
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
      replyTo = replyTo,
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
          "0.0.0.0",
          EventType.WeeklyEmailsSent.code,
          s"Les emails hebdomadaires ont été envoyés ($count)",
          None,
          None,
          None,
          None
        )
      }

  private def sendWeeklyEmail(infos: WeeklyEmailInfos): Future[Option[String]] = {
    val bodyInner = common.weeklyEmailBody(
      infos,
      application => routes.ApplicationController.show(application.id).absoluteURL(https, host),
      daySinceLastAgentAnswerForApplicationsThatShouldBeClosed
    )
    val email = Email(
      subject = common.weeklyEmailSubject,
      from = from,
      replyTo = replyTo,
      to = List(s"${quoteEmailPhrase(infos.user.name)} <${infos.user.email}>"),
      bodyHtml = Some(common.renderEmail(bodyInner))
    )
    emailsService.sendNonBlocking(email, EmailPriority.Normal)
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
