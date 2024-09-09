package browser

import cats.syntax.all._
import helper.{Time, UUIDHelper}
import models.{Application, Area, LoginToken, User, UserGroup}
import org.junit.runner._
import org.specs2.matcher.MatchResult
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test._
import scala.annotation.nowarn
import services._

/** Tests are broken as unit instead of using tables to allow the CI to pass those tests (doesn't
  * work otherwise).
  */
@RunWith(classOf[JUnitRunner])
class ApplicationAccessSpec extends Specification with BaseSpec {

  "Application" should {
    "Allow the instructor to access an Application" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        applicationAccessTest(app, browser, port, "instructor-test", shouldExpectAnError = false)
    }
  }

  "Application" should {
    "Not Allow an unrelated instructor to access an Application" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        applicationAccessTest(
          app,
          browser,
          port,
          "instructor-test-unrelated",
          shouldExpectAnError = true
        )
    }
  }

  "Application" should {
    "Allow the helper to access an Application" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        applicationAccessTest(app, browser, port, "helper-test", shouldExpectAnError = false)
    }
  }

  "Application" should {
    "Allow an related helper to access an Application" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        applicationAccessTest(app, browser, port, "helper-test-friend", shouldExpectAnError = false)
    }
  }

  "Application" should {
    "Not Allow an unrelated helper to access an Application" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        applicationAccessTest(
          app,
          browser,
          port,
          "helper-test-unrelated",
          shouldExpectAnError = true
        )
    }
  }

  "Application" should {
    "Not Allow an unrelated expert to access an Application" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        applicationAccessTest(
          app,
          browser,
          port,
          "expert-test-unrelated",
          shouldExpectAnError = true
        )
    }
  }

  "Application" should {
    "Not Allow a manager to access an Application" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        applicationAccessTest(app, browser, port, "helper-test-manager", shouldExpectAnError = true)
    }
  }

  def applicationAccessTest(
      app: play.api.Application,
      browser: TestBrowser,
      port: Int,
      userCodeName: String,
      shouldExpectAnError: Boolean
  ): MatchResult[String] = {
    val tokenService = app.injector.instanceOf[TokenService]
    val userService = app.injector.instanceOf[UserService]
    val groupService = app.injector.instanceOf[UserGroupService]
    val applicationService = app.injector.instanceOf[ApplicationService]

    val number = scala.util.Random.nextInt()
    val area = Area.all.head.id
    val instructorGroup = UserGroup(
      id = UUIDHelper.randomUUID,
      name = s"Instructor Group $number",
      description = None,
      inseeCode = List("0"),
      creationDate = Time.nowParis(),
      areaIds = area :: Nil,
      publicNote = None,
      internalSupportComment = None
    )
    groupService.add(instructorGroup)
    val helperGroup = UserGroup(
      id = UUIDHelper.randomUUID,
      name = s"Helper Group $number",
      description = None,
      inseeCode = List("0"),
      creationDate = Time.nowParis(),
      areaIds = area :: Nil,
      publicNote = None,
      internalSupportComment = None
    )
    groupService.add(helperGroup)

    val instructorUser = User(
      UUIDHelper.namedFrom(s"instructor-test$number"),
      "key",
      "FirstName".some,
      "LastName".some,
      s"J'instruit TEST $number",
      s"Instructeur Testeur $number",
      s"instructor-test$number@example.com",
      helper = true,
      instructor = true,
      admin = false,
      List(area),
      Time.nowParis(),
      "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = List(instructorGroup.id),
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
    )
    val unrelatedInstructorUser = User(
      UUIDHelper.namedFrom(s"instructor-test-unrelated$number"),
      "key",
      "FirstName".some,
      "LastName".some,
      s"Je n'instruit pas TEST $number",
      s"Instructeur Testeur $number",
      s"instructor-test-unrelated$number@example.com",
      helper = true,
      instructor = true,
      admin = false,
      List(area),
      Time.nowParis(),
      "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = Nil,
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
    )
    val helperUser = User(
      UUIDHelper.namedFrom(s"helper-test$number"),
      "key",
      "FirstName".some,
      "LastName".some,
      s"J'aide TEST $number",
      "Aidant Testeur",
      s"helper-test$number@example.com",
      helper = true,
      instructor = false,
      admin = false,
      List(area),
      Time.nowParis(),
      "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = List(helperGroup.id),
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
    )
    val helperFriendUser = User(
      UUIDHelper.namedFrom(s"helper-test-friend$number"),
      "key",
      "FirstName".some,
      "LastName".some,
      s"Je suis collegue de  TEST $number",
      "Aidant Testeur",
      s"helper-test-friend$number@example.com",
      helper = true,
      instructor = false,
      admin = false,
      List(area),
      Time.nowParis(),
      "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = List(helperGroup.id),
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
    )
    val unrelatedHelperUser = User(
      UUIDHelper.namedFrom(s"helper-test-unrelated$number"),
      "key",
      "FirstName".some,
      "LastName".some,
      s"Je suis collegue de  TEST $number",
      "Aidant Testeur",
      s"helper-test-unrelated$number@example.com",
      helper = true,
      instructor = false,
      admin = false,
      List(area),
      Time.nowParis(),
      "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = List(helperGroup.id),
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
    )
    val unrelatedExpertUser = User(
      UUIDHelper.namedFrom(s"expert-test-unrelated$number"),
      "key",
      "FirstName".some,
      "LastName".some,
      s"Je suis un expert sans rapport $number",
      "Expert Testeur",
      s"expert-test-unrelated$number@example.com",
      helper = true,
      instructor = false,
      admin = false,
      List(area),
      Time.nowParis(),
      "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = List(helperGroup.id),
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
    )
    val managerUser = User(
      UUIDHelper.namedFrom(s"helper-test-manager$number"),
      "key",
      "FirstName".some,
      "LastName".some,
      s"Je suis manager de TEST $number",
      "Manager Testeur",
      s"helper-test-manager$number@example.com",
      helper = false,
      instructor = false,
      admin = false,
      List(area),
      Time.nowParis(),
      "0",
      groupAdmin = false,
      disabled = false,
      cguAcceptationDate = Some(Time.nowParis()),
      firstLoginDate = None,
      groupIds = List(helperGroup.id),
      observableOrganisationIds = Nil,
      managingOrganisationIds = Nil,
      managingAreaIds = Nil,
      internalSupportComment = None
    )

    val users = List(
      instructorUser,
      unrelatedInstructorUser,
      helperUser,
      helperFriendUser,
      unrelatedHelperUser,
      unrelatedExpertUser,
      managerUser
    )

    val result = userService.add(users)
    result.isRight must beTrue
    users.map(user => userService.validateCGU(user.id))

    val application = Application(
      UUIDHelper.randomUUID,
      creationDate = Time.nowParis(),
      creatorUserName = helperUser.nameWithQualite,
      creatorUserId = helperUser.id,
      creatorGroupId = None,
      creatorGroupName = None,
      subject = s"Sujet de la demande $number",
      description = s"John a un problème $number",
      userInfos = Map("Prénom" -> "John", "Nom de famille" -> "Doe", "Date de naissance" -> "1988"),
      invitedUsers = Map(
        instructorUser.id -> instructorUser.nameWithQualite,
        helperFriendUser.id -> helperFriendUser.nameWithQualite
      ),
      area = area,
      irrelevant = false,
      mandatType = Some(Application.MandatType.Paper),
      mandatDate = Some(java.time.ZonedDateTime.now().toString),
      invitedGroupIdsAtCreation = List(instructorGroup.id)
    )
    applicationService.createApplication(application)

    // Helper login
    val loginToken =
      LoginToken.forUserId(UUIDHelper.namedFrom(s"$userCodeName$number"), 5, "127.0.0.1")
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

    val applicationURL = controllers.routes.ApplicationController
      .show(application.id)
      .absoluteURL(false, s"localhost:$port")
    browser.goTo(applicationURL)

    if (shouldExpectAnError) {
      browser.pageSource must contain(
        "Vous n'avez pas les droits suffisants pour voir cette demande."
      )
      browser.pageSource must not contain application.subject
      browser.pageSource must not contain application.description
      browser.pageSource must not contain application.userInfos("Prénom")
      browser.pageSource must not contain application.userInfos("Nom de famille")
      browser.pageSource must not contain application.userInfos("Date de naissance")
    } else {
      browser.pageSource must contain(application.subject)
      browser.pageSource must contain(application.description)
      browser.pageSource must contain(application.userInfos("Prénom"))
      browser.pageSource must contain(application.userInfos("Nom de famille"))
      browser.pageSource must contain(application.userInfos("Date de naissance"))
    }
  }

}
