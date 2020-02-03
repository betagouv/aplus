package tasks

import java.nio.file.Files
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

import akka.actor.ActorSystem
import extentions.Time
import javax.inject.Inject
import org.joda.time.Period

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class RemoveExpiredFilesTask @Inject() (
    actorSystem: ActorSystem,
    configuration: play.api.Configuration
)(implicit executionContext: ExecutionContext) {
  private val filesPath = configuration.underlying.getString("app.filesPath")

  private val filesExpirationInDays =
    configuration.underlying.getString("app.filesExpirationInDays").toInt

  val startAtHour = 5
  val startDate = Time.now().withTimeAtStartOfDay().plusDays(1).withHourOfDay(startAtHour)
  val initialDelay = (new Period(Time.now(), startDate).toStandardSeconds.getSeconds).seconds

  actorSystem.scheduler.schedule(initialDelay = 0.seconds, interval = 24.hours) {
    removeExpiredFile
  }

  def removeExpiredFile(): Unit = {
    val dir = new File(filesPath)
    if (dir.exists() && dir.isDirectory) {
      val fileToDelete = dir
        .listFiles()
        .filter(_.isFile)
        .filter { file =>
          val instant = Files.getLastModifiedTime(file.toPath).toInstant
          instant.plus(filesExpirationInDays + 1, DAYS).isBefore(Instant.now())
        }
      fileToDelete.forall(_.delete())
    }
  }
}
