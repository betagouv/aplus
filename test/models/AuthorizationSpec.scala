package models

import models.Application.{MandatType, SeenByUser}
import models.Authorization.UserRight.Helper
import models.Authorization.{applicationFileCanBeShowed, UserRights}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import java.time.ZonedDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

@RunWith(classOf[JUnitRunner])
class AuthorizationSpec extends Specification {

  "Authorization FileCanBeShowed should returns" >> {
    "for an helper" >> {
      "for an application file" >> {
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
      }
    }

  }

}
