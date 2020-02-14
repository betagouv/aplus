package models

import java.util.UUID

import org.joda.time.DateTime

case class UserGroup(
    id: UUID,
    name: String,
    description: Option[String],
    inseeCode: List[String],
    creationDate: DateTime,
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
      .orElse(UserGroup.organisationDeductedFromName(name))

}

object UserGroup {

  def organisationDeductedFromName(name: String): Option[Organisation] = {
    val lowerCaseName = name.toLowerCase()
    // Hack: `.reverse` the orgs so we can match first MSAP before MSA and
    // Sous-Préf before Préf
    Organisation.all.reverse
      .find { organisation =>
        lowerCaseName.contains(organisation.shortName.toLowerCase()) ||
        lowerCaseName.contains(organisation.name.toLowerCase())
      }
  }

}
