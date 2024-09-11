package browser

import cats.syntax.all._
import helper.{Hash, Time, UUIDHelper}
import java.time.ZonedDateTime
import models.{Area, LoginToken, User}
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.specification.BeforeAfterAll
import play.api.test._
import scala.annotation.nowarn
import services.{TokenService, UserService}

@RunWith(classOf[JUnitRunner])
class LoginSpec extends Specification with Tables with BaseSpec with BeforeAfterAll {

  val existingUser: User = User(
    UUIDHelper.namedFrom("julien.test"),
    Hash.sha256(s"julien.test"),
    "FirstName".some,
    "LastName".some,
    "Julien DAUPHANT TEST",
    "Admin A+",
    "julien.dauphant.test@beta.gouv.fr",
    helper = true,
    instructor = false,
    admin = true,
    Area.all.map(_.id),
    ZonedDateTime.parse("2017-11-01T00:00+01:00"),
    "75056",
    firstLoginDate = None,
    groupAdmin = true,
    disabled = false,
    observableOrganisationIds = Nil,
    managingOrganisationIds = Nil,
    managingAreaIds = Nil,
    internalSupportComment = None
  )

  def beforeAll(): Unit = {
    val userService = applicationWithBrowser.injector.instanceOf[UserService]
    val _ = userService.add(List(existingUser))
    val _ = userService.validateCGU(existingUser.id)
  }

  def afterAll(): Unit = {
    val userService = applicationWithBrowser.injector.instanceOf[UserService]
    val _ = userService.deleteById(existingUser.id)
  }

  "Login" should {

    "Login with valid or invalid emails" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() =
        "email" | "result" |
          "julien.dauphant.test" + "@beta.gouv.fr" ! "Consultez vos e-mails" |
          "wrong@beta.gouv.fr" ! "Aucun compte actif n’est associé à cette adresse e-mail." |
          "simon.pineau" + "@beta.gouv.fr" ! "Aucun compte actif n’est associé à cette adresse e-mail." |> {
            (email, expected) =>
              val loginURL =
                controllers.routes.LoginController.login.absoluteURL(false, s"localhost:$port")
              browser.goTo(loginURL)
              browser.el("input[name='email']").fill().withText(email)
              browser.el("form").submit()

              eventually {
                browser.pageSource must contain(expected)
              }
          }
    }

    "Use token with success" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() = {
        val tokenService = app.injector.instanceOf[TokenService]
        val loginToken = LoginToken.forUserId(existingUser.id, 5, "127.0.0.1")

        tokenService.create(loginToken)

        val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage
          .absoluteURL(false, s"localhost:$port")

        browser.goTo(s"$loginURL?token=${loginToken.token}&path=/")

        eventually {
          browser.url must endWith(
            controllers.routes.ApplicationController.myApplications.url.substring(1)
          )
        }
      }
    }

    "Use expired token without success" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() = {
        val tokenService = app.injector.instanceOf[TokenService]

        val loginToken = LoginToken
          .forUserId(existingUser.id, 5, "127.0.0.1")
          .copy(expirationDate = Time.nowParis().minusMinutes(5))

        tokenService.create(loginToken)

        val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage
          .absoluteURL(false, s"localhost:$port")

        browser.goTo(s"$loginURL?token=${loginToken.token}&path=/")

        eventually {
          browser.url must endWith(controllers.routes.HomeController.index.url.substring(1))
          browser.pageSource must contain("Votre lien de connexion a expiré, il est valable")
        }
      }
    }

    "Use token without success" in new WithBrowser(
      webDriver = webDriver,
      app = applicationWithBrowser
    ) {
      @nowarn("msg=discarded non-Unit value")
      override def running() = {
        val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage
          .absoluteURL(false, s"localhost:$port")

        browser.goTo(s"$loginURL?token=90798798789798&path=/")

        eventually {
          browser.url must endWith(controllers.routes.LoginController.login.url.substring(1))
          browser.pageSource must contain(
            "Le lien que vous avez utilisé n'est plus valide, il a déjà été utilisé."
          )
        }
      }
    }

  }
}
