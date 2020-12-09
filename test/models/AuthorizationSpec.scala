package models

import models.Answer.AnswerType
import models.Application.{MandatType, SeenByUser}
import models.Authorization.UserRight.Helper
import models.Authorization.{answerFileCanBeShowed, applicationFileCanBeShowed, UserRights}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import java.time.ZonedDateTime
import java.time.ZonedDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

@RunWith(classOf[JUnitRunner])
class AuthorizationSpec extends Specification {

  "Authorization FileCanBeShowed should returns" >> {
    "for an helper" >> {
      "for an application file" >> {

        "true for a not expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(Helper))
          val fileExpirationDate = 10
          val overrun = -3

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now().minusDays(fileExpirationDate + overrun),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map.empty[UUID, String],
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          applicationFileCanBeShowed(fileExpirationDate)(application)(userId, rights) should beTrue
        }

        "false for an expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(Helper))
          val fileExpirationDate = 10
          val overrun = 10

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now().minusDays(fileExpirationDate + overrun),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map.empty[UUID, String],
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          applicationFileCanBeShowed(fileExpirationDate)(application)(userId, rights) should beFalse
        }

        "false if i'm not the creator" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(Helper))
          val fileExpirationDate = 10

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = UUID.randomUUID(),
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map.empty[UUID, String],
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          applicationFileCanBeShowed(fileExpirationDate)(application)(userId, rights) should beFalse
        }
      }

      "for an answer file" >> {

        def createAnswer(applicationId: UUID, date: ZonedDateTime) = Answer(
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

        "true for a not expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(Helper))
          val fileExpirationDate = 10
          val overrun = -3

          val answer = createAnswer(applicationId, now().minusDays(fileExpirationDate + overrun))

          val application = Application(
            id = applicationId,
            answers = List(answer),
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map.empty[UUID, String],
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          answerFileCanBeShowed(fileExpirationDate)(application, answer.id)(
            userId,
            rights
          ) should beTrue
        }

        "false for an expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(Helper))
          val fileExpirationDate = 10
          val overrun = 2

          val answer = createAnswer(applicationId, now().minusDays(fileExpirationDate + overrun))

          val application = Application(
            id = applicationId,
            answers = List(answer),
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map.empty[UUID, String],
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          answerFileCanBeShowed(fileExpirationDate)(application, answer.id)(
            userId,
            rights
          ) should beFalse
        }

      }

    }

  }

}
