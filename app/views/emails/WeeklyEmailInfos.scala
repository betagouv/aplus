package views.emails

import models.{Application, User}

case class WeeklyEmailInfos(user: User, applicationsThatShouldBeClosed: List[Application])
