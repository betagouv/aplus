package models

import java.util.UUID

import extentions.{Hash, UUIDHelper}
import org.joda.time.{DateTime, Days}

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
}

object User {
  private val date = DateTime.parse("2017-11-01T00:00+01:00")
  val systemUser = User(UUIDHelper.namedFrom("system"), Hash.sha256(s"system"), "Système A+", "System A+", "contact@aplus.beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true)

  val admins =  List(
    // Enabled
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "Experte A+", "zohra.lebel@beta.gouv.fr", true, false, true, Area.all.map(_.id), date, true, "75056", true, disabled = false, expert = true, cguAcceptationDate = Some(date)),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "Admin A+", "julien.dauphant@beta.gouv.fr", true, false, true, Area.all.map(_.id), date, true, "75056", true, disabled = false, cguAcceptationDate = Some(date)),
    User(UUIDHelper.namedFrom("sylvain"), Hash.sha256(s"sylvain"), "Sylvain DERMY", "Expert A+", "sylvain.dermy@beta.gouv.fr", true, false, true, Area.all.map(_.id), date, true, "75056", true, disabled = false, expert = true, cguAcceptationDate = Some(date)),
   // Disabled
    User(UUIDHelper.namedFrom("simon"), Hash.sha256(s"simon"), "Simon PINEAU", "Expert A+", "simon.pineau@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("louis"), Hash.sha256(s"louis - disabled"), "Louis MOSCAROLA (disabled)", "Expert A+", "louis.moscarola@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan - disabled"), "Yan TANGUY (disabled)", "Aide A+", "yan.tanguy@dila.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("pierre"), Hash.sha256(s"pierre -disabled"), "Pierre MOMBOISSE (disabled)", "Aide A+", "pierre.momboisse@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
    User(UUIDHelper.namedFrom("dominique"), Hash.sha256(s"dominique - disabled"), "Dominique LEQUEPEYS (disabled)", "Aide A+", "dominique.lequepeys@beta.gouv.fr", false, false, false, List(), date, false, "75056", false, disabled = true),
  )
}
