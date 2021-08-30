package services

import java.io.File
import java.nio.file.{FileAlreadyExistsException, Files, Path, Paths}
import java.util.UUID

import helper.StringHelper.normalizeNFKC

object AttachmentHelper {
  private val APPLICATION_ID_KEY = "application-id"
  private val ANSWER_ID_KEY = "answer-id"
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

  def retrieveOrGenerateApplicationId(formContent: Map[String, String]): UUID =
    formContent.get(APPLICATION_ID_KEY).map(UUID.fromString).getOrElse(UUID.randomUUID())

  def retrieveOrGenerateAnswerId(formContent: Map[String, String]): UUID =
    formContent.get(ANSWER_ID_KEY).map(UUID.fromString).getOrElse(UUID.randomUUID())

  private def storeAttachments(
      getAttachmentsToStore: => Iterable[(Path, String)],
      applicationId: UUID,
      filesPath: String,
      prefix: String
  ): Map[String, Long] =
    getAttachmentsToStore
      .flatMap({ case (attachmentPath, attachmentName) =>
        storeAttachment(attachmentPath, attachmentName, applicationId, filesPath, prefix)
      })
      .toMap

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
