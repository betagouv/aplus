package models

case class User(id: String,
            name: String,
            qualite: String,
            email: String,
            instructor: Boolean)

object User {
  val all = List(
    User("sabine", "Sabine", "Assistante Sociale de la ville d'Argenteuil", "sabine@example.com", false),
    User("dila", "Zohra", "DILA", "zohra@example.com", true),
    User("jean", "Jean DUCAFE", "CAF", "jean@example.com", true),
    User("paul", "Paul MURSSAF", "URSSAF", "paul@example.com", true),
    User("amelie", "Amelie LASANTE", "CPAM", "sabine@example.com", true)
  )
  def get(id: String): Option[User] = all.find(_.id == id)
}