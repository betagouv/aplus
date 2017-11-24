package models

import org.joda.time.DateTime

case class Application(id: String,
                       status: String,
                       creationDate: DateTime,
                       helperName: String,
                       helperUserId: String,
                       subject: String,
                       description: String,
                       userInfos: Map[String, String],
                       invitedUserIDs: List[String],
                       area: String,
                       answers: List[Answer] = List())