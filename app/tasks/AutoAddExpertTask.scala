package tasks

import java.util.UUID

import cats.syntax.all._
import helper.Time
import javax.inject.Inject
import models.Answer.AnswerType
import models._
import modules.AppConfig
import org.apache.pekko.actor._
import services._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AutoAddExpertTask @Inject() (
    actorSystem: ActorSystem,
    applicationService: ApplicationService,
    config: AppConfig,
    eventService: EventService,
    notificationService: NotificationService,
    userService: UserService
)(implicit executionContext: ExecutionContext) {
  val startAtHour = 8
  val now = java.time.ZonedDateTime.now()
  val startDate = now.toLocalDate.atStartOfDay(now.getZone).plusDays(1).withHour(startAtHour)
  val initialDelay: FiniteDuration = java.time.Duration.between(now, startDate).getSeconds.seconds

  // https://github.com/akka/akka/blob/v2.6.4/akka-actor/src/main/scala/akka/actor/Scheduler.scala#L403
  actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = initialDelay, delay = 24.hours)(() =>
    inviteExpertsInApplication()
  )

  val dayWithoutAgentAnswer = 5
  val daySinceLastAgentAnswer = 15

  def inviteExpertsInApplication() =
    if (config.featureAutoAddExpert) {
      applicationService.openAndOlderThan(dayWithoutAgentAnswer).foreach { application =>
        if (application.status =!= Application.Status.Processed) {
          application.answers
            .filter(_.creatorUserID =!= application.creatorUserId)
            .lastOption match {
            case None => // No answer for someone else the creator
              inviteExpert(application, dayWithoutAgentAnswer)
            case Some(answer)
                if answer.ageInDays > daySinceLastAgentAnswer => // The last answer is older than X days
              inviteExpert(application, daySinceLastAgentAnswer)
            case _ =>
          }
        }
      }
    }

  private def inviteExpert(application: Application, days: Int): Future[Unit] =
    userService.allExperts.map { expertUsers =>
      val experts =
        expertUsers.map(user => user.id -> user.nameWithQualite).toMap

      expertUsers.headOption.foreach { expert =>
        val answer = Answer(
          UUID.randomUUID(),
          application.id,
          Time.nowParis(),
          AnswerType.Custom,
          s"Je rejoins la conversation automatiquement comme expert(e) car le dernier message a plus de $days jours",
          expert.id,
          expert.nameWithQualite,
          experts,
          visibleByHelpers = true,
          declareApplicationHasIrrelevant = false,
          Map.empty[String, String].some,
          invitedGroupIds = List.empty[UUID]
        )
        if (applicationService.addAnswer(application.id, answer, expertInvited = true) === 1) {
          notificationService.newAnswer(application, answer)
          eventService.info(
            User.systemUser,
            "0.0.0.0",
            "ADD_EXPERT_CREATED",
            s"Les experts ont été automatiquement ajoutés ${answer.id} sur la demande ${application.id}",
            none,
            application.id.some,
            expert.id.some,
            Option.empty[Throwable]
          )
        } else {
          eventService.error(
            User.systemUser,
            "0.0.0.0",
            "ANSWER_NOT_CREATED",
            s"Les experts n'ont pas pu être automatiquement ajoutés ${answer.id} sur la demande ${application.id} : problème BDD",
            none,
            application.id.some,
            expert.id.some,
            Option.empty[Throwable]
          )
        }
      }
    }

}
