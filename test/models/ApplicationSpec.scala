package models

import java.time.ZonedDateTime.now
import java.util.UUID.randomUUID

class ApplicationSpec extends org.specs2.mutable.Specification {

  def makeApplication() = Application(
    id = randomUUID(),
    creationDate = now(),
    creatorUserName = "creator",
    creatorUserId = randomUUID(),
    subject = "subject",
    description = "description",
    userInfos = Map.empty,
    invitedUsers = Map.empty,
    area = randomUUID(),
    irrelevant = false,
    mandatType = Option.empty,
    mandatDate = Option.empty
  )

  def makeUser() = User(
    id = randomUUID(),
    key = "key",
    name = "name",
    qualite = "qualite",
    email = "email",
    helper = true,
    instructor = false,
    admin = false,
    areas = List.empty,
    creationDate = now(),
    communeCode = "communeCode",
    groupAdmin = false,
    disabled = false
  )

  def makeAnswer() = Answer(
    randomUUID(),
    randomUUID(),
    now(),
    "message",
    randomUUID(),
    "creatorUserName",
    Map.empty,
    visibleByHelpers = true,
    declareApplicationHasIrrelevant = false,
    Option.empty
  )

  "The Application longStatus should" >> {

    "display 'Nouvelle' for a new application" >> {
      val application = makeApplication().copy(closed = false)
      val user = makeUser()

      application.longStatus(user) must be("Nouvelle")
    }

    "display 'Clôturée' if closed is true" >> {
      val application = makeApplication().copy(closed = true)
      val user = makeUser()

      application.longStatus(user) must be("Clôturée")
    }

    "display 'Envoyée'" >> {
      "if the reading user is the creator" >> {
        val creatorId = randomUUID()
        val application =
          makeApplication().copy(
            creatorUserId = creatorId,
            closed = false
          )
        val user = makeUser().copy(id = creatorId)

        application.longStatus(user) must be("Envoyée")
      }

      "if the reading user is the creator and it exists an answer by the creator" >> {
        val creatorId = randomUUID()
        val application =
          makeApplication().copy(
            creatorUserId = creatorId,
            answers = List(makeAnswer().copy(creatorUserID = creatorId)),
            closed = false
          )
        val user = makeUser().copy(id = creatorId)

        application.longStatus(user) must be("Envoyée")
      }
    }

    "display 'Répondu'" >> {
      "if the reading user is the creator and it exists an answer not by the creator" >> {
        val creatorId = randomUUID()
        val application =
          makeApplication().copy(
            creatorUserId = creatorId,
            answers = List(makeAnswer().copy(creatorUserID = randomUUID())),
            closed = false
          )
        val user = makeUser().copy(id = creatorId)

        application.longStatus(user) must be("Répondu")
      }

      // TODO : check functional rule
      "if an answer exists by a user that has the same name as the user qualite" >> {
        val creatorId = randomUUID()
        val userQualite = "qualite"
        val application =
          makeApplication().copy(
            creatorUserId = creatorId,
            answers = List(makeAnswer().copy(creatorUserName = userQualite)),
            closed = false
          )
        val user = makeUser().copy(id = randomUUID(), qualite = userQualite)

        application.longStatus(user) must contain(s"Répondu par $userQualite")
      }
    }

    "display 'Consultée' if the reading user is not the creator and has seen the application" >> {
      val creatorId = randomUUID()
      val userId = randomUUID()
      val application =
        makeApplication().copy(
          creatorUserId = creatorId,
          closed = false,
          seenByUserIds = List(userId)
        )
      val user = makeUser().copy(id = userId)

      application.longStatus(user) must be("Consultée")
    }

  }

}
