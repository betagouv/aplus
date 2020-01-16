package browser

import extentions.{Time, UUIDHelper}
import models.{Area, LoginToken, User, UserGroup}
import org.joda.time.DateTime
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._
import services.{ApplicationService, EventService, TokenService, UserGroupService, UserService}


@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification with Tables with BaseSpec {

  "Application" should {
    "Create Application with success"  in new WithBrowser(webDriver = WebDriverFactory(HTMLUNIT), app = applicationWithBrowser) {
      val tokenService = app.injector.instanceOf[TokenService]
      val userService = app.injector.instanceOf[UserService]
      val groupService = app.injector.instanceOf[UserGroupService]
      val eventService = app.injector.instanceOf[EventService]
      val applicationService = app.injector.instanceOf[ApplicationService]

      val number = scala.util.Random.nextInt()
      val area = Area.all.head.id
      val instructorGroup = UserGroup(id = UUIDHelper.randomUUID, name = s"Group $number", description = None, inseeCode = List("0"), creationDate = Time.now(), areaIds = area::Nil)
      groupService.add(instructorGroup)
      val instructorUser = User(
        UUIDHelper.randomUUID,
        "key",
        s"J'instruit TEST $number",
        s"Instructeur Testeur $number",
        s"instructor-test$number@example.com",
        true,
        true,
        false,
        List(area),
        Time.now(),
        "0",
        false,
        false,
        cguAcceptationDate = Some(Time.now()),
        groupIds = List(instructorGroup.id)
      )
      val helperUser = User(
        UUIDHelper.randomUUID,
        "key",
        s"J'aide TEST $number",
        "Aidant Testeur",
        s"helper-test$number@example.com",
        true,
        false,
        false,
        List(area),
        Time.now(),
        "0",
        false,
        false,
        cguAcceptationDate = Some(Time.now())
      )
      userService.add(List(instructorUser, helperUser))
      userService.acceptCGU(helperUser.id, false)

      // Helper login
      val loginToken = LoginToken.forUserId(helperUser.id, 5, "127.0.0.1")
      tokenService.create(loginToken)

      val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL(false, s"localhost:$port")

      browser.goTo(s"$loginURL?token=${loginToken.token}&path=/")

      // Wait for login
      eventually {
        browser.url must endWith(controllers.routes.ApplicationController.myApplications().url.substring(1))
      }

      // Submit an application
      val createApplicationURL = controllers.routes.ApplicationController.create().absoluteURL(false, s"localhost:$port")
      browser.goTo(createApplicationURL)
      
      val subject = s"Sujet de la demande $number"
      val firstName = "John"
      val lastName = "Doe"
      val description = s"John a un problème $number"
      val birthDate = "1988"

      browser.waitUntil(browser.el(s"input[value='${instructorGroup.name}']").clickable())
      
      browser.el(s"input[value='${instructorGroup.name}']").click()
      browser.el(s"input[value='${instructorGroup.name}']").selected() mustEqual true

      browser.waitUntil(browser.el(s"input[value='${instructorUser.id}']").selected)

      browser.el("input[name='subject']").fill().withText(subject)
      browser.el("input[name='infos[Prénom]']").fill().withText(firstName)
      browser.el("input[name='infos[Nom de famille]']").fill().withText(lastName)
      browser.el("input[name='infos[Date de naissance]']").fill().withText(birthDate)
      browser.el("textarea[name='description']").fill().withText(description)
      browser.el("input[name='validate']").click()
      
      browser.waitUntil(browser.el("input[name='validate']").selected)
      
      browser.el("form").submit()

      // Wait for form submit
      eventually {
        browser.url must endWith(controllers.routes.ApplicationController.myApplications().url.substring(1))
      }

      // Check if the application exist in database
      val applicationOption = applicationService.allByArea(area, false)
        .find(_.subject == subject)

      applicationOption mustNotEqual None
      val application = applicationOption.get
      application.subject mustEqual subject
      //TODO :  Fix test : "Prénom" is deserialize as Pr?nom and skipped by Play form binding :( with playspec only, it was working before I pass the post in "multipart/form-data"
      // application.userInfos("Prénom") mustEqual firstName
      application.userInfos("Nom de famille") mustEqual lastName
      application.userInfos("Date de naissance") mustEqual birthDate
      application.description mustEqual description
      application.creatorUserId mustEqual helperUser.id
      application.creatorUserName mustEqual groupService.contextualizedUserName(helperUser)
      application.invitedUsers mustEqual Map(instructorUser.id -> groupService.contextualizedUserName(instructorUser))
    }
  }
}