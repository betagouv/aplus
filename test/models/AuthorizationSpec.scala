package models

import java.time.ZonedDateTime
import java.time.ZonedDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID
import models.Answer.AnswerType
import models.Application.{MandatType, SeenByUser}
import models.Authorization.UserRight.{HasUserId, Helper, InstructorOfGroups}
import models.Authorization.UserRights
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AuthorizationSpec extends Specification {

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

  "Authorization FileCanBeShown should returns" >> {
    "for an helper" >> {
      "for an application file" >> {

        "true for a not expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), Helper))
          val fileExpirationDate = 10
          val overrun: Long = -3

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now().minusDays(fileExpirationDate + overrun),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            creatorGroupId = None,
            creatorGroupName = None,
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

          val metadata = FileMetadata.Attached.Application(application.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beTrue
        }

        "false for an expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), Helper))
          val fileExpirationDate = 10
          val overrun: Long = 10

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now().minusDays(fileExpirationDate + overrun),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            creatorGroupId = None,
            creatorGroupName = None,
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

          val metadata = FileMetadata.Attached.Application(application.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beFalse
        }

        "false if i'm not the creator" >> {
          val applicationId = randomUUID()
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
            creatorGroupId = None,
            creatorGroupName = None,
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

          val metadata = FileMetadata.Attached.Application(application.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beFalse
        }
      }

      "for an answer file" >> {

        "true for a not expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), Helper))
          val fileExpirationDate = 10
          val overrun: Long = -3

          val answer = createAnswer(applicationId, now().minusDays(fileExpirationDate + overrun))

          val application = Application(
            id = applicationId,
            answers = List(answer),
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            creatorGroupId = None,
            creatorGroupName = None,
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

          val metadata = FileMetadata.Attached.Answer(application.id, answer.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beTrue
        }

        "false for an expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), Helper))
          val fileExpirationDate = 10
          val overrun: Long = 2

          val answer = createAnswer(applicationId, now().minusDays(fileExpirationDate + overrun))

          val application = Application(
            id = applicationId,
            answers = List(answer),
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = userId,
            creatorGroupId = None,
            creatorGroupName = None,
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

          val metadata = FileMetadata.Attached.Answer(application.id, answer.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beFalse
        }

      }

    }

    "for an instructor" >> {
      "for an application file" >> {

        "true for a not expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), InstructorOfGroups(Set.empty[UUID])))
          val fileExpirationDate = 10
          val overrun: Long = -3

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now().minusDays(fileExpirationDate + overrun),
            creatorUserName = "Mathieu",
            creatorUserId = UUID.randomUUID(),
            creatorGroupId = None,
            creatorGroupName = None,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map(userId -> ""),
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          val metadata = FileMetadata.Attached.Application(application.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beTrue
        }

        "false for an expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), InstructorOfGroups(Set.empty[UUID])))
          val fileExpirationDate = 10
          val overrun: Long = 10

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now().minusDays(fileExpirationDate + overrun),
            creatorUserName = "Mathieu",
            creatorUserId = UUID.randomUUID(),
            creatorGroupId = None,
            creatorGroupName = None,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map(userId -> ""),
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          val metadata = FileMetadata.Attached.Application(application.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beFalse
        }

        "false if i'm not invited on the application" >> {
          val applicationId = randomUUID()
          val rights = UserRights(Set(InstructorOfGroups(Set.empty[UUID])))
          val fileExpirationDate = 10

          val answers = List.empty[Answer]

          val application = Application(
            id = applicationId,
            answers = answers,
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = UUID.randomUUID(),
            creatorGroupId = None,
            creatorGroupName = None,
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

          val metadata = FileMetadata.Attached.Application(application.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beFalse
        }
      }

      "for an answer file" >> {

        "true for a not expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), InstructorOfGroups(Set.empty[UUID])))
          val fileExpirationDate = 10
          val overrun: Long = -3

          val answer = createAnswer(applicationId, now().minusDays(fileExpirationDate + overrun))

          val application = Application(
            id = applicationId,
            answers = List(answer),
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = UUID.randomUUID(),
            creatorGroupId = None,
            creatorGroupName = None,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map(userId -> ""),
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          val metadata = FileMetadata.Attached.Answer(application.id, answer.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beTrue
        }

        "false for an expired date" >> {
          val applicationId = randomUUID()
          val userId = randomUUID()
          val rights = UserRights(Set(HasUserId(userId), InstructorOfGroups(Set.empty[UUID])))
          val fileExpirationDate = 10
          val overrun: Long = 2

          val answer = createAnswer(applicationId, now().minusDays(fileExpirationDate + overrun))

          val application = Application(
            id = applicationId,
            answers = List(answer),
            seenByUsers = List.empty[SeenByUser],
            creationDate = now(),
            creatorUserName = "Mathieu",
            creatorUserId = UUID.randomUUID(),
            creatorGroupId = None,
            creatorGroupName = None,
            subject = "Sujet",
            description = "Description",
            userInfos = Map.empty[String, String],
            invitedUsers = Map(userId -> ""),
            area = randomUUID(),
            irrelevant = false,
            mandatType = Option.empty[MandatType],
            mandatDate = Option.empty[String],
            invitedGroupIdsAtCreation = List.empty[UUID]
          )

          val metadata = FileMetadata.Attached.Answer(application.id, answer.id)
          Authorization.fileCanBeShown(fileExpirationDate)(metadata, application)(
            rights
          ) should beFalse
        }

      }

    }

  }

}
