package utils

import models.{Answer, Application, User}
import org.joda.time.DateTime

object DemoData {
  var argenteuilAreaId = UUIDHelper.namedFrom("argenteuil")
  var users = List(
    User(UUIDHelper.namedFrom("sabine"), Hash.sha256(s"sabine"), "Sabine", "Assistante Sociale de la ville d'Argenteuil", "sabine@assistante-sociale.example.com", true, false, List(argenteuilAreaId)),
    User(UUIDHelper.namedFrom("dominique"), Hash.sha256(s"dominique"), "Dominique", "Accueil association Liberto", "dom@liberto.example.com", true, false, List(argenteuilAreaId)),
    User(UUIDHelper.namedFrom("zohra"), Hash.sha256(s"zohra"), "Zohra LEBEL", "DILA", "zohra.lebel@dila.gouv.fr", true, true, List()),
    User(UUIDHelper.namedFrom("yan"), Hash.sha256(s"yan"), "Yan TANGUY", "DILA", "yan.tanguy@dila.gouv.fr", true, true, List()),
    User(UUIDHelper.namedFrom("julien"), Hash.sha256(s"julien"), "Julien DAUPHANT", "DILA", "julien.dauphant@beta.gouv.fr", true, true, List()),
    User(UUIDHelper.namedFrom("jean"), Hash.sha256(s"jean"), "Jean DUCAFE", "CAF", "jean@caf.example.com", false, true, List(argenteuilAreaId)),
    User(UUIDHelper.namedFrom("paul"), Hash.sha256(s"paul"), "Paul MURSSAF", "URSSAF", "paul@ursaff.example.com", false, true, List(argenteuilAreaId)),
    User(UUIDHelper.namedFrom("amelie"), Hash.sha256(s"amelie"), "Amelie LASANTE", "CPAM", "sabine@cpam.example.com", false, true, List(argenteuilAreaId)),
    User(UUIDHelper.namedFrom("hugo"), Hash.sha256(s"hugo"), "Hugo DECAFE", "CAF", "hugo@caf.example.com", false, true, List(argenteuilAreaId)),
    User(UUIDHelper.namedFrom("marine"), Hash.sha256(s"marine"), "Marine DURSSAF", "URSSAF", "marine@ursaff.example.com", false, true, List(argenteuilAreaId)),
    User(UUIDHelper.namedFrom("jeanne"), Hash.sha256(s"jeanne"), "Jeanne de SANTE", "CPAM", "jeanne@cpam.example.com", false, true, List(argenteuilAreaId))
  )
  private val sabineAuthor = "Sabine, Assistante Sociale de la ville d'Argenteuil"

  var applications = List(
    Application(
      UUIDHelper.namedFrom("application1"),
      "En cours",
      DateTime.now(),
      sabineAuthor,
      UUIDHelper.namedFrom("sabine"),
      "Etat dossier CAF de Mr MARTIN John",
      "Bonjour,\nMr MARTIN John a fait transférer son dossier de la CAF de Marseille à la CAF d'Argenteuil, il ne sait pas où en est sa demande et voudrait savoir à qui envoyer ces documents de suivie.\nSon numéro à la CAF de Marseille est le 98767687, il est né le 16 juin 1985.\n\nMerci de votre aide",
      Map("Numéro de CAF" -> "98767687", "Nom de famille" -> "MARTIN", "Prénom" -> "John", "Date de naissance" -> "16 juin 1985"),
      users.filter(_.qualite == "CAF").map(user => user.id -> user.nameWithQualite).headOption.toMap,
      argenteuilAreaId
    ),
    Application(
      UUIDHelper.namedFrom("application0"),
      "Terminé",
      DateTime.now(),
      sabineAuthor,
      UUIDHelper.namedFrom("sabine"),
      "Demande d'APL de Mme DUPOND Martine",
      "Bonjour,\nMme DUPOND Martine né le 12 juin 1978 a déposé une demande d'APL le 1 octobre.\nPouvez-vous m'indiquez l'état de sa demande ?\n\nMerci de votre réponse",
      Map("Numéro de CAF" -> "38767687", "Nom de famille" -> "DUPOND", "Prénom" -> "Martine", "Date de naissance" -> "12 juin 1978"),
      users.filter(_.qualite == "CAF").map(user => user.id -> user.nameWithQualite).toMap,
      argenteuilAreaId
    )
  )
  var answers = List(
      Answer(UUIDHelper.namedFrom("answer0"),
        UUIDHelper.namedFrom("application0"),
        DateTime.now(),
        "Je transmets pour info à la CAF",
        users.filter(_.qualite == "DILA").head.id,
        users.filter(_.qualite == "DILA").head.nameWithQualite,
        users.filter(_.qualite == "CAF").map(user => user.id -> user.nameWithQualite).toMap, false,
        argenteuilAreaId),
      Answer(UUIDHelper.namedFrom("answer1"),
        UUIDHelper.namedFrom("application0"),
        DateTime.now(),
        "Le dossier de Mme DUPOND a été accepté, elle devrait recevoir une réponse par courrier bientôt.",
        users.filter(_.qualite == "CAF").head.id,
        users.filter(_.qualite == "DILA").head.nameWithQualite,
        Map(),
        true,
        argenteuilAreaId)
  )
}
