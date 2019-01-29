package models

import java.util.UUID

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
                hasAcceptedCharte: Boolean,
                communeCode: String,
                delegations: Map[String, String] = Map()) {
  def nameWithQualite = s"$name ( $qualite )"
}

object User {
  private val date = DateTime.parse("2017-11-01T00:00+01:00")
  val admins =  List(
    // Enabled
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "Admin A+", "zohra.lebel@beta.gouv.fr", true, false, true, List(), date, true),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "Admin A+", "julien.dauphant@beta.gouv.fr", true, false, true, List(), date, true),
    User(UUIDHelper.namedFrom("louis"), Hash.sha256(s"louis"), "Louis MOSCAROLA", "Admin A+", "louis.moscarola@beta.gouv.fr", true, false, true, List(), date, true),
    // Disabled
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan - disabled"), "Yan TANGUY (disabled)", "Aide A+", "yan.tanguy@dila.gouv.fr - disabled", false, false, false, List(), date, false),
    User(UUIDHelper.namedFrom("pierre"), Hash.sha256(s"pierre -disabled"), "Pierre MOMBOISSE (disabled)", "Aide A+", "pierre.momboisse@beta.gouv.fr", false, false, false, List(), date, false),
    User(UUIDHelper.namedFrom("simon"), Hash.sha256(s"simon - disabled"), "Simon PINEAU (disabled)", "Aide A+", "simon.pineau@beta.gouv.fr", true, false, true, List(), date, true),
    User(UUIDHelper.namedFrom("dominique"), Hash.sha256(s"dominique - disabled"), "Dominique LEQUEPEYS (disabled)", "Aide A+", "dominique.lequepeys@beta.gouv.fr - disabled", false, false, false, List(), date, true),
  )
}
