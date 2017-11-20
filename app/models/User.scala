package models

case class User(id: String,
                name: String,
                qualite: String,
                email: String,
                helper: Boolean,
                instructor: Boolean,
                areas: List[String])

object User {
  val all = List(
    User("sabine", "Sabine", "Assistante Sociale de la ville d'Argenteuil", "sabine@assistante-sociale.fr", true, false, List("argenteuil")),
    User("dila", "Zohra", "DILA", "zohra@dila.gouv.fr", true, true, List()),
    User("jean", "Jean DUCAFE", "CAF", "jean@caf.fr", true, true, List("argenteuil")),
    User("paul", "Paul MURSSAF", "URSSAF", "paul@ursaff.fr", true, true, List("argenteuil")),
    User("amelie", "Amelie LASANTE", "CPAM", "sabine@cpam.fr", true, true, List("argenteuil"))
  )
  def get(id: String): Option[User] = all.find(_.id == id)
}
