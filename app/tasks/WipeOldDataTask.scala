package tasks

import java.time.{Duration, ZonedDateTime}
import javax.inject.Inject
import models.EventType
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import services.{ApplicationService, EventService, FileService, MandatService, SmsService}

class WipeOldDataTask @Inject() (
    actorSystem: ActorSystem,
    applicationService: ApplicationService,
    configuration: Configuration,
    eventService: EventService,
    fileService: FileService,
    mandatService: MandatService,
    smsService: SmsService
)(implicit executionContext: ExecutionContext) {

  private val retentionInMonthsOpt: Option[Long] =
    configuration.getOptional[Long]("app.personalDataRetentionInMonths")

  val startAtHour = 4
  val now: ZonedDateTime = ZonedDateTime.now() // Machine Time

  val startDate: ZonedDateTime =
    now.toLocalDate.atStartOfDay(now.getZone).plusDays(1).withHour(startAtHour)

  val initialDelay: FiniteDuration = Duration.between(now, startDate).getSeconds.seconds

  val _ =
    actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = initialDelay, delay = 24.hours)(
      new Runnable {
        override def run(): Unit = retentionInMonthsOpt.foreach(retention => wipeOldData(retention))
      }
    )

  def wipeOldData(retentionInMonths: Long): Unit = {
    applicationService
      .wipePersonalData(retentionInMonths)
      .onComplete {
        case Success(wiped) =>
          val numWiped = wiped.length
          val wipedIds = wiped.map(_.id).mkString(", ")
          logSuccess(
            if (numWiped > 0)
              ("Les données personnelles des demandes ont été supprimées : " +
                s"$numWiped lignes ($wipedIds)")
            else
              s"Aucunes données personnelles à supprimer dans les demandes"
          )
        case Failure(error) =>
          logError(
            "Impossible de supprimer les informations personnelles des demandes",
            EventType.WipeDataError,
            Some(error)
          )
      }

    fileService
      .wipeFilenames(retentionInMonths)
      .foreach(
        _.fold(
          error => eventService.logErrorNoRequest(error),
          numOfRows => logSuccess(s"Suppression de $numOfRows noms de fichiers")
        )
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

  private def logSuccess(description: String) =
    eventService.logNoRequest(EventType.WipeDataComplete, description)

  private def logError(description: String, eventType: EventType, exception: Option[Throwable]) =
    eventService.logNoRequest(
      eventType,
      description,
      underlyingException = exception
    )

}
