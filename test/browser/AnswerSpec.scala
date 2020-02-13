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

  def generateGroup(groupService: UserGroupService): UserGroup = {
    val number = scala.util.Random.nextInt()
    val area = Area.all.head.id
    val group = UserGroup(
      id = UUIDHelper.randomUUID,
      name = s"Group $number",
      description = None,
      inseeCode = List("0"),
      creationDate = Time.now(),
      areaIds = area :: Nil
    )
    groupService.add(group)
    group
  }

  def generateHelper(group: UserGroup, userService: UserService): User = {
    val email = "helper-test" +
      group.name.toLowerCase.replaceAllLiterally(" ", "_") +
      "@example.com"
    val user = User(
      id = UUIDHelper.randomUUID,
      key = "key",
      name = s"J'aide TEST ${group.name}",
      qualite = s"Aidant Testeur (${group.name})",
      email = email,
      helper = true,
      instructor = false,
      admin = false,
      areas = group.areaIds,
      creationDate = Time.now(),
      communeCode = "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.now()),
      groupIds = List(group.id)
    )
    val result = userService.add(List(user))
    result.isRight must beTrue
    user
  }

  def generateInstructor(group: UserGroup, userService: UserService): User = {
    val email = "instructor-test" +
      group.name.toLowerCase.replaceAllLiterally(" ", "_") +
      "@example.com"
    val user = User(
      id = UUIDHelper.randomUUID,
      key = "key",
      name = s"J'instruit ${group.name}",
      qualite = s"Instructeur Testeur (${group.name})",
      email = email,
      helper = true,
      instructor = true,
      admin = false,
      areas = group.areaIds,
      creationDate = Time.now(),
      communeCode = "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.now()),
      groupIds = List(group.id)
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
      val tokenService = app.injector.instanceOf[TokenService]
      val userService = app.injector.instanceOf[UserService]
      val groupService = app.injector.instanceOf[UserGroupService]
      val eventService = app.injector.instanceOf[EventService]
      val applicationService = app.injector.instanceOf[ApplicationService]

      // Generate data and save in DB
      val group = generateGroup(groupService)
      val instructorUser = generateInstructor(group, userService)
      val helperUser = generateHelper(group, userService)
      val users = List(
        instructorUser,
        helperUser
      )
      users.forall(user => userService.acceptCGU(user.id, false))
      val application =
        generateApplication(helperUser, group, List(instructorUser), applicationService)

      // Helper login
      val loginToken =
        LoginToken.forUserId(helperUser.id, 5, "127.0.0.1")
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
      answer.creatorUserID mustEqual helperUser.id
      // Note: actually uses
      // contextualizedUserName(request.currentUser, currentAreaId)
      answer.creatorUserName mustEqual s"${helperUser.name} (${group.name})"

    }
  }
}
