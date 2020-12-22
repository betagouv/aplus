package models

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

  lazy val toLogString: String =
    s"[Id: '$id' ; " +
      s"Nom : '$name' ; " +
      s"""Description : '${description.getOrElse("<vide>")}' ; """ +
      s"""BAL : '${email.getOrElse("<vide>")}' ; """ +
      s"""Organisme : ${organisation.map(_.id).getOrElse("<vide>")} ; """ +
      s"""Territoires : ${areaIds.mkString(", ")}]"""

}
