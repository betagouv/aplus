package tasks

import akka.actor.ActorSystem
import java.time.{Duration, ZonedDateTime}
import javax.inject.Inject
import models.EventType
import modules.AppConfig
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import services.{EventService, FileService}

class RekeyEncryptedFieldsTask @Inject() (
    actorSystem: ActorSystem,
    config: AppConfig,
    eventService: EventService,
    fileService: FileService,
)(implicit executionContext: ExecutionContext) {

  private val shouldRekey = config.fieldEncryptionKeyRotationExecute
  private val encryptIfPlainText = config.fieldEncryptionKeyRotationEncryptIfPlainText

  val now = ZonedDateTime.now() // Machine Time

  // 10 min after the old data wipe
  val startDate =
    now.toLocalDate.atStartOfDay(now.getZone).plusDays(1).withHour(4).plusMinutes(10)

  val initialDelay = Duration.between(now, startDate).getSeconds.seconds

  actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = initialDelay, delay = 24.hours)(
    new Runnable {
      override def run(): Unit = rekey()
    }
  )

  def rekey(): Unit =
    if (shouldRekey) {
      fileService.rekeyFilenames(encryptIfPlainText).onComplete {
        case Success(Right((numOfNoops, numOfUpdates))) =>
          val message =
            s"Changement des clés de chiffrement : $numOfUpdates champs mis à jour / $numOfNoops champs non affectés"
          eventService.logNoRequest(EventType.FieldEncryptionRekeyComplete, message)
        case Success(Left(error)) =>
          eventService.logErrorNoRequest(error)
        case Failure(exception) =>
          eventService.logNoRequest(
            EventType.FieldEncryptionRekeyError,
            "Erreur lors de la rotation des clés de chiffrement",
            underlyingException = Some(exception)
          )
      }
    }

}
