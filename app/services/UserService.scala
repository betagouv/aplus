package services

import javax.inject.Inject

import models.User
import utils.Hash

@javax.inject.Singleton
class UserService @Inject()(configuration: play.api.Configuration) {
  private lazy val cryptoSecret = configuration.underlying.getString("play.http.secret.key")

  private var users = List(
    User("sabine", Hash.sha256(s"sabine$cryptoSecret"), "Sabine", "Assistante Sociale de la ville d'Argenteuil", "sabine@assistante-sociale.fr", true, false, List("argenteuil")),
    User("dila", Hash.sha256(s"dila$cryptoSecret"), "Zohra", "DILA", "zohra@dila.gouv.fr", true, true, List()),
    User("jean", Hash.sha256(s"jean$cryptoSecret"), "Jean DUCAFE", "CAF", "jean@caf.fr", true, true, List("argenteuil")),
    User("paul", Hash.sha256(s"paul$cryptoSecret"), "Paul MURSSAF", "URSSAF", "paul@ursaff.fr", true, true, List("argenteuil")),
    User("amelie", Hash.sha256(s"amelie$cryptoSecret"), "Amelie LASANTE", "CPAM", "sabine@cpam.fr", true, true, List("argenteuil")),
    User("hugo", Hash.sha256(s"jean$cryptoSecret"), "Hugo DECAFE", "CAF", "hugo@caf.fr", true, true, List("argenteuil")),
    User("marine", Hash.sha256(s"paul$cryptoSecret"), "Marine DURSSAF", "URSSAF", "marine@ursaff.fr", true, true, List("argenteuil")),
    User("jeanne", Hash.sha256(s"amelie$cryptoSecret"), "Jeanne de SANTE", "CPAM", "jeanne@cpam.fr", true, true, List("argenteuil"))
  )

  def all() = users

  def byId(id: String): Option[User] = all.find(_.id == id)

  def byKey(key: String): Option[User] = all.find(_.key == key)

  def byEmail(email: String): Option[User] = all.find(_.email.toLowerCase() == email.toLowerCase())
}