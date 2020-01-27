package browser

import extentions.{Time, UUIDHelper}
import javax.inject.Inject
import models.LoginToken
import org.joda.time.DateTime
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import services.TokenService


@RunWith(classOf[JUnitRunner])
class gLoginSpec extends Specification with Tables with BaseSpec {

  "Login" should {
    "Login with valid or invalid emails" in new WithBrowser(webDriver = WebDriverFactory(HTMLUNIT), app = applicationWithBrowser) {
        "email"                              | "result"                                             |
        "julien.dauphant" + "@beta.gouv.fr"  ! "Consultez vos emails"                                |
        "wrong@beta.gouv.fr"                 ! "Aucun compte actif n'est associé à cette adresse e-mail."  |
        "simon.pineau" + "@beta.gouv.fr"     ! "Aucun compte actif n'est associé à cette adresse e-mail."        |>
          { (email, expected) =>
            val loginURL = controllers.routes.LoginController.login().absoluteURL(false, s"localhost:$port")

            browser.goTo(loginURL)
            browser.el("input[name='email']").fill().withText(email)
            browser.el("form").submit()

            browser.pageSource must contain(expected)
        }
    }

    "Use token with success"  in new WithBrowser(webDriver = WebDriverFactory(HTMLUNIT), app = applicationWithBrowser) {
      val tokenService = app.injector.instanceOf[TokenService]
      val loginToken = LoginToken.forUserId(UUIDHelper.namedFrom("julien"), 5, "127.0.0.1")
      tokenService.create(loginToken)

      val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL(false, s"localhost:$port")

      browser.goTo(s"$loginURL?token=${loginToken.token}&path=/")

      eventually {
        browser.url must endWith(controllers.routes.ApplicationController.myApplications().url.substring(1))
      }
    }
    "Use expired token without success"  in new WithBrowser(webDriver = WebDriverFactory(HTMLUNIT), app = applicationWithBrowser) {
      val tokenService = app.injector.instanceOf[TokenService]
      val loginToken = LoginToken.forUserId(UUIDHelper.namedFrom("julien"), 5, "127.0.0.1")
        .copy(expirationDate = DateTime.now(Time.dateTimeZone).minusMinutes(5))
      tokenService.create(loginToken)

      val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL(false, s"localhost:$port")

      browser.goTo(s"$loginURL?token=${loginToken.token}&path=/")

      eventually {
        browser.url must endWith(controllers.routes.LoginController.login().url.substring(1))
        browser.pageSource must contain("Le lien que vous avez utilisez a expiré (il expire après")
      }
    }
    "Use token without success"  in new WithBrowser(webDriver = WebDriverFactory(HTMLUNIT), app = applicationWithBrowser) {
      val loginURL = controllers.routes.LoginController.magicLinkAntiConsumptionPage().absoluteURL(false, s"localhost:$port")

      browser.goTo(s"$loginURL?token=90798798789798&path=/")

      eventually {
        browser.url must endWith(controllers.routes.LoginController.login().url.substring(1))
        browser.pageSource must contain("Le lien que vous avez utilisé n'est plus valide, il a déjà été utilisé.")
      }
    }
  }
}