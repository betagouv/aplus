package models

case class User(id: String,
            name: String,
            qualite: String,
            email: String,
            instructor: Boolean)

object User {
  val all = List(
    User("sabine", "Sabine", "Assistante Sociale de la ville d'Argenteuil", "sabine@assistante-sociale.fr", false),
    User("dila", "Zohra", "DILA", "zohra@dila.gouv.fr", true),
    User("jean", "Jean DUCAFE", "CAF", "jean@caf.fr", true),
    User("paul", "Paul MURSSAF", "URSSAF", "paul@ursaff.fr", true),
    User("amelie", "Amelie LASANTE", "CPAM", "sabine@cpam.fr", true)
  )
  def get(id: String): Option[User] = all.find(_.id == id)
}
