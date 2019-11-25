package models

import java.util.UUID

import constants.Constants
import extentions.{Hash, UUIDHelper}
import org.joda.time.DateTime

case class User(id: UUID,
                key: String,
                name: String,
                qualite: String,
                email: String,
                helper: Boolean,
                instructor: Boolean,
                admin: Boolean,
                areas: List[UUID],
                creationDate: DateTime,
                @deprecated
                hasAcceptedCharte: Boolean,
                communeCode: String,
                groupAdmin: Boolean,
                disabled: Boolean,
                expert: Boolean = false,
                groupIds: List[UUID] = List(),
                delegations: Map[String, String] = Map(),
                cguAcceptationDate: Option[DateTime] = None,
                newsletterAcceptationDate: Option[DateTime] = None) extends AgeModel {
  def nameWithQualite = s"$name ( $qualite )"

  def canBeEditedBy(user: User): Boolean =
    user.admin && user.areas.intersect(user.areas).nonEmpty

  def canSeeUsersInArea(areaId: UUID): Boolean =
    (areaId == Area.allArea.id || areas.contains(areaId)) && (admin || groupAdmin)

}

object User {
  private val date = DateTime.parse("2017-11-01T00:00+01:00")
  val systemUser = User(UUIDHelper.namedFrom("system"), Hash.sha256(s"system"), "Système A+", "System A+", Constants.supportEmail, false, false, false, List(), date, false, "75056", false, disabled = true)

  val admins = List(
    // Enabled
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "Experte A+", "zohra.lebel@beta.gouv.fr", true, false, true, Area.all.map(_.id), date, true, "75056", true, disabled = false, expert = true, cguAcceptationDate = Some(date)),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "Admin A+", "julien.dauphant@beta.gouv.fr", true, false, true, Area.all.map(_.id), date, true, "75056", true, disabled = false, cguAcceptationDate = Some(date)),
    User(UUIDHelper.namedFrom("thibault"), Hash.sha256(s"thibault"), "Thibault DESJARDINS", "Expert A+", "thibault.desjardins@beta.gouv.fr", true, false, true, Area.all.map(_.id), date, true, "75056", true, disabled = false, expert = false, cguAcceptationDate = Some(date)),
    User(UUIDHelper.namedFrom("laurent"), Hash.sha256(s"laurent"), "Laurent COURTOIS-COURRET", "Expert A+", "laurent.courtois-courret@beta.gouv.fr", true, false, true, Area.all.map(_.id), date, true, "75056", true, disabled = false, expert = false, cguAcceptationDate = Some(date)),
    User(id = UUIDHelper.namedFrom("lucien"),
      key = Hash.sha256(s"lucien"),
      name = "Lucien PEREIRA",
      qualite = "Expert A+",
      email = "lucien.pereira@beta.gouv.fr",
      helper = true,
      instructor = false,
      admin = true,
      areas = Area.all.map(_.id),
      creationDate = date,
      hasAcceptedCharte = true,
      communeCode = "75056",
      groupAdmin = true,
      disabled = false,
      cguAcceptationDate = Some(date)),

    User(id = UUIDHelper.namedFrom("dunia"),
      key = Hash.sha256("dunia"),
      name = "Dunia El Achcar",
      qualite = "Experte A+",
      email = "dunia.el_achcar@beta.gouv.fr",
      helper = true,
      instructor = false,
      admin = true,
      areas = Area.all.map(_.id),
      creationDate = date,
      hasAcceptedCharte = true,
      communeCode = "75056",
      groupAdmin = true,
      disabled = false,
      cguAcceptationDate = Some(date)),
    // Disabled
    User(UUIDHelper.namedFrom("simon"), Hash.sha256(s"simon - disabled"), "Simon PINEAU", "Expert A+", "simon.pineau@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("louis"), Hash.sha256(s"louis - disabled"), "Louis MOSCAROLA (disabled)", "Expert A+", "louis.moscarola@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan - disabled"), "Yan TANGUY (disabled)", "Aide A+", "yan.tanguy@dila.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("pierre"), Hash.sha256(s"pierre -disabled"), "Pierre MOMBOISSE (disabled)", "Aide A+", "pierre.momboisse@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("dominique"), Hash.sha256(s"dominique - disabled"), "Dominique LEQUEPEYS (disabled)", "Aide A+", "dominique.lequepeys@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("sylvain"), Hash.sha256(s"sylvain - disabled"), "Sylvain DERMY", "Expert A+", "sylvain.dermy@beta.gouv.fr", false, false, false, List.empty, date, false, "75056", false, cguAcceptationDate = Some(date), disabled = true),
  )
}
