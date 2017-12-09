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
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "Aide A+/ Je ne sais pas", "zohra.lebel@dila.gouv.fr", true, true, true, List()),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan"), "Yan TANGUY", "Aide A+ / Je ne sais pas", "yan.tanguy@dila.gouv.fr", true, true, true, List()),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "Aide A+ / Je ne sais pas", "julien.dauphant@beta.gouv.fr", true, true, true, List()),
    User(UUIDHelper.namedFrom("dominique"), Hash.sha256(s"dominique"), "Dominique LEQUEPEYS", "Aide A+ / Je ne sais pas", "dominique.lequepeys@beta.gouv.fr", true, true, true, List()),
  )
}