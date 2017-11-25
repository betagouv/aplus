package models

import java.util.UUID

import org.joda.time.DateTime

case class Application(id: UUID,
                       status: String,
                       creationDate: DateTime,
                       helperName: String,
                       helperUserId: UUID,
                       subject: String,
                       description: String,
                       userInfos: Map[String, String],
                       invitedUserIDs: List[UUID],
                       area: UUID){
   def answers = List[Answer]()
}

case class ApplicationWithAnswers(application: Application, answers: List[Answer] = List())