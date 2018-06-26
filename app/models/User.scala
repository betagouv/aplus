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
                delegations: Map[String, String] = Map()) {
  def nameWithQualite = s"$name ( $qualite )"
}

object User {
  private val date = DateTime.parse("2017-11-01T00:00+01:00")
  val admins =  List(
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "Aide A+", "zohra.lebel@beta.gouv.fr", true, true, true, List(), date, true),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan"), "Yan TANGUY (disabled)", "Aide A+", "yan.tanguy@dila.gouv.fr - disabled", false, false, false, List(), date, false),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "Aide A+", "julien.dauphant@beta.gouv.fr", true, true, true, List(), date, true),
    User(UUIDHelper.namedFrom("pierre"), Hash.sha256(s"pierre"), "Pierre MOMBOISSE", "Aide A+", "pierre.momboisse@beta.gouv.fr", true, true, true, List(), date, true),
    User(UUIDHelper.namedFrom("dominique"), Hash.sha256(s"dominique"), "Dominique LEQUEPEYS", "Aide A+", "dominique.lequepeys@beta.gouv.fr", true, true, true, List(), date, true),
  )
}
