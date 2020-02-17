package models

import java.util.UUID

import constants.Constants
import helper.{Hash, UUIDHelper}
import org.joda.time.DateTime

case class User(
    id: UUID,
    key: String,
    name: String,
    qualite: String,
    email: String,
    helper: Boolean,
    instructor: Boolean,
    admin: Boolean,
    @deprecated(
      message = "User#areas is deprecated in favor of UserGroup#areaIds.",
      since = "v0.4.3"
    ) areas: List[UUID],
    creationDate: DateTime,
    communeCode: String,
    // If true, this person is managing the groups it is in
    // can see all users in its groups, and add new users in its groups
    // cannot modify users, only admin can.
    groupAdmin: Boolean,
    disabled: Boolean,
    expert: Boolean = false,
    groupIds: List[UUID] = List(),
    cguAcceptationDate: Option[DateTime] = None,
    newsletterAcceptationDate: Option[DateTime] = None,
    phoneNumber: Option[String] = None
) extends AgeModel {
  def nameWithQualite = s"$name ( $qualite )"

  def canBeEditedBy(user: User): Boolean =
    user.admin && user.areas.intersect(user.areas).nonEmpty

  def canSeeUsersInArea(areaId: UUID): Boolean =
    (areaId == Area.allArea.id || areas.contains(areaId)) && (admin || groupAdmin)
}

object User {
  private val date = DateTime.parse("2017-11-01T00:00+01:00")

  val systemUser = User(
    UUIDHelper.namedFrom("system"),
    Hash.sha256(s"system"),
    "Système A+",
    "System A+",
    Constants.supportEmail,
    false,
    false,
    false,
    List(),
    date,
    "75056",
    false,
    disabled = true
  )

  val admins = List(
    // Enabled
    User(
      UUIDHelper.namedFrom("zohra"),
      Hash.sha256(s"zohra"),
      "LEBEL-SEDKI Zohra",
      "Experte A+",
      "zohra.lebel@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      expert = true,
      cguAcceptationDate = Some(date)
    ),
    User(
      UUIDHelper.namedFrom("julien"),
      Hash.sha256(s"julien"),
      "Julien DAUPHANT",
      "Admin A+",
      "julien.dauphant@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      cguAcceptationDate = Some(date)
    ),
    User(
      UUIDHelper.namedFrom("thibault"),
      Hash.sha256(s"thibault"),
      "Thibault DESJARDINS",
      "Expert A+",
      "thibault.desjardins@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      expert = false,
      cguAcceptationDate = Some(date)
    ),
    User(
      UUIDHelper.namedFrom("laurent"),
      Hash.sha256(s"laurent"),
      "Laurent COURTOIS-COURRET",
      "Expert A+",
      "laurent.courtois-courret@beta.gouv.fr",
      true,
      false,
      true,
      Area.all.map(_.id),
      date,
      "75056",
      true,
      disabled = false,
      expert = false,
      cguAcceptationDate = Some(date)
    ),
    User(
      id = UUIDHelper.namedFrom("dunia"),
      key = Hash.sha256("dunia"),
      name = "Dunia El Achcar",
      qualite = "Experte A+",
      email = "dunia.el_achcar@beta.gouv.fr",
      helper = true,
      instructor = false,
      admin = true,
      areas = Area.all.map(_.id),
      creationDate = date,
      communeCode = "75056",
      groupAdmin = true,
      disabled = false,
      cguAcceptationDate = Some(date)
    )
  )
}
