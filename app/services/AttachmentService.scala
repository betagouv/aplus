package services

import java.io.File
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.util.UUID

import actions.RequestWithUserData
import forms.Models.{AnswerData, ApplicationData}
import play.api.data.Form
import play.api.mvc.{AnyContent, MultipartFormData}

object AttachmentService {

  private val APPLICATION_ID_KEY = "application-id"
  private val ANSWER_ID_KEY = "answer-id"
  private val APPLICATION_PREFIX = "app_"
  private val ANSWER_PREFIX = "ans_"
  private val PENDING_FILE_PREFIX = "pending-file"

  def computeStoreAndRemovePendingAndNewApplicationAttachment(applicationId: UUID, form: Form[ApplicationData], request: RequestWithUserData[AnyContent], filesPath: String): (Map[String, Long], Map[String, Long]) = {
    val attachmentsToDelete = getAttachments(applicationId, filesPath, APPLICATION_PREFIX)
      .filterNot({ case (name, _) => form.data.filter({ case (k, _) => k.startsWith(PENDING_FILE_PREFIX) }).values.toList.contains(name) })
      .keys.toList
    deleteAttachments(applicationId, attachmentsToDelete, filesPath, APPLICATION_PREFIX)
    val newAttachments = storeAttachments(request, applicationId, filesPath, APPLICATION_PREFIX)
    val pendingAttachments = getAttachments(applicationId, filesPath, APPLICATION_PREFIX)
      .filter({ case (name, _) => form.data.filter({ case (k, _) => k.startsWith(PENDING_FILE_PREFIX) }).values.toList.contains(name) })
    pendingAttachments -> newAttachments
  }

  def computeStoreAndRemovePendingAndNewAnswerAttachment(applicationId: UUID, form: Form[AnswerData], request: RequestWithUserData[AnyContent], filesPath: String): (Map[String, Long], Map[String, Long]) = {
    val attachmentsToDelete = getAttachments(applicationId, filesPath, ANSWER_PREFIX)
      .filterNot({ case (name, _) => form.data.filter({ case (k, _) => k.startsWith(PENDING_FILE_PREFIX) }).values.toList.contains(name) })
      .keys.toList
    deleteAttachments(applicationId, attachmentsToDelete, filesPath, ANSWER_PREFIX)
    val newAttachments = storeAttachments(request, applicationId, filesPath, ANSWER_PREFIX)
    val pendingAttachments = getAttachments(applicationId, filesPath, ANSWER_PREFIX)
      .filter({ case (name, _) => form.data.filter({ case (k, _) => k.startsWith(PENDING_FILE_PREFIX) }).values.toList.contains(name) })
    pendingAttachments -> newAttachments
  }

  def retrieveOrGenerateApplicationId(form: Form[ApplicationData]): UUID = {
    form.data.get(APPLICATION_ID_KEY).map(UUID.fromString).getOrElse(UUID.randomUUID())
  }

  def retrieveOrGenerateAnswerId(form: Form[AnswerData]): UUID = {
    form.data.get(ANSWER_ID_KEY).map(UUID.fromString).getOrElse(UUID.randomUUID())
  }

  private def storeAttachments(request: RequestWithUserData[AnyContent], applicationId: UUID, filesPath: String, prefix: String): Map[String, Long] = {
    request.body.asMultipartFormData.map(_.files.filter(_.key.matches("file\\[\\d+\\]")))
      .getOrElse(Nil).flatMap({ attachment => storeAttachment(attachment, applicationId, filesPath, prefix) }).toMap
  }

  private def getAttachments(applicationId: UUID, filesPath: String, prefix: String): Map[String, Long] = {
    val path = new File(s"$filesPath")
    path.listFiles.filter(_.isFile)
      .filter(_.getName.startsWith(s"${prefix}$applicationId"))
      .map(path => storageFilenameToClientFilename(path.getName, applicationId.toString, prefix) -> path.length()).toMap
  }

  private def deleteAttachments(applicationId: UUID, attachments: List[String], filesPath: String, prefix: String): Unit = {
    val path = new File(s"$filesPath")
    path.listFiles.filter(_.isFile)
      .filter(f => attachments.contains(storageFilenameToClientFilename(f.getName, applicationId.toString, prefix)))
      .foreach(_.delete())
  }

  private def storageFilenameToClientFilename(storageFilename: String, applicationId: String, prefix: String): String = {
    storageFilename.replaceFirst(s"${prefix}$applicationId-", "")
  }

  def storeAttachment(attachment: MultipartFormData.FilePart[play.api.libs.Files.TemporaryFile], applicationId: UUID, filesPath: String, prefix: String): Option[(String, Long)] = {
    if (attachment.filename.isEmpty) {
      None
    } else {
      val filename = Paths.get(attachment.filename).getFileName
      val fileDestination = Paths.get(s"$filesPath/${prefix}$applicationId-$filename")
      try {
        Files.copy(attachment.ref, fileDestination)
        val f: File = new File(fileDestination.toString)
        Some(filename.toString -> f.length())
      } catch {
        case _: FileAlreadyExistsException =>
          val f: File = new File(fileDestination.toString)
          Some(filename.toString -> f.length())
      }
    }
  }
}
