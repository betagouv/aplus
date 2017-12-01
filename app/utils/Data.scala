package utils

import models._

object Data {
  var areas = List(
    Area(UUIDHelper.namedFrom("argenteuil"), "Argenteuil"),
    Area(UUIDHelper.namedFrom("besancon"), "Besançon"),
    Area(UUIDHelper.namedFrom("exemple"), "Demo")
  )
  var users = List(
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "DILA", "zohra.lebel@dila.gouv.fr", true, true, true, List()),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan"), "Yan TANGUY", "DILA", "yan.tanguy@dila.gouv.fr", true, true, true, List()),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "BETAGOUV", "julien.dauphant@beta.gouv.fr", true, true, true, List())
  )
}
