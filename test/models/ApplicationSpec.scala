package models

import java.time.ZonedDateTime.now
import java.util.UUID
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

    "display 'Nouvelle'" >> {
      val application = makeApplication().copy(closed = false)
      val user = makeUser()

      application.longStatus(user) must be("Nouvelle")
    }

    "display 'Clôturée' if closed is true" >> {
      val application = makeApplication().copy(closed = true)
      val user = makeUser()

      application.longStatus(user) must be("Clôturée")
    }

    "display 'Envoyée' if user is the creator" >> {
      val userId = UUID.randomUUID()
      val application =
        makeApplication().copy(
          creatorUserId = userId,
          closed = false
        )
      val user = makeUser().copy(id = userId)

      application.longStatus(user) must be("Envoyée")
    }

    "display 'Envoyée' if the user is the creator and it exists an answer by the creator" >> {
      val userId = UUID.randomUUID()
      val application =
        makeApplication().copy(
          creatorUserId = userId,
          answers = List(makeAnswer().copy(creatorUserID = userId)),
          closed = false
        )
      val user = makeUser().copy(id = userId)

      application.longStatus(user) must be("Envoyée")
    }

    "display 'Répondu' if the user is the creator and it exists an answer not by the creator" >> {
      val userId = UUID.randomUUID()
      val application =
        makeApplication().copy(
          creatorUserId = userId,
          answers = List(makeAnswer().copy(creatorUserID = randomUUID())),
          closed = false
        )
      val user = makeUser().copy(id = userId)

      application.longStatus(user) must be("Répondu")
    }

  }

}
