package tasks

import java.nio.file.Files
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

import akka.actor.ActorSystem
import javax.inject.Inject

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class RemoveExpiredFilesTask @Inject() (
    actorSystem: ActorSystem,
    configuration: play.api.Configuration
)(implicit executionContext: ExecutionContext) {
  private val filesPath = configuration.underlying.getString("app.filesPath")

  private val filesExpirationInDays: Int =
    configuration.underlying.getString("app.filesExpirationInDays").toInt

  val startAtHour = 5
  val now = java.time.ZonedDateTime.now() // Machine Time
  val startDate = now.toLocalDate.atStartOfDay(now.getZone).plusDays(1).withHour(startAtHour)
  val initialDelay = java.time.Duration.between(now, startDate).getSeconds.seconds

  actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = initialDelay, delay = 24.hours)(
    new Runnable { override def run(): Unit = removeExpiredFile }
  )

  def removeExpiredFile(): Unit = {
    val dir = new File(filesPath)
    if (dir.exists() && dir.isDirectory) {
      val fileToDelete = dir
        .listFiles()
        .filter(_.isFile)
        .filter { file =>
          val instant = Files.getLastModifiedTime(file.toPath).toInstant
          instant.plus(filesExpirationInDays.toLong + 1, DAYS).isBefore(Instant.now())
        }
      fileToDelete.foreach(_.delete())
    }
  }

}
