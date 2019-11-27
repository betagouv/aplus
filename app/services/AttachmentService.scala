package services

import java.io.File
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.util.UUID

import actions.RequestWithUserData
import forms.Models.ApplicationData
import play.api.data.Form
import play.api.mvc.{AnyContent, MultipartFormData}

object AttachmentService {

  private val APPLICATION_ID_KEY = "application-id"

  def computeStoreAndRemovePendingAndNewAttachment(applicationId: UUID, form: Form[ApplicationData], request: RequestWithUserData[AnyContent], filesPath: String): (Map[String, Long], Map[String, Long]) = {
    val attachmentsToDelete = getAttachments(applicationId, filesPath)
      .filterNot({ case (name, _) => form.data.filter({ case (k, _) => k.startsWith("pending-file") }).values.toList.contains(name) })
      .keys.toList
    deleteAttachments(applicationId, attachmentsToDelete, filesPath)
    val newAttachments = storeAttachments(request, applicationId, filesPath)
    val pendingAttachments = getAttachments(applicationId, filesPath)
      .filter({ case (name, _) => form.data.filter({ case (k, _) => k.startsWith("pending-file") }).values.toList.contains(name) })
    pendingAttachments -> newAttachments
  }

  def retrieveOrGenerateApplicationId(form: Form[ApplicationData]): UUID = {
    form.data.get(APPLICATION_ID_KEY).map(UUID.fromString).getOrElse(UUID.randomUUID())
  }

  private def storeAttachments(request: RequestWithUserData[AnyContent], applicationId: UUID, filesPath: String): Map[String, Long] = {
    request.body.asMultipartFormData.map(_.files.filter(_.key.matches("file\\[\\d+\\]")))
      .getOrElse(Nil).flatMap({ attachment => storeAttachment(attachment, applicationId, filesPath) }).toMap
  }

  private def getAttachments(applicationId: UUID, filesPath: String): Map[String, Long] = {
    val path = new File(s"$filesPath")
    path.listFiles.filter(_.isFile)
      .filter(_.getName.startsWith(s"app_$applicationId"))
      .map(path => storageFilenameToClientFilename(path.getName, applicationId.toString) -> path.length()).toMap
  }

  private def deleteAttachments(applicationId: UUID, attachments: List[String], filesPath: String): Unit = {
    val path = new File(s"$filesPath")
    path.listFiles.filter(_.isFile)
      .filter(f => attachments.contains(storageFilenameToClientFilename(f.getName, applicationId.toString)))
      .foreach(_.delete())
  }

  private def storageFilenameToClientFilename(storageFilename: String, applicationId: String): String = {
    storageFilename.replaceFirst(s"app_$applicationId-", "")
  }

  def storeAttachment(attachment: MultipartFormData.FilePart[play.api.libs.Files.TemporaryFile], applicationId: UUID, filesPath: String): Option[(String, Long)] = {
    if (attachment.filename.isEmpty) {
      None
    } else {
      val filename = Paths.get(attachment.filename).getFileName
      val fileDestination = Paths.get(s"$filesPath/app_$applicationId-$filename")
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
