package services

import java.io.File
import java.nio.file.{FileAlreadyExistsException, Files, Path, Paths}
import java.util.UUID

import helper.StringHelper.normalizeNFKC

import net.scalytica.clammyscan._
import net.scalytica.clammyscan.streams.{ClamError, FileOk, ScannedBody, VirusFound}

object AttachmentHelper {

  private val APPLICATION_PREFIX = "app_"
  private val ANSWER_PREFIX = "ans_"

  def computeStoreAndRemovePendingAndNewApplicationAttachment(
      applicationId: UUID,
      getAttachmentsToStore: => Iterable[(Path, String)],
      filesPath: String
  ): (Map[String, Long], Map[String, Long]) = {
    val newAttachments =
      storeAttachments(getAttachmentsToStore, applicationId, filesPath, APPLICATION_PREFIX)
    val pendingAttachments = getAttachments(applicationId, filesPath, APPLICATION_PREFIX)
    pendingAttachments -> newAttachments
  }

  def computeStoreAndRemovePendingAndNewAnswerAttachment(
      applicationId: UUID,
      getAttachmentsToStore: => Iterable[(Path, String)],
      filesPath: String
  ): (Map[String, Long], Map[String, Long]) = {
    val newAttachments =
      storeAttachments(getAttachmentsToStore, applicationId, filesPath, ANSWER_PREFIX)
    val pendingAttachments = getAttachments(applicationId, filesPath, ANSWER_PREFIX)
    pendingAttachments -> newAttachments
  }

  private def storeAttachments(
      getAttachmentsToStore: => Iterable[(Path, String)],
      applicationId: UUID,
      filesPath: String,
      prefix: String
  ): Map[String, Long] =
    getAttachmentsToStore.flatMap { case (attachmentPath, attachmentName) =>
      storeAttachment(attachmentPath, attachmentName, applicationId, filesPath, prefix)
    }.toMap

  private def getAttachments(
      applicationId: UUID,
      filesPath: String,
      prefix: String
  ): Map[String, Long] = {
    val path = new File(s"$filesPath")
    path.listFiles
      .filter(_.isFile)
      .filter(_.getName.startsWith(s"$prefix$applicationId"))
      .map(path =>
        storageFilenameToClientFilename(path.getName, applicationId.toString, prefix) -> path
          .length()
      )
      .toMap
  }

  private def storageFilenameToClientFilename(
      storageFilename: String,
      applicationId: String,
      prefix: String
  ): String =
    storageFilename.replaceFirst(s"$prefix$applicationId-", "")

  def storeAttachment(
      attachmentPath: Path,
      attachmentName: String,
      applicationId: UUID,
      filesPath: String,
      prefix: String
  ): Option[(String, Long)] = {
    val fileDestination = Paths.get(s"$filesPath/$prefix$applicationId-$attachmentName")
    try {
      Files.copy(attachmentPath, fileDestination)
      val f: File = new File(fileDestination.toString)
      Some(normalizeNFKC(attachmentName) -> f.length())
    } catch {
      case _: FileAlreadyExistsException =>
        val f: File = new File(fileDestination.toString)
        Some(normalizeNFKC(attachmentName) -> f.length())
    }
  }

}
