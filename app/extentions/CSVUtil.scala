package extentions

import models.{Application, Area, User}

object CSVUtil {

  def escape(content: String): String = {
    "\"" + content.filterNot(_ == '\n').replace("\"", "\"\"") + "\""
  }

  def getCSVLineFor(application: Application, users: List[User], requestedByAdmin: Boolean): String = {
    val id = application.id
    val status = application.status
    val created = application.creationDate
    val creatorName = escape(application.creatorUserName)
    val creatorId = application.creatorUserId
    val subject = escape(application.subject)
    val invited = escape(application.invitedUsers.values.mkString(","))
    val administrations = escape(application.administrations(users).mkString(","))
    val area = escape(Area.all.find(_.id == application.area).map(_.name).head)
    val closed = application.closedDate.getOrElse("")
    val adminSpecific = if (requestedByAdmin) {
      val irrelevant = if (application.irrelevant) "Oui"
      else "Non"
      irrelevant + ";" + application.usefulness.getOrElse("?")
    } else {
      ""
    }
    val answer = application.firstAnswerTimeInMinutes.map(_.toString).getOrElse("")
    val resolution = application.resolutionTimeInMinutes.map(_.toString).getOrElse("")

    s"""$id;$status;$created;$creatorName;$creatorId;$subject;$invited;$administrations;$area;$closed;$adminSpecific;$answer;$resolution"""
  }
}
