package tasks

import cats.syntax.all._
import java.time.{Duration, ZonedDateTime}
import javax.inject.Inject
import models.{Error, EventType}
import models.dataModels.ApplicationRow
import modules.AppConfig
import org.apache.pekko.actor.ActorSystem
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import services.{
  ApplicationService,
  EventService,
  FileService,
  MandatService,
  ServicesDependencies,
  SmsService
}

class WipeOldDataTask @Inject() (
    actorSystem: ActorSystem,
    applicationService: ApplicationService,
    config: AppConfig,
    dependencies: ServicesDependencies,
    eventService: EventService,
    fileService: FileService,
    mandatService: MandatService,
    smsService: SmsService
)(implicit executionContext: ExecutionContext) {

  import dependencies.ioRuntime

  val startAtHour = 4
  val now: ZonedDateTime = ZonedDateTime.now() // Machine Time

  val startDate: ZonedDateTime =
    now.toLocalDate.atStartOfDay(now.getZone).plusDays(1).withHour(startAtHour)

  val initialDelay: FiniteDuration = Duration.between(now, startDate).getSeconds.seconds

  val _ =
    actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = initialDelay, delay = 24.hours)(
      new Runnable {
        override def run(): Unit = wipeOldData(config.dataRetentionInMonths)
      }
    )

  def wipeOldData(retentionInMonths: Long): Unit =
    wipePersonalData(retentionInMonths)
      .onComplete {
        case Success(Right(wiped)) =>
          val numWiped = wiped.length
          val wipedIds = wiped.map(_.id).mkString(", ")
          logSuccess(
            if (numWiped > 0)
              ("Les données personnelles des demandes ont été supprimées : " +
                s"$numWiped lignes ($wipedIds)")
            else
              s"Aucunes données personnelles à supprimer dans les demandes"
          )
        case Success(Left(error)) =>
          eventService.logErrorNoRequest(error)
        case Failure(error) =>
          logError(
            "Impossible de supprimer les informations personnelles des demandes",
            EventType.WipeDataError,
            Some(error)
          )
      }

  // Not wiping these data, pending legal validation
  /*
    mandatService
      .wipePersonalData(retentionInMonths)
      .onComplete {
        case Success(Right(wiped)) =>
          val numWiped = wiped.length
          val wipedIds = wiped.map(_.id.underlying).mkString(", ")
          logSuccess(
            if (numWiped > 0)
              ("Les données personnelles des mandats ont été supprimées : " +
                s"$numWiped lignes ($wipedIds)")
            else
              s"Aucunes données personnelles à supprimer dans les mandats"
          )
        case Success(Left(error)) =>
          logError(error.description, error.eventType, error.underlyingException)
        case Failure(error) =>
          logError(
            "Impossible de supprimer les informations personnelles des mandats",
            EventType.WipeDataError,
            Some(error)
          )
      }
   */

  def wipePersonalData(retentionInMonths: Long): Future[Either[Error, List[ApplicationRow]]] = {
    val applications = applicationService.applicationsThatShouldBeWipedBlocking(retentionInMonths)
    val files = fileService.queryByApplicationsIdsBlocking(applications.map(_.id))
    val filesIds = files.map(_.id)
    val filesDeletionResult = fileService.deleteByIds(filesIds).unsafeToFuture()
    filesDeletionResult.map {
      case Left(error) =>
        error.asLeft[List[ApplicationRow]]
      case Right(_) =>
        applications
          .flatMap { application =>
            val _ = fileService.wipeFilenamesByIdsBlocking(filesIds)
            applicationService.wipeApplicationPersonalDataBlocking(application)
          }
          .asRight[Error]
    }
  }

  private def logSuccess(description: String) =
    eventService.logNoRequest(EventType.WipeDataComplete, description)

  private def logError(description: String, eventType: EventType, exception: Option[Throwable]) =
    eventService.logNoRequest(
      eventType,
      description,
      underlyingException = exception
    )

}
