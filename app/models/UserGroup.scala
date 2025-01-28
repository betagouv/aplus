package models

import cats.syntax.all._
import helper.StringHelper.withQuotes
import java.time.ZonedDateTime
import java.util.UUID

object UserGroup {

  val nameMaxLength: Int = 100

}

case class UserGroup(
    id: UUID,
    name: String,
    description: Option[String],
    inseeCode: List[String],
    creationDate: ZonedDateTime,
    areaIds: List[UUID],
    organisationId: Option[Organisation.Id] = None,
    email: Option[String] = None,
    isInFranceServicesNetwork: Boolean,
    // This is a note displayed to users trying to select this group
    publicNote: Option[String],
    // This is a comment only visible by the admins
    internalSupportComment: Option[String]
) {

  lazy val organisation: Option[Organisation] = organisationId.flatMap(Organisation.byId)

  lazy val nameLog: String = withQuotes(name)
  lazy val descriptionLog: String = description.map(withQuotes).getOrElse("<vide>")
  lazy val emailLog: String = email.map(withQuotes).getOrElse("<vide>")
  lazy val organisationLog: String = organisationId.map(_.id).getOrElse("<vide>")
  lazy val areaIdsLog: String = areaIds.mkString(", ")
  lazy val publicNoteLog: String = publicNote.map(withQuotes).getOrElse("<vide>")

  lazy val internalSupportCommentLog: String =
    internalSupportComment.map(withQuotes).getOrElse("<vide>")

  lazy val toLogString: String =
    "[" + List[(String, String)](
      ("Id", id.toString),
      ("Nom", nameLog),
      ("Description", descriptionLog),
      ("BAL", emailLog),
      ("Organisme", organisationLog),
      ("Territoires", areaIdsLog),
      ("Notice", publicNoteLog),
      ("Information Support", internalSupportCommentLog),
    ).map { case (fieldName, value) => s"$fieldName : $value" }.mkString(" | ") + "]"

  def toDiffLogString(other: UserGroup): String = {
    val diffs: List[String] = List[(String, Boolean, String, String)](
      ("Id", id =!= other.id, id.toString, other.id.toString),
      ("Nom", name =!= other.name, nameLog, other.nameLog),
      ("Description", description =!= other.description, descriptionLog, other.descriptionLog),
      ("BAL", email =!= other.email, emailLog, other.emailLog),
      (
        "Organisme",
        organisationId =!= other.organisationId,
        organisationLog,
        other.organisationLog
      ),
      ("Territoires", areaIds =!= other.areaIds, areaIdsLog, other.areaIdsLog),
      ("Notice", publicNote =!= other.publicNote, publicNoteLog, other.publicNoteLog),
      (
        "Information Support",
        internalSupportCommentLog =!= other.internalSupportCommentLog,
        internalSupportCommentLog,
        other.internalSupportCommentLog
      ),
    ).collect { case (name, true, thisValue, thatValue) => s"$name : $thisValue -> $thatValue" }
    "[" + diffs.mkString(" | ") + "]"
  }

}
