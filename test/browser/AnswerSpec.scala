package browser

import cats.syntax.all._
import helper.{Time, UUIDHelper}
import java.util.UUID
import models.{Application, Area, LoginToken, User, UserGroup}
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._
import services.{ApplicationService, TokenService, UserGroupService, UserService}

@RunWith(classOf[JUnitRunner])
class AnswerSpec extends Specification with Tables with BaseSpec {

  def generateGroup(testSeed: Int, groupSeed: String, groupService: UserGroupService): UserGroup = {
    val area = Area.all.head.id
    val group = UserGroup(
      id = UUIDHelper.randomUUID,
      name = s"Group $groupSeed$testSeed",
      description = None,
      inseeCode = List("0"),
      creationDate = Time.nowParis(),
      areaIds = area :: Nil,
      publicNote = None,
      internalSupportComment = None
    )
    groupService.add(group)
    group
  }

  def userId(testSeed: Int, userSeed: String): UUID =
    UUIDHelper.namedFrom(s"$userSeed$testSeed")

  def generateUser(
      testSeed: Int,
      userSeed: String,
      firstName: String,
      lastName: String,
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
      firstName = firstName.some,
      lastName = lastName.some,
      name = userName,
      qualite = userQualite,
      email = email,
      helper = isHelper,
      instructor = isInstructor,
      admin = false,
      areas = groups.flatMap(_.areaIds),
      creationDate = Time.nowParis(),
      communeCode = "0",
      groupAdmin = false,
      disabled = false,
      expert = isExpert,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = groups.map(_.id),
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
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
      creationDate = Time.nowParis(),
      creatorUserName = user.nameWithQualite,
      creatorUserId = user.id,
      creatorGroupId = None,
      creatorGroupName = None,
      subject = s"Sujet de la demande (aidant ${user.name})",
      description = s"John a un problème (aidant ${user.name})",
      userInfos = Map("Prénom" -> "John", "Nom de famille" -> "Doe", "Date de naissance" -> "1988"),
      invitedUsers = invitedUsers.map(user => (user.id, user.nameWithQualite)).toMap,
      area = group.areaIds.head,
      irrelevant = false,
      expertInvited = true,
      mandatType = Some(Application.MandatType.Paper),
      mandatDate = Some(java.time.ZonedDateTime.now().toString),
      invitedGroupIdsAtCreation = List(group.id)
    )
    val result = applicationService.createApplication(application)
    result must beTrue
    application
  }

  "Application" should {
    "Allow answer from certain type of users" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      "userCodeName" |
        "instructor-test" |
        "invited-expert-test" |
        "invited-user-test" |
        "helper-test" |> { (userSeed: String) =>
          val tokenService = app.injector.instanceOf[TokenService]
          val userService = app.injector.instanceOf[UserService]
          val groupService = app.injector.instanceOf[UserGroupService]
          val applicationService = app.injector.instanceOf[ApplicationService]

          // Generate data and save in DB
          val testSeed = scala.util.Random.nextInt()
          val helperGroup = generateGroup(testSeed, "helper", groupService)
          val answerGroup = generateGroup(testSeed, "answer", groupService)
          val expertGroup = generateGroup(testSeed, "expert", groupService)
          val instructorUser = generateUser(
            testSeed,
            userSeed = "instructor-test",
            firstName = "FirstName",
            lastName = "LastName",
            userName = s"J'instruit $testSeed",
            userQualite = s"Instructeur Testeur $testSeed",
            isHelper = true,
            isInstructor = true,
            isExpert = false,
            groups = List(answerGroup),
            userService = userService
          )
          val invitedExpertUser = generateUser(
            testSeed,
            userSeed = "invited-expert-test",
            firstName = "FirstName",
            lastName = "LastName",
            userName = s"Je suis un expert TEST $testSeed",
            userQualite = s"Expert $testSeed",
            isHelper = true,
            isInstructor = false,
            isExpert = true,
            groups = List(expertGroup),
            userService = userService
          )
          val invitedUser = generateUser(
            testSeed,
            userSeed = "invited-user-test",
            firstName = "FirstName",
            lastName = "LastName",
            userName = s"Je suis un agent TEST $testSeed",
            userQualite = s"Agent $testSeed",
            isHelper = true,
            isInstructor = false,
            isExpert = false,
            groups = List(answerGroup),
            userService = userService
          )
          val helperUser = generateUser(
            testSeed,
            userSeed = "helper-test",
            firstName = "FirstName",
            lastName = "LastName",
            userName = s"J'aide TEST $testSeed",
            userQualite = s"Aidant Testeur $testSeed",
            isHelper = true,
            isInstructor = false,
            isExpert = false,
            groups = List(helperGroup),
            userService = userService
          )
          val users = List(
            instructorUser,
            invitedExpertUser,
            invitedUser,
            helperUser
          )
          users.map(user => userService.validateCGU(user.id))
          val expertInvited = userSeed === "invited-expert-test"
          val userInvited = userSeed === "invited-user-test"
          val invitedUsers =
            List(
              instructorUser.some,
              invitedExpertUser.some.filter(_ => expertInvited),
              invitedUser.some.filter(_ => userInvited)
            ).flatten
          val application =
            generateApplication(
              helperUser,
              helperGroup,
              invitedUsers,
              applicationService
            )

          // Helper login
          val answerUserId = userId(testSeed, userSeed)
          val loginToken =
            LoginToken.forUserId(answerUserId, 5, "127.0.0.1")
          tokenService.create(loginToken)

          val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage
            .absoluteURL(false, s"localhost:$port")

          browser.goTo(s"$loginURL?token=${loginToken.token}&path=/")

          // Wait for login
          eventually {
            browser.url must endWith(
              controllers.routes.ApplicationController.myApplications.url.substring(1)
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
            .allByArea(helperGroup.areaIds.head, anonymous = false)
            .find(app => app.id === application.id)

          changedApplicationOption mustNotEqual None
          val changedApplication = changedApplicationOption.get

          val answer = changedApplication.answers.head
          answer.message mustEqual answerMessage
          answer.creatorUserID mustEqual answerUserId
        }
    }
  }
}
