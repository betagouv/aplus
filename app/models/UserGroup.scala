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
    email: Option[String] = None
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

  lazy val toLogString: String =
    "[" + List[(String, String)](
      ("Id", id.toString),
      ("Nom", nameLog),
      ("Description", descriptionLog),
      ("BAL", emailLog),
      ("Organisme", organisationLog),
      ("Territoires", areaIdsLog)
    ).map { case (fieldName, value) => s"$fieldName : $value" }.mkString(" | ") + "]"

  def toDiffLogString(other: UserGroup): String = {
    val diffs: List[String] = List[(String, Boolean, String, String)](
      ("Id", id =!= other.id, id.toString, other.id.toString),
      ("Nom", name =!= other.name, nameLog, other.nameLog),
      ("Description", description =!= other.description, descriptionLog, other.descriptionLog),
      ("BAL", email =!= other.email, emailLog, other.emailLog),
      ("Organisme", organisation =!= other.organisation, organisationLog, other.organisationLog),
      ("Territoires", areaIds =!= other.areaIds, areaIdsLog, other.areaIdsLog),
    ).collect { case (name, true, thisValue, thatValue) => s"$name : $thisValue -> $thatValue" }
    "[" + diffs.mkString(" | ") + "]"
  }

}
