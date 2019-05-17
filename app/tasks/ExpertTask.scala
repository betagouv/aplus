package tasks

import java.util.UUID

import akka.actor._
import javax.inject.{Inject, Named}
import models._
import services.{ApplicationService, EventService, NotificationService}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext


class ExpertTask @Inject()(actorSystem: ActorSystem,
                           applicationService: ApplicationService,
                           eventService: EventService,
                           notificationService: NotificationService)(implicit executionContext: ExecutionContext) {

  actorSystem.scheduler.schedule(initialDelay = 5.seconds, interval = 24.hours) {
    inviteExpertsInApplication
  }

  val dayWithoutAgentAnswer = 5
  val daySinceLastAgentAnswer = 15

  def inviteExpertsInApplication =
        applicationService.openAndOlderThan(dayWithoutAgentAnswer).foreach { application =>
            application.answers.filter(_.creatorUserID != application.creatorUserId).lastOption match {
              case None => // No answer for someone else the creator
                inviteExpert(application, dayWithoutAgentAnswer)
              case Some(answer) if answer.ageInDays > daySinceLastAgentAnswer  => // The last answer is older than X days
                inviteExpert(application, daySinceLastAgentAnswer)
            }
        }

  private def inviteExpert(application: Application, days: Int): Unit = {
    val expertUsers = User.admins.filter(_.expert)
    val experts = expertUsers.map(user => user.id -> user.nameWithQualite).toMap
    val answer = Answer(UUID.randomUUID(),
      application.id,
      Time.now(),
      s"Je rejoins la conversation automatiquement comme ${expertUsers.head.qualite} car le dernier message a plus de $days jours",
      expertUsers.head.id,
      expertUsers.head.nameWithQualite,
      experts,
      true,
      application.area,
      false,
      Some(Map()))
    if (applicationService.add(application.id, answer, true)  == 1) {
      notificationService.newAnswer(application, answer)
      eventService.info(User.systemUser, Area.fromId(application.area).get, "0.0.0.0", "ADD_EXPERT_CREATED", s"Les experts ont été automatiquement ajouté ${answer.id} sur la demande ${application.id}", Some(application), None)
    } else {
      eventService.error(User.systemUser, Area.fromId(application.area).get, "0.0.0.0", "ANSWER_NOT_CREATED", s"Les experts n'ont pas pu être automatiquement ajouté ${answer.id} sur la demande ${application.id} : problème BDD", Some(application), None)
    }
  }
}