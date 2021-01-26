package models

import cats.syntax.all._
import helper.StringHelper.withQuotes
import java.time.ZonedDateTime
import java.util.UUID

case class UserGroup(
    id: UUID,
    name: String,
    description: Option[String],
    inseeCode: List[String],
    creationDate: ZonedDateTime,
    areaIds: List[UUID],
    organisation: Option[Organisation.Id] = None,
    email: Option[String] = None,
    // This is a note displayed to users trying to select this group
    publicNote: Option[String],
    // This is a comment only visible by the admins
    internalSupportComment: Option[String]
) {

  def canHaveUsersAddedBy(user: User): Boolean =
    (user.groupAdmin && user.groupIds.contains(id)) || (user.admin && areaIds.forall(
      user.areas.contains
    ))

  lazy val organisationSetOrDeducted: Option[Organisation] =
    organisation
      .flatMap(Organisation.byId)
      .orElse(Organisation.deductedFromName(name))

  lazy val nameLog: String = withQuotes(name)
  lazy val descriptionLog: String = description.map(withQuotes).getOrElse("<vide>")
  lazy val emailLog: String = email.map(withQuotes).getOrElse("<vide>")
  lazy val organisationLog: String = organisation.map(_.id).getOrElse("<vide>")
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
      ("Organisme", organisation =!= other.organisation, organisationLog, other.organisationLog),
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
