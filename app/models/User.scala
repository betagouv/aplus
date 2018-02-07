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
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "Aide A+ / Je ne sais pas", "zohra.lebel@dila.gouv.fr", true, true, true, List(), date, true),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan"), "Yan TANGUY", "Aide A+ / Je ne sais pas", "yan.tanguy@dila.gouv.fr", true, true, true, List(), date, true),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "Aide A+ / Je ne sais pas", "julien.dauphant@beta.gouv.fr", true, true, true, List(), date, true),
    User(UUIDHelper.namedFrom("dominique"), Hash.sha256(s"dominique"), "Dominique LEQUEPEYS", "Aide A+ / Je ne sais pas", "dominique.lequepeys@beta.gouv.fr", true, true, true, List(), date, true),
  )

  val demo = List(
    User(UUIDHelper.namedFrom("clarisse"), Hash.sha256(s"clarisse"), "Clarisse MAIDE", "Assistante sociale de la ville d'argenteuil", "clarisse@argenteuil.example.com", true, false, false, List(), date, true),
    User(UUIDHelper.namedFrom("david"), Hash.sha256(s"david"), "David AIDE", "Defenseur des droits", "david@ddd.example.com", true, false, false, List(), date, true),
    User(UUIDHelper.namedFrom("jean"), Hash.sha256(s"jean"), "Jean BAUCHE", "Pole Emploi", "jean@pe.example.com", true, true, false, List(), date, true),
    User(UUIDHelper.namedFrom("nina"), Hash.sha256(s"nina"), "Nina EMBAUCHE", "Pole Emploi", "nina@pe.example.com", true, true, false, List(), date, true),
    User(UUIDHelper.namedFrom("anne"), Hash.sha256(s"anne"), "Anne TRESOR", "DGFIP", "anne@dgfip.example.com", true, true, false, List(), date, true),
    User(UUIDHelper.namedFrom("flora"), Hash.sha256(s"flora"), "Flora DUCAFE", "CAF", "flora@caf.example.com", true, true, false, List(), date, true),
    User(UUIDHelper.namedFrom("amelie"), Hash.sha256(s"amelie"), "Amelie LASANTE", "CPAM", "amelie@cpam.example.com", true, true, false, List(), date, true),
    User(UUIDHelper.namedFrom("paul"), Hash.sha256(s"paul"), "Paul RETRAITE", "CNAV", "paul@cnav.example.com", true, true, false, List(), date, true),
    User(UUIDHelper.namedFrom("pierre"), Hash.sha256(s"pierre"), "Pierre SOUTIENT", "MDPH", "pierre@mdph.example.com", true, true, false, List(), date, true),
    User(UUIDHelper.namedFrom("sofia"), Hash.sha256(s"sofia"), "Sofia DEP", "Maison du département", "sofia@dep.example.com", true, true, false, List(), date, true),
  )
}