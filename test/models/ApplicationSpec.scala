package models

import java.time.ZonedDateTime
import java.util.UUID

import models.Answer.AnswerType
import models.Application.MandatType
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application should" >> {
    "display 'Archivée' status if application is closed" >> {
      val closed = true
      val application = Application(
        closed = closed,
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String]
      )

      application.status must equalTo("Archivée")
    }

    "display 'répondu' status if there is an answer with the same creator as the application" >> {
      val closed = false
      val applicationCreatorUserId = UUID.randomUUID()

      val answers = List(
        Answer(
          UUID.randomUUID(),
          UUID.randomUUID(),
          ZonedDateTime.now(),
          AnswerType.Custom,
          "message",
          applicationCreatorUserId,
          "createUserName",
          Map.empty[UUID, String],
          visibleByHelpers = false,
          declareApplicationHasIrrelevant = false,
          Option.empty[Map[String, String]]
        ),
        Answer(
          UUID.randomUUID(),
          UUID.randomUUID(),
          ZonedDateTime.now(),
          AnswerType.Custom,
          "message",
          UUID.randomUUID(),
          "createUserName",
          Map.empty[UUID, String],
          visibleByHelpers = false,
          declareApplicationHasIrrelevant = false,
          Option.empty[Map[String, String]]
        )
      )

      val application = Application(
        closed = closed,
        answers = answers,
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = applicationCreatorUserId,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String]
      )

      application.status must equalTo("Répondu")
    }

    "display 'nouvelle' status if there is no answer with the same creator as the application" >> {
      val closed = false

      val answers = List(
        Answer(
          UUID.randomUUID(),
          UUID.randomUUID(),
          ZonedDateTime.now(),
          AnswerType.Custom,
          "message",
          UUID.randomUUID(),
          "createUserName",
          Map.empty[UUID, String],
          visibleByHelpers = false,
          declareApplicationHasIrrelevant = false,
          Option.empty[Map[String, String]]
        ),
        Answer(
          UUID.randomUUID(),
          UUID.randomUUID(),
          ZonedDateTime.now(),
          AnswerType.Custom,
          "message",
          UUID.randomUUID(),
          "createUserName",
          Map.empty[UUID, String],
          visibleByHelpers = false,
          declareApplicationHasIrrelevant = false,
          Option.empty[Map[String, String]]
        )
      )

      val application = Application(
        closed = closed,
        answers = answers,
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String]
      )

      application.status must equalTo("Nouvelle")
    }

  }

}
