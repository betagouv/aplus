package models

import java.util.UUID

import extentions.{Hash, UUIDHelper}

case class User(id: UUID,
                key: String,
                name: String,
                qualite: String,
                email: String,
                helper: Boolean,
                instructor: Boolean,
                admin: Boolean,
                areas: List[UUID]) {
  def nameWithQualite = s"$name ( $qualite )"
}

object User {
  val admins =  List(
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "DILA", "zohra.lebel@dila.gouv.fr", true, true, true, List()),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan"), "Yan TANGUY", "DILA", "yan.tanguy@dila.gouv.fr", true, true, true, List()),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "BETAGOUV", "julien.dauphant@beta.gouv.fr", true, true, true, List())
  )
}