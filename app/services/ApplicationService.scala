package services

import javax.inject.Inject

import models.{Answer, Application, User}
import org.joda.time.DateTime

@javax.inject.Singleton
class ApplicationService @Inject()() {

  private val sabineAuthor = "Sabine, Assistante Sociale de la ville d'Argenteuil"

  private var applications = List(
    Application(
      "1",
      "En cours",
      DateTime.now(),
      sabineAuthor,
      "sabine",
      "Etat dossier CAF de Mr MARTIN John",
      "Bonjour,\nMr MARTIN John a fait transférer son dossier de la CAF de Marseille à la CAF d'Argenteuil, il ne sait pas où en est sa demande et voudrait savoir à qui envoyer ces documents de suivie.\nSon numéro à la CAF de Marseille est le 98767687, il est né le 16 juin 1985.\n\nMerci de votre aide",
      Map("Numéro de CAF" -> "98767687", "Nom de famille" -> "MARTIN", "Prénom" -> "John", "Date de naissance" -> "16 juin 1985"),
      List("jean"),
      "argenteuil"
    ),
    Application(
      "0",
      "Terminé",
      DateTime.now(),
      sabineAuthor,
      "sabine",
      "Demande d'APL de Mme DUPOND Martine",
      "Bonjour,\nMme DUPOND Martine né le 12 juin 1978 a déposé une demande d'APL le 1 octobre.\nPouvez-vous m'indiquez l'état de sa demande ?\n\nMerci de votre réponse",
      Map("Numéro de CAF" -> "38767687", "Nom de famille" -> "DUPOND", "Prénom" -> "Martine", "Date de naissance" -> "12 juin 1978"),
      List("jean"),
      "argenteuil",
      List(
        Answer("0", DateTime.now(), "Je transmets pour info à la CAF", User.all.filter(_.qualite == "DILA").head, User.all.filter(_.qualite == "CAF"), false, "argenteuil"),
        Answer("0", DateTime.now(), "Le dossier de Mme DUPOND a été accepté, elle devrait recevoir une réponse par courrier bientôt.", User.all.filter(_.qualite == "CAF").head, List(), true, "argenteuil")
      )
    )
  )

  def all() = applications

  def byId(id: String) = applications.find(_.id == id)

  def allForHelperUserId(helperUserId: String) = applications.filter(_.helperUserId == helperUserId)

  def allForInvitedUserId(invitedUserId: String) = applications.filter(_.invitedUserIDs.contains(invitedUserId))

  def createApplication(newApplication: Application): Unit = {
    applications = newApplication :: applications
  }

  def add(answer: Answer): Unit = {
    applications = applications.map { application =>
      if (application.id != answer.applicationId) {
        application
      } else {
        application.copy(invitedUserIDs = answer.invitedUsers.map(_.id), answers = application.answers ++ List(answer))
      }
    }
  }
}

