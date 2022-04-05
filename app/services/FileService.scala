package services

import akka.Done
import akka.stream.scaladsl._
import anorm._
import aplus.macros.Macros
import cats.syntax.all._
import cats.data.EitherT
import helper.StringHelper.normalizeNFKC
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, FileMetadata, User}
import models.dataModels.FileMetadataRow
import net.scalytica.clammyscan.streams.{ClamError, ClamIO, FileOk, VirusFound}
import play.api.Configuration
import play.api.mvc.Request
import play.api.libs.concurrent.{ActorSystemProvider, MaterializerProvider}
import play.api.db.Database
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Try}

@Singleton
class FileService @Inject() (
    configuration: Configuration,
    db: Database,
    eventService: EventService,
    materializer: MaterializerProvider,
    notificationsService: NotificationService,
    system: ActorSystemProvider
)(implicit ec: ExecutionContext) {
  implicit val actorSystem = system.get

  val filesPath: String = {
    val path = configuration.get[String]("app.filesPath")
    val dir = Paths.get(path)
    if (!Files.isDirectory(dir)) {
      Files.createDirectories(dir)
    }
    path
  }

  val clamAvIsEnabled = configuration.get[Boolean]("app.clamav.enabled")

  val clamIo = ClamIO(
    configuration.get[String]("app.clamav.host"),
    configuration.get[Int]("app.clamav.port"),
    configuration.get[Int]("app.clamav.timeoutInSeconds").seconds,
    // maxBytes = 10M
    10000000
  )

  // Play is supposed to give us temporary files here
  def saveFiles(
      pathsWithFilenames: List[(Path, String)],
      document: FileMetadata.Attached,
      uploader: User
  )(implicit
      request: Request[_]
  ): Future[Either[Error, List[FileMetadata]]] = {
    val result: EitherT[Future, Error, List[(Path, FileMetadata)]] = pathsWithFilenames.traverse {
      case (path, filename) =>
        val metadata = FileMetadata(
          id = UUID.randomUUID(),
          uploadDate = Instant.now(),
          filename = normalizeNFKC(filename),
          filesize = path.toFile.length().toInt,
          status = FileMetadata.Status.Scanning,
          attached = document,
        )
        EitherT(insertMetadata(metadata)).map(_ => (path, metadata))
    }

    // Scan in background, only on success, and sequentially
    result.value.foreach {
      case Right(metadataList) =>
        scanFilesBackground(metadataList, uploader)
      case _ =>
    }

    result.map(_.map { case (_, metadata) => metadata }).value
  }

  def fileMetadata(fileId: UUID): Future[Either[Error, Option[(Path, FileMetadata)]]] =
    byId(fileId).map(_.map(_.map(metadata => (Paths.get(s"$filesPath/$fileId"), metadata))))

  // TODO: remove that once saving names in DB is known to be working
  //       this piece of code is here just in case we need reverting
  def legacyFilePath(metadata: FileMetadata): Path =
    metadata.attached match {
      case FileMetadata.Attached.Application(applicationId) =>
        Paths.get(s"$filesPath/app_$applicationId-${metadata.filename}")
      case FileMetadata.Attached.Answer(applicationId, _) =>
        Paths.get(s"$filesPath/ans_$applicationId-${metadata.filename}")
    }

  private def scanFilesBackground(metadataList: List[(Path, FileMetadata)], uploader: User)(implicit
      request: Request[_]
  ): Future[Done] =
    // sequential => parallelism = 1
    Source
      .fromIterator(() => metadataList.iterator)
      .mapAsync(1) { case (path, metadata) =>
        val sink = clamIo.scan(metadata.id.toString)
        val scanResult: Future[Either[Error, Unit]] =
          if (clamAvIsEnabled)
            FileIO
              .fromPath(path)
              .toMat(sink)(Keep.right)
              .run()
              .flatMap { clamResult =>
                val status = clamResult match {
                  case FileOk =>
                    eventService.logSystem(
                      EventType.FileAvailable,
                      s"Aucun virus détecté par ClamAV dans le fichier ${metadata.id}"
                    )
                    val fileDestination = Paths.get(s"$filesPath/${metadata.id}")
                    Files.copy(path, fileDestination)
                    // Can throw java.nio.file.FileAlreadyExistsException
                    Try(Files.copy(path, legacyFilePath(metadata)))
                    Files.deleteIfExists(path)
                    FileMetadata.Status.Available
                  case VirusFound(message) =>
                    eventService.logSystem(
                      EventType.FileQuarantined,
                      s"Signature de virus détectée par ClamAV dans le fichier ${metadata.id}: " +
                        message
                    )
                    Files.deleteIfExists(path)
                    FileMetadata.Status.Quarantined
                  case error: ClamError =>
                    eventService.logSystem(
                      EventType.FileScanError,
                      s"Erreur de ClamAV pour le fichier ${metadata.id}: ${error.message}"
                    )
                    Files.deleteIfExists(path)
                    FileMetadata.Status.Error
                }
                notificationsService.fileUploadStatus(metadata.attached, status, uploader)
                updateStatus(metadata.id, status)
              }
          else {
            eventService.logSystem(
              EventType.FileAvailable,
              s"Le fichier ${metadata.id} est disponible. ClamAV est désactivé. " +
                "Aucun scan n'a été effectué"
            )
            val fileDestination = Paths.get(s"$filesPath/${metadata.id}")
            Files.copy(path, fileDestination)
            // Can throw java.nio.file.FileAlreadyExistsException
            Try(Files.copy(path, legacyFilePath(metadata)))
            Files.deleteIfExists(path)
            updateStatus(metadata.id, FileMetadata.Status.Available)
          }

        scanResult
          .map {
            case Right(_) => ()
            case Left(error) =>
              eventService.logErrorNoUser(error)
              Files.deleteIfExists(path)
              val status = FileMetadata.Status.Error
              updateStatus(metadata.id, status)
                .foreach(_.left.foreach(e => eventService.logErrorNoUser(e)))
          }
          .recover { case error =>
            eventService.logSystem(
              EventType.FileScanError,
              s"Erreur lors de la recherche de virus dans le fichier ${metadata.id}",
              underlyingException = Some(error)
            )
            Files.deleteIfExists(path)
            val status = FileMetadata.Status.Error
            updateStatus(metadata.id, status)
              .foreach(_.left.foreach(e => eventService.logErrorNoUser(e)))
            notificationsService.fileUploadStatus(metadata.attached, status, uploader)
          }
      }
      .run()
      .recover { case error =>
        eventService.logSystem(
          EventType.FileScanError,
          s"Erreur imprévue (bug) durant la recherche de virus dans les fichiers " +
            metadataList.map { case (_, metadata) => metadata.id },
          underlyingException = Some(error)
        )
        Done
      }

  private val (fileMetadataRowParser, tableFields) = Macros.parserWithFields[FileMetadataRow](
    "id",
    "upload_date",
    "filename",
    "filesize",
    "status",
    "application_id",
    "answer_id"
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  private def byId(fileId: UUID): Future[Either[Error, Option[FileMetadata]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          SQL(s"""SELECT $fieldsInSelect FROM file_metadata WHERE id = {fileId}::uuid""")
            .on("fileId" -> fileId)
            .as(fileMetadataRowParser.singleOpt)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FileMetadataError,
            s"Impossible de chercher la metadata de fichier $fileId",
            e,
            none
          )
        )
        .flatMap {
          case None => none.asRight
          case Some(row) =>
            row.toFileMetadata match {
              case None =>
                Error
                  .Database(
                    EventType.FileMetadataError,
                    s"Ligne invalide en BDD pour la metadata de fichier ${row.id} [" +
                      s"upload_date ${row.uploadDate}" +
                      s"filesize ${row.filesize}" +
                      s"status ${row.status}" +
                      s"application_id ${row.applicationId}" +
                      s"answer_id ${row.answerId}" +
                      "]",
                    none
                  )
                  .asLeft
              case Some(metadata) => metadata.some.asRight
            }
        }
    )

  def byApplicationId(applicationId: UUID): Future[Either[Error, List[FileMetadata]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          SQL(
            s"""SELECT $fieldsInSelect FROM file_metadata WHERE application_id = {applicationId}::uuid"""
          )
            .on("applicationId" -> applicationId)
            .as(fileMetadataRowParser.*)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FileMetadataError,
            s"Impossible de chercher les fichiers de la demande $applicationId",
            e,
            none
          )
        )
        .map(_.flatMap(_.toFileMetadata))
    )

  def byAnswerId(answerId: UUID): Future[Either[Error, List[FileMetadata]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          SQL(s"""SELECT $fieldsInSelect FROM file_metadata WHERE answer_id = {answerId}::uuid""")
            .on("answerId" -> answerId)
            .as(fileMetadataRowParser.*)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FileMetadataError,
            s"Impossible de chercher les fichiers de la réponse $answerId",
            e,
            none
          )
        )
        .map(_.flatMap(_.toFileMetadata))
    )

  def deleteBefore(beforeDate: Instant): Future[Unit] = {
    def logException(exception: Throwable) =
      eventService.logNoRequest(
        EventType.FileDeletionError,
        s"Erreur lors de la suppression d'un fichier",
        underlyingException = exception.some
      )

    before(beforeDate)
      .map(
        _.fold(
          e => eventService.logErrorNoRequest(e),
          files => {
            Source
              .fromIterator(() => files.iterator)
              .mapAsync(1) { metadata =>
                val path = Paths.get(s"$filesPath/${metadata.id}")
                Files.deleteIfExists(path)
                updateStatus(metadata.id, FileMetadata.Status.Expired)
                  .map(_.fold(e => eventService.logErrorNoRequest(e), identity))
              }
              .recover(logException _)
              .runWith(Sink.ignore)
              .foreach(_ =>
                eventService.logNoRequest(
                  EventType.FilesDeletion,
                  s"Fin de la suppression des fichiers avant $beforeDate"
                )
              )
          },
        )
      )
      .recover { case error =>
        logException(error)
      }
  }

  private def before(beforeDate: Instant): Future[Either[Error, List[FileMetadata]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          SQL(s"""SELECT $fieldsInSelect FROM file_metadata WHERE upload_date < {beforeDate}""")
            .on("beforeDate" -> beforeDate)
            .as(fileMetadataRowParser.*)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FileMetadataError,
            s"Impossible de chercher les fichiers de avant $beforeDate",
            e,
            none
          )
        )
        .map(_.flatMap(_.toFileMetadata))
    )

  private def insertMetadata(metadata: FileMetadata): Future[Either[Error, Unit]] =
    Future(
      Try {
        val row = FileMetadataRow.fromFileMetadata(metadata)
        db.withConnection { implicit connection =>
          SQL"""
            INSERT INTO file_metadata (
              id,
              upload_date,
              filename,
              filesize,
              status,
              application_id,
              answer_id
            ) VALUES (
              ${row.id}::uuid,
              ${row.uploadDate},
              ${row.filename},
              ${row.filesize},
              ${row.status},
              ${row.applicationId}::uuid,
              ${row.answerId}::uuid
            )""".executeUpdate()
        }
      }.toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FileMetadataError,
            s"Impossible d'enregistrer la metadata de fichier ${metadata.id} " +
              s"[document ${metadata.attached} ; " +
              s"taille ${metadata.filesize} ; status ${metadata.status}]",
            e,
            none
          )
        )
        .flatMap { numOfRows =>
          if (numOfRows === 1) ().asRight
          else
            Error
              .Database(
                EventType.FileMetadataError,
                s"Nombre incorrect de lignes ($numOfRows) lors de l'ajout " +
                  s"de la préinscription de la metadata de fichier ${metadata.id} " +
                  s"[document ${metadata.attached} ; " +
                  s"taille ${metadata.filesize} ; status ${metadata.status}]",
                none
              )
              .asLeft
        }
    )

  private def updateStatus(id: UUID, status: FileMetadata.Status): Future[Either[Error, Unit]] =
    Future(
      Try {
        val rawStatus = FileMetadataRow.statusFromFileMetadata(status)
        db.withConnection { implicit connection =>
          SQL"""
            UPDATE file_metadata
            SET status = $rawStatus
            WHERE id = $id::uuid
            """.executeUpdate()
        }
      }.toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FileMetadataError,
            s"Impossible de mettre le status $status sur la metadata de fichier $id",
            e,
            none
          )
        )
        .flatMap { numOfRows =>
          if (numOfRows === 1) ().asRight
          else
            Error
              .Database(
                EventType.FileMetadataError,
                s"Nombre incorrect de lignes modifiées ($numOfRows) " +
                  s"lors de la mise à jour du status $status de la metadata $id",
                none
              )
              .asLeft
        }
    )

}
