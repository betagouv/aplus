package browser

import helper.{Time, UUIDHelper}
import models.{Application, Area, LoginToken, User, UserGroup}
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._
import services._

@RunWith(classOf[JUnitRunner])
class ApplicationAccessSpec extends Specification with Tables with BaseSpec {

  "Application" should {
    "Access Application (Part 1)" in new WithBrowser(
      webDriver = WebDriverFactory(HTMLUNIT),
      app = applicationWithBrowser
    ) {

      "userCodeName" | "expectedError" |
        "instructor-test" ! false |
        "instructor-test-unrelated" ! true |
        "helper-test" ! false |
        "helper-test-friend" ! false |> { (userCodeName: String, shouldExpectAnError: Boolean) =>
        applicationAccessTest(app, browser, port, userCodeName, shouldExpectAnError)
      }
    }
  }

  "Application" should {
    "Access Application (Part 2)" in new WithBrowser(
      webDriver = WebDriverFactory(HTMLUNIT),
      app = applicationWithBrowser
    ) {

      "userCodeName" | "expectedError" |
        "helper-test-unrelated" ! true |
        "expert-test-unrelated" ! true |
        "helper-test-manager" ! true |> { (userCodeName: String, shouldExpectAnError: Boolean) =>
        applicationAccessTest(app, browser, port, userCodeName, shouldExpectAnError)
      }
    }
  }

  def applicationAccessTest(
      app: play.api.Application,
      browser: play.api.test.TestBrowser,
      port: Int,
      userCodeName: String,
      shouldExpectAnError: Boolean
  ): org.specs2.matcher.MatchResult[String] = {
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
      areaIds = area :: Nil
    )
    groupService.add(instructorGroup)
    val helperGroup = UserGroup(
      id = UUIDHelper.randomUUID,
      name = s"Helper Group $number",
      description = None,
      inseeCode = List("0"),
      creationDate = Time.nowParis(),
      areaIds = area :: Nil
    )
    groupService.add(helperGroup)

    val instructorUser = User(
      UUIDHelper.namedFrom(s"instructor-test$number"),
      "key",
      s"J'instruit TEST $number",
      s"Instructeur Testeur $number",
      s"instructor-test$number@example.com",
      true,
      true,
      false,
      List(area),
      Time.nowParis(),
      "0",
      false,
      false,
      cguAcceptationDate = Some(Time.nowParis()),
      groupIds = List(instructorGroup.id)
    )
    val unrelatedInstructorUser = User(
      UUIDHelper.namedFrom(s"instructor-test-unrelated$number"),
      "key",
      s"Je n'instruit pas TEST $number",
      s"Instructeur Testeur $number",
      s"instructor-test-unrelated$number@example.com",
      true,
      true,
      false,
      List(area),
      Time.nowParis(),
      "0",
      false,
      false,
      cguAcceptationDate = Some(Time.nowParis()),
      groupIds = Nil
    )
    val helperUser = User(
      UUIDHelper.namedFrom(s"helper-test$number"),
      "key",
      s"J'aide TEST $number",
      "Aidant Testeur",
      s"helper-test$number@example.com",
      true,
      false,
      false,
      List(area),
      Time.nowParis(),
      "0",
      false,
      false,
      cguAcceptationDate = Some(Time.nowParis()),
      groupIds = List(helperGroup.id)
    )
    val helperFriendUser = User(
      UUIDHelper.namedFrom(s"helper-test-friend$number"),
      "key",
      s"Je suis collegue de  TEST $number",
      "Aidant Testeur",
      s"helper-test-friend$number@example.com",
      true,
      false,
      false,
      List(area),
      Time.nowParis(),
      "0",
      false,
      false,
      cguAcceptationDate = Some(Time.nowParis()),
      groupIds = List(helperGroup.id)
    )
    val unrelatedHelperUser = User(
      UUIDHelper.namedFrom(s"helper-test-unrelated$number"),
      "key",
      s"Je suis collegue de  TEST $number",
      "Aidant Testeur",
      s"helper-test-unrelated$number@example.com",
      true,
      false,
      false,
      List(area),
      Time.nowParis(),
      "0",
      false,
      false,
      cguAcceptationDate = Some(Time.nowParis()),
      groupIds = List(helperGroup.id)
    )
    val unrelatedExpertUser = User(
      UUIDHelper.namedFrom(s"expert-test-unrelated$number"),
      "key",
      s"Je suis un expert sans rapport $number",
      "Expert Testeur",
      s"expert-test-unrelated$number@example.com",
      true,
      false,
      false,
      List(area),
      Time.nowParis(),
      "0",
      false,
      false,
      cguAcceptationDate = Some(Time.nowParis()),
      groupIds = List(helperGroup.id)
    )
    val managerUser = User(
      UUIDHelper.namedFrom(s"helper-test-manager$number"),
      "key",
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
      groupIds = List(helperGroup.id)
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
    users.forall(user => userService.acceptCGU(user.id, false))

    val application = Application(
      UUIDHelper.randomUUID,
      creationDate = Time.nowParis(),
      creatorUserName = helperUser.nameWithQualite,
      creatorUserId = helperUser.id,
      subject = s"Sujet de la demande $number",
      description = s"John a un problème $number",
      userInfos = Map("Prénom" -> "John", "Nom de famille" -> "Doe", "Date de naissance" -> "1988"),
      invitedUsers = Map(
        instructorUser.id -> instructorUser.nameWithQualite,
        helperFriendUser.id -> helperFriendUser.nameWithQualite
      ),
      area = area,
      irrelevant = false
    )
    applicationService.createApplication(application)

    // Helper login
    val loginToken =
      LoginToken.forUserId(UUIDHelper.namedFrom(s"$userCodeName$number"), 5, "127.0.0.1")
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

    val applicationURL = controllers.routes.ApplicationController
      .show(application.id)
      .absoluteURL(false, s"localhost:$port")
    browser.goTo(applicationURL)

    if (shouldExpectAnError) {
      browser.pageSource must contain(
        "Vous n'avez pas les droits suffisants pour voir cette demande."
      )
      browser.pageSource must not contain (application.subject)
      browser.pageSource must not contain (application.description)
      browser.pageSource must not contain (application.userInfos("Prénom"))
      browser.pageSource must not contain (application.userInfos("Nom de famille"))
      browser.pageSource must not contain (application.userInfos("Date de naissance"))
    } else {
      browser.pageSource must contain(application.subject)
      browser.pageSource must contain(application.description)
      browser.pageSource must contain(application.userInfos("Prénom"))
      browser.pageSource must contain(application.userInfos("Nom de famille"))
      browser.pageSource must contain(application.userInfos("Date de naissance"))
    }
  }

}
