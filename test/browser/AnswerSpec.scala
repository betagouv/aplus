package browser

import helper.{Time, UUIDHelper}
import java.util.UUID
import models.{Application, Area, LoginToken, User, UserGroup}
import org.joda.time.DateTime
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._
import services.{ApplicationService, EventService, TokenService, UserGroupService, UserService}

@RunWith(classOf[JUnitRunner])
class AnswerSpec extends Specification with Tables with BaseSpec {

  def generateGroup(testSeed: Int, groupService: UserGroupService): UserGroup = {
    val area = Area.all.head.id
    val group = UserGroup(
      id = UUIDHelper.randomUUID,
      name = s"Group $testSeed",
      description = None,
      inseeCode = List("0"),
      creationDate = Time.now(),
      areaIds = area :: Nil
    )
    groupService.add(group)
    group
  }

  def userId(testSeed: Int, userSeed: String): UUID =
    UUIDHelper.namedFrom(s"$userSeed$testSeed")

  def generateUser(
      testSeed: Int,
      userSeed: String,
      userName: String,
      userQualite: String,
      isHelper: Boolean,
      isInstructor: Boolean,
      isExpert: Boolean,
      groups: List[UserGroup],
      userService: UserService
  ): User = {
    val email = userSeed + testSeed.toString + "@example.com"
    val user = User(
      id = userId(testSeed, userSeed),
      key = "key",
      name = userName,
      qualite = userQualite,
      email = email,
      helper = isHelper,
      instructor = isInstructor,
      admin = false,
      areas = groups.flatMap(_.areaIds),
      creationDate = Time.now(),
      communeCode = "0",
      groupAdmin = false,
      disabled = false,
      expert = isExpert,
      cguAcceptationDate = Some(Time.now()),
      groupIds = groups.map(_.id)
    )
    val result = userService.add(List(user))
    result.isRight must beTrue
    user
  }

  def generateApplication(
      user: User,
      group: UserGroup,
      invitedUsers: List[User],
      applicationService: ApplicationService
  ): Application = {
    // Create Application
    val application = Application(
      UUIDHelper.randomUUID,
      creationDate = Time.now(),
      creatorUserName = user.nameWithQualite,
      creatorUserId = user.id,
      subject = s"Sujet de la demande (aidant ${user.name})",
      description = s"John a un problème (aidant ${user.name})",
      userInfos = Map("Prénom" -> "John", "Nom de famille" -> "Doe", "Date de naissance" -> "1988"),
      invitedUsers = invitedUsers.map(user => (user.id, user.nameWithQualite)).toMap,
      area = group.areaIds.head,
      irrelevant = false
    )
    val result = applicationService.createApplication(application)
    result must beTrue
    application
  }

  "Application" should {
    "Allow answer from certain type of users" in new WithBrowser(
      webDriver = WebDriverFactory(HTMLUNIT),
      app = applicationWithBrowser
    ) {
      "userCodeName" | "expectedError" |
        "instructor-test" ! false |
        "helper-test" ! false |> { (userSeed, shouldExpectAnError) =>
        val tokenService = app.injector.instanceOf[TokenService]
        val userService = app.injector.instanceOf[UserService]
        val groupService = app.injector.instanceOf[UserGroupService]
        val eventService = app.injector.instanceOf[EventService]
        val applicationService = app.injector.instanceOf[ApplicationService]

        // Generate data and save in DB
        val testSeed = scala.util.Random.nextInt()
        val group = generateGroup(testSeed, groupService)
        val instructorUser = generateUser(
          testSeed,
          "instructor-test",
          s"J'instruit $testSeed",
          s"Instructeur Testeur $testSeed",
          true,
          true,
          false,
          List(group),
          userService
        )
        val expertUser = generateUser(
          testSeed,
          "expert-test",
          s"Je suis un expert TEST $testSeed",
          s"Expert $testSeed",
          true,
          false,
          true,
          List(group),
          userService
        )
        val helperUser = generateUser(
          testSeed,
          "helper-test",
          s"J'aide TEST $testSeed",
          s"Aidant Testeur $testSeed",
          true,
          false,
          false,
          List(group),
          userService
        )
        val users = List(
          instructorUser,
          expertUser,
          helperUser
        )
        users.forall(user => userService.acceptCGU(user.id, false))
        val application =
          generateApplication(helperUser, group, List(instructorUser), applicationService)

        // Helper login
        val answerUserId = userId(testSeed, userSeed)
        val loginToken =
          LoginToken.forUserId(answerUserId, 5, "127.0.0.1")
        tokenService.create(loginToken)

        val loginURL = controllers.routes.LoginController
          .magicLinkAntiConsumptionPage()
          .absoluteURL(false, s"localhost:$port")

        browser.goTo(s"$loginURL?token=${loginToken.token}&path=/")

        // Wait for login
        eventually {
          browser.url must endWith(
            controllers.routes.ApplicationController.myApplications().url.substring(1)
          )
        }

        // Submit answer
        val applicationURL =
          controllers.routes.ApplicationController
            .show(application.id)
            .absoluteURL(false, s"localhost:$port")
        browser.goTo(applicationURL)

        val answerMessage = "Il y a juste à faire ça!"

        browser.waitUntil(browser.el(s"textarea[name='message']").clickable())
        browser.el("textarea[name='message']").fill().withText(answerMessage)
        browser.el("button[id='review-validation']").click()

        // Wait for form submit
        eventually {
          browser.pageSource must contain(helperUser.name)
        }

        val changedApplicationOption = applicationService
          .allByArea(group.areaIds.head, false)
          .find(app => (app.id: UUID) == (application.id: UUID))

        changedApplicationOption mustNotEqual None
        val changedApplication = changedApplicationOption.get

        val answer = changedApplication.answers.head
        answer.message mustEqual answerMessage
        answer.creatorUserID mustEqual answerUserId
      // Note: answer.creatorUserName actually uses
      // contextualizedUserName(request.currentUser, currentAreaId)

      }
    }
  }
}
