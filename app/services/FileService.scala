package services

import anorm._
import aplus.macros.Macros
import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import eu.timepit.refined.types.string.NonEmptyString
import fs2.Stream
import fs2.aws.s3.S3
import fs2.aws.s3.models.Models.{BucketName, FileKey}
import fs2.io.file.{Files => FsFiles, Path => FsPath}
import helper.Crypto
import helper.StringHelper.normalizeNFKC
import io.laserdisc.pure.s3.tagless.{Interpreter => S3Interpreter}
import java.net.URI
import java.nio.file.{Files, Path => NioPath}
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, FileMetadata, User}
import models.dataModels.FileMetadataRow
import modules.AppConfig
import org.apache.pekko.stream.scaladsl.Source
import org.reactivestreams.FlowAdapters
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.mvc.Request
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{HeadObjectRequest, NoSuchKeyException}

@Singleton
class FileService @Inject() (
    config: AppConfig,
    db: Database,
    dependencies: ServicesDependencies,
    eventService: EventService,
    lifecycle: ApplicationLifecycle,
    notificationsService: NotificationService,
)(implicit ec: ExecutionContext) {

  import dependencies.ioRuntime

  private val credentials: AwsBasicCredentials = AwsBasicCredentials.create(
    config.filesOvhS3AccessKey,
    config.filesOvhS3SecretKey
  )

  private def fileAAD(fileId: UUID): String = s"File_$fileId"

  private val ovhS3Client = S3AsyncClient
    .builder()
    .credentialsProvider(StaticCredentialsProvider.create(credentials))
    .endpointOverride(URI.create(config.filesOvhS3Endpoint))
    .region(Region.of(config.filesOvhS3Region))
    .build()

  lifecycle.addStopHook { () =>
    Future(ovhS3Client.close())
  }

  private val bucket = BucketName(NonEmptyString.unsafeFrom(config.filesOvhS3Bucket))
  private def s3fileName(fileId: UUID) = FileKey(NonEmptyString.unsafeFrom(s"$fileId"))

  def ovhS3 = S3.create(S3Interpreter[IO].create(ovhS3Client))

  // Play is supposed to give us temporary files here
  def saveFiles(
      pathsWithFilenames: List[(NioPath, String)],
      document: FileMetadata.Attached,
      uploader: User
  )(implicit
      request: Request[_]
  ): Future[Either[Error, List[FileMetadata]]] = {
    val result: EitherT[Future, Error, List[(NioPath, FileMetadata)]] =
      pathsWithFilenames.traverse { case (path, filename) =>
        val metadata = FileMetadata(
          id = UUID.randomUUID(),
          uploadDate = Instant.now(),
          filename = normalizeNFKC(filename),
          // Note that Play does it that way: https://github.com/playframework/playframework/blob/fbe1c146e17ad3a0dc58d65ffd30c6640602f33d/core/play/src/main/scala/play/api/mvc/Results.scala#L651
          filesize = Files.size(path).toInt,
          status = FileMetadata.Status.Scanning,
          attached = document,
          encryptionKeyId = config.filesCurrentEncryptionKeyId.some,
        )
        EitherT(insertMetadata(metadata)).map(_ => (path, metadata))
      }

    // Scan in background, only on success, and sequentially
    result.value.foreach {
      case Right(metadataList) =>
        handleUploadedFilesAndDeleteFromFs(
          metadataList.map { case (path, metadata) => (FsPath.fromNioPath(path), metadata) },
          uploader
        ).unsafeRunAndForget()
      case _ =>
    }

    result.map(_.map { case (_, metadata) => metadata }).value
  }

  /** This is the "official" way to check https://stackoverflow.com/a/56038360
    *
    * The AWS library throws software.amazon.awssdk.services.s3.model.NoSuchKeyException when the
    * file does not exist.
    *
    * See also https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html
    */
  def fileExistsOnS3(fileId: UUID): IO[Either[Error, Boolean]] =
    S3Interpreter[IO]
      .create(ovhS3Client)
      .headObject(
        HeadObjectRequest
          .builder()
          .bucket(bucket.value.value)
          .key(s3fileName(fileId).value.value)
          .build()
      )
      .map(_ => true)
      .recover { case _: NoSuchKeyException => false }
      .attempt
      .map(
        _.left.map(e =>
          Error.MiscException(
            EventType.FileError,
            s"Impossible de vérifier si le fichier $fileId existe",
            e,
            none
          )
        )
      )

  /** Array[Byte] is used here in order to have the least amount of copy. Play uses akka ByteString
    * which is a wrapper around Array[Byte]. We cannot do a 0-copy implementation due to fs2-io not
    * allowing mutable access. This def is written in a way that it does only 1 copy of the data.
    *
    * `.unsafeToPublisher()` is used instead of `.toPublisherResource` because Play cannot handle
    * Resource and using it would have the publisher closed before play begins streaming.
    *
    * See also `.toPublisher` in
    * https://www.javadoc.io/doc/co.fs2/fs2-docs_2.13/3.10.2/fs2/interop/flow/index.html
    *
    * S3 wrapper
    * https://github.com/laserdisc-io/fs2-aws/blob/main/pure-aws/pure-s3-tagless/src/main/scala/io/laserdisc/pure/s3/tagless/Interpreter.scala
    * S3 implementation
    * https://github.com/laserdisc-io/fs2-aws/blob/main/fs2-aws-s3/src/main/scala/fs2/aws/s3/S3.scala
    */
  def fileStream(file: FileMetadata): Either[Error, Source[Array[Byte], _]] =
    file.encryptionKeyId
      .flatMap(config.filesEncryptionKeys.get)
      .toRight(
        Error.EntityNotFound(
          EventType.FileMetadataError,
          s"Clé de chiffrement non trouvée pour le fichier ${file.id}, " +
            s"la clé ${file.encryptionKeyId} n'est pas dans l'environnement",
          none
        )
      )
      .map(decryptionKey =>
        Source
          .fromPublisher(
            FlowAdapters.toPublisher(
              ovhS3
                .readFile(bucket, s3fileName(file.id))
                .through(Crypto.stream.decrypt(fileAAD(file.id), decryptionKey))
                .chunks
                .map(_.toArray) // Copies each chunk into an Array
                .unsafeToPublisher()
            )
          )
      )

  private def uploadFile(path: FsPath, metadata: FileMetadata, uploader: User)(implicit
      request: Request[_]
  ): Stream[IO, Unit] =
    FsFiles[IO]
      .readAll(path)
      .through(Crypto.stream.encrypt(fileAAD(metadata.id), config.filesCurrentEncryptionKey))
      .through(ovhS3.uploadFile(bucket, s3fileName(metadata.id)))
      .evalMap(etag =>
        IO.blocking {
          eventService.logSystem(
            EventType.FileAvailable,
            s"Upload du fichier ${metadata.id} terminé avec etag $etag"
          )
        }
      )
      .evalMap(_ => updateStatus(metadata.id, FileMetadata.Status.Available))
      // Any error in updateStatus, we try to put the status to error and log everything we can
      .evalMap(
        _.fold(
          error =>
            IO.blocking(eventService.logErrorNoUser(error)) >>
              updateStatus(metadata.id, FileMetadata.Status.Error).flatMap(
                _.fold(e => IO.blocking(eventService.logErrorNoUser(e)), _ => IO.pure(()))
              ),
          _ => IO.pure(())
        )
      )
      // Log residual errors and if there are any, try to set file status to Error
      .attempt
      .evalMap(
        _.fold(
          error =>
            IO.blocking(
              eventService.logSystem(
                EventType.FileScanError,
                s"Erreur lors de l'upload ou la recherche de virus dans le fichier ${metadata.id}",
                underlyingException = Some(error)
              )
            ) >>
              updateStatus(metadata.id, FileMetadata.Status.Error).flatMap(
                _.fold(e => IO.blocking(eventService.logErrorNoUser(e)), Function.const(IO.unit))
              ) >>
              IO.blocking(
                notificationsService
                  .fileUploadStatus(metadata.attached, FileMetadata.Status.Error, uploader)
              ),
          _ => IO.pure(())
        )
      )

  /** Will also delete the files */
  private def handleUploadedFilesAndDeleteFromFs(
      metadataList: List[(FsPath, FileMetadata)],
      uploader: User
  )(implicit
      request: Request[_]
  ): IO[Unit] =
    Stream
      .bracket(IO.pure(metadataList)) { metadataList =>
        // Whatever happens, we want the files to be deleted from the filesystem
        metadataList.traverse { case (path, _) => FsFiles[IO].deleteIfExists(path) }.void
      }
      .flatMap(metadataList => Stream.emits(metadataList))
      .flatMap { case (path, metadata) => uploadFile(path, metadata, uploader) }
      .handleErrorWith(error =>
        Stream.eval(
          IO.blocking(
            eventService.logSystem(
              EventType.FileScanError,
              s"Erreur imprévue (bug) durant la recherche de virus dans les fichiers " +
                metadataList.map { case (_, metadata) => metadata.id },
              underlyingException = Some(error)
            )
          )
        )
      )
      .compile
      .drain

  private val (fileMetadataRowParser, tableFields) = Macros.parserWithFields[FileMetadataRow](
    "id",
    "upload_date",
    "filename",
    "filesize",
    "status",
    "application_id",
    "answer_id",
    "encryption_key_id",
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  def fileMetadata(fileId: UUID): IO[Either[Error, Option[FileMetadata]]] =
    IO.blocking(
      db.withConnection { implicit connection =>
        SQL(s"""SELECT $fieldsInSelect FROM file_metadata WHERE id = {fileId}::uuid""")
          .on("fileId" -> fileId)
          .as(fileMetadataRowParser.singleOpt)
      }
    ).attempt
      .map(
        _.left
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

  def queryByApplicationsIdsBlocking(
      applicationIds: List[UUID]
  )(implicit connection: Connection): List[FileMetadata] =
    SQL(
      s"""
        SELECT $fieldsInSelect
        FROM file_metadata
        WHERE application_id = ANY(array[{applicationIds}::uuid[]))
      """
    )
      .on("applicationIds" -> applicationIds)
      .as(fileMetadataRowParser.*)
      .flatMap(_.toFileMetadata)

  def byApplicationId(applicationId: UUID): Future[Either[Error, List[FileMetadata]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          SQL(
            s"""
              SELECT $fieldsInSelect
              FROM file_metadata
              WHERE application_id = {applicationId}::uuid
            """
          )
            .on("applicationId" -> applicationId)
            .as(fileMetadataRowParser.*)
            .flatMap(_.toFileMetadata)
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

  def allOrThrow: List[FileMetadataRow] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect FROM file_metadata""").as(fileMetadataRowParser.*)
    }

  def deleteByIds(fileIds: List[UUID]): IO[Either[Error, Unit]] =
    Stream
      .emits(fileIds)
      .evalMap { fileId =>
        val deletion = ovhS3.delete(bucket, s3fileName(fileId)) >>
          updateStatus(fileId, FileMetadata.Status.Expired).flatMap(
            _.fold(
              e => IO.blocking(eventService.logErrorNoRequest(e)),
              Function.const(IO.unit)
            )
          )
        deletion.recoverWith(e =>
          IO.blocking(
            eventService.logNoRequest(
              EventType.FileDeletionError,
              s"Erreur lors de la suppression d'un fichier",
              underlyingException = e.some
            )
          )
        )
      }
      .compile
      .drain
      .map(_.asRight)

  def wipeFilenamesByIdsBlocking(fileIds: List[UUID])(implicit connection: Connection): Int =
    SQL"""UPDATE file_metadata
          SET filename = 'fichier-non-existant'
          WHERE id = ANY(array[{fileIds}::uuid[]))""".executeUpdate()

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
              answer_id,
              encryption_key_id
            ) VALUES (
              ${row.id}::uuid,
              ${row.uploadDate},
              ${row.filename},
              ${row.filesize},
              ${row.status},
              ${row.applicationId}::uuid,
              ${row.answerId}::uuid,
              ${row.encryptionKeyId}
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

  private def updateStatus(id: UUID, status: FileMetadata.Status): IO[Either[Error, Unit]] =
    IO.blocking {
      val rawStatus = FileMetadataRow.statusFromFileMetadata(status)
      db.withConnection { implicit connection =>
        SQL"""
            UPDATE file_metadata
            SET status = $rawStatus
            WHERE id = $id::uuid
            """.executeUpdate()
      }
    }.attempt
      .map(
        _.left
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
