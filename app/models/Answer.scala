package models

import org.joda.time.DateTime

case class Answer(applicationId: String, creationDate: DateTime, message: String, author: User, users: List[User], last: Boolean, area: String)

object Answer {
  var all = List(
    Answer("0", DateTime.now(), "Je transmets pour info à la CAF", User.all.filter(_.qualite == "DILA").head, User.all.filter(_.qualite == "CAF"), false, "argenteuil"),
    Answer("0", DateTime.now(), "Le dossier de Mme DUPOND a été accepté, elle devrait recevoir une réponse par courrier bientôt.", User.all.filter(_.qualite == "CAF").head, List(), true, "argenteuil")
  )
  def forApplicationId(id: String) = all.filter(_.applicationId == id)
  def add(answer: Answer): Unit = {
    all = all ++ List(answer)
  }
}