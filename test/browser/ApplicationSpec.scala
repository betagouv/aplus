package browser

import cats.syntax.all._
import helper.{Time, UUIDHelper}
import models.{Area, LoginToken, User, UserGroup}
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test._
import services.{ApplicationService, TokenService, UserGroupService, UserService}

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification with BaseSpec {

  "Application" should {
    "Create Application with success" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      val tokenService = app.injector.instanceOf[TokenService]
      val userService = app.injector.instanceOf[UserService]
      val groupService = app.injector.instanceOf[UserGroupService]
      val applicationService = app.injector.instanceOf[ApplicationService]

      val number = scala.util.Random.nextInt()
      val area = Area.all.head.id

      val instructorGroup = UserGroup(
        id = UUIDHelper.randomUUID,
        name = s"Group $number",
        description = None,
        inseeCode = List("0"),
        creationDate = Time.nowParis(),
        areaIds = area :: Nil,
        publicNote = None,
        internalSupportComment = None
      )

      groupService.add(instructorGroup)

      val instructorUser = User(
        UUIDHelper.randomUUID,
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
        internalSupportComment = None,
        passwordActivated = false,
      )

      val helperUser = User(
        UUIDHelper.randomUUID,
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
        observableOrganisationIds = Nil,
        managingOrganisationIds = Nil,
        managingAreaIds = Nil,
        internalSupportComment = None,
        passwordActivated = false,
      )

      userService.add(List(instructorUser, helperUser))
      userService.validateCGU(helperUser.id)

      // Helper login
      val loginToken = LoginToken.forUserId(helperUser.id, 5, "127.0.0.1")
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

      // Submit an application
      val createApplicationURL =
        controllers.routes.ApplicationController.create.absoluteURL(false, s"localhost:$port")

      browser.goTo(createApplicationURL)

      val subject = s"Sujet de la demande $number"
      val firstName = "John"
      val lastName = "Doe"
      val description = s"John a un problème $number"
      val birthDate = "1988"

      // Note: mdl reduce checkboxes to size 0
      // The driver will not be able to scroll to the element selected by,
      // for example, input[value='validate'] and will fail with
      // org.openqa.selenium.ElementNotInteractableException: Element <input id="checkbox-mandat" class="mdl-checkbox__input" name="validate" type="checkbox"> could not be scrolled into view
      browser.waitUntil(browser.el(s"label[for*='${instructorGroup.id}']").clickable())

      browser.el(s"label[for*='${instructorGroup.id}']").click()
      browser.el(s"input[value='${instructorGroup.id}']").selected() mustEqual true

      browser.el("input[name='subject']").fill().withText(subject)
      browser.el("input[name='usagerPrenom']").fill().withText(firstName)
      browser.el("input[name='usagerNom']").fill().withText(lastName)
      browser.el("input[name='usagerBirthDate']").fill().withText(birthDate)
      browser.el("textarea[name='description']").fill().withText(description)
      // Checkbox
      browser.el("label[for='checkbox-mandat']").click()

      browser.waitUntil(browser.el("input[name='validate']").selected)

      // Radio
      browser.el("label[for='mandat-option-already']").click()
      browser.el("input[name='mandatDate']").fill().withText(java.time.ZonedDateTime.now().toString)

      browser.el("form").submit()

      // Wait for form submit
      eventually {
        browser.url must endWith(
          controllers.routes.ApplicationController.myApplications.url.substring(1)
        )
      }

      // Check if the application exist in database
      val applicationOption = applicationService
        .allByArea(area, anonymous = false)
        .find(_.subject === subject)

      applicationOption mustNotEqual None
      val application = applicationOption.get
      application.subject mustEqual subject
      application.userInfos("Prénom") mustEqual firstName
      application.userInfos("Nom de famille") mustEqual lastName
      application.userInfos("Date de naissance") mustEqual birthDate
      application.description mustEqual description
      application.creatorUserId mustEqual helperUser.id
      application.creatorUserName mustEqual helperUser.nameWithQualite
      application.invitedUsers mustEqual Map(
        instructorUser.id -> instructorUser.nameWithQualite
      )
    }
  }
}
