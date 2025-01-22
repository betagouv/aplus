package models

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID
import models.Answer.AnswerType
import models.Application.{MandatType, SeenByUser}
import models.Application.Status.{Archived, New, Processing}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  private def createAnswer(applicationId: UUID, date: ZonedDateTime) = Answer(
    UUID.randomUUID(),
    applicationId,
    date,
    AnswerType.Custom,
    "",
    UUID.randomUUID(),
    "",
    Map.empty[UUID, String],
    visibleByHelpers = true,
    declareApplicationHasIrrelevant = true,
    Option.empty[Map[String, String]],
    invitedGroupIds = List.empty[UUID]
  )

  "Application should" >> {
    "compute all answers as 'new' for a user without seenByUser" >> {
      val applicationId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val answers = List(
        createAnswer(applicationId, ZonedDateTime.now()),
        createAnswer(applicationId, ZonedDateTime.now())
      )

      val application = Application(
        id = applicationId,
        answers = answers,
        seenByUsers = List.empty[SeenByUser],
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.newAnswersFor(userId) must equalTo(answers)
    }

    "compute only new answers as 'new' for a user with seenByUser" >> {
      val applicationId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val firstAnswerDate = ZonedDateTime.of(2020, 11, 1, 15, 36, 0, 0, ZoneId.systemDefault())
      val secondAnswerDate = ZonedDateTime.of(2020, 11, 9, 15, 36, 0, 0, ZoneId.systemDefault())

      val lastVisitDate =
        ZonedDateTime.of(2020, 11, 5, 15, 36, 0, 0, ZoneId.systemDefault()).toInstant

      val answer1 = createAnswer(applicationId, firstAnswerDate)
      val answer2 = createAnswer(applicationId, secondAnswerDate)

      val application = Application(
        id = applicationId,
        answers = List(answer1, answer2),
        seenByUsers = List(SeenByUser(userId, lastVisitDate)),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.newAnswersFor(userId) must equalTo(List(answer2))
    }

    "compute none of the answers as 'new' for a user with a visit after all answers" >> {
      val applicationId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val firstAnswerDate = ZonedDateTime.of(2020, 11, 1, 15, 36, 0, 0, ZoneId.systemDefault())
      val secondAnswerDate = ZonedDateTime.of(2020, 11, 9, 15, 36, 0, 0, ZoneId.systemDefault())

      val lastVisitDate =
        ZonedDateTime.of(2020, 11, 10, 15, 36, 0, 0, ZoneId.systemDefault()).toInstant

      val application = Application(
        id = applicationId,
        answers = List(
          createAnswer(applicationId, firstAnswerDate),
          createAnswer(applicationId, secondAnswerDate)
        ),
        seenByUsers = List(SeenByUser(userId, lastVisitDate)),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.newAnswersFor(userId) must equalTo(List.empty[Answer])
    }

  }

  "Application should display status" >> {
    "'Archivée' if application is closed" >> {
      val closed = true
      val application = Application(
        closed = closed,
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.status must equalTo(Archived)
    }

    "'répondu' if there is an answer with the same creator as the application" >> {
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
          Option.empty[Map[String, String]],
          invitedGroupIds = List.empty[UUID]
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
          Option.empty[Map[String, String]],
          invitedGroupIds = List.empty[UUID]
        )
      )

      val application = Application(
        closed = closed,
        answers = answers,
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = applicationCreatorUserId,
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.status must equalTo(Processing)
    }

    "'nouvelle' if there is no answer with the same creator as the application" >> {
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
          Option.empty[Map[String, String]],
          invitedGroupIds = List.empty[UUID]
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
          Option.empty[Map[String, String]],
          invitedGroupIds = List.empty[UUID]
        )
      )

      val application = Application(
        closed = closed,
        answers = answers,
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.status must equalTo(New)
    }
  }

  "Application displaying should be" >> {
    "false for an application creator" >> {
      val userId = UUID.randomUUID()
      val application = Application(
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = userId,
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.hasBeenDisplayedFor(userId) must beTrue
    }

    "false for an application with the userId in seenByUsers" >> {
      val userId = UUID.randomUUID()
      val application = Application(
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        seenByUsers = List(SeenByUser(userId = userId, lastSeenDate = Instant.now())),
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.hasBeenDisplayedFor(userId) must beTrue
    }

    "true for an application without the userId in seenByUsers" >> {
      val userId = UUID.randomUUID()
      val application = Application(
        id = UUID.randomUUID(),
        creationDate = ZonedDateTime.now(),
        creatorUserName = "Mathieu",
        creatorUserId = UUID.randomUUID(),
        creatorGroupId = None,
        creatorGroupName = None,
        subject = "Sujet",
        description = "Description",
        userInfos = Map.empty[String, String],
        invitedUsers = Map.empty[UUID, String],
        seenByUsers = List.empty[SeenByUser],
        area = UUID.randomUUID(),
        irrelevant = false,
        mandatType = Option.empty[MandatType],
        mandatDate = Option.empty[String],
        invitedGroupIdsAtCreation = List.empty[UUID],
        isInFranceServicesNetwork = true,
      )

      application.hasBeenDisplayedFor(userId) must beFalse
    }
  }

}
