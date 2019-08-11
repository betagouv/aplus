package browser

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._
import play.api.inject.guice.GuiceApplicationBuilder


@RunWith(classOf[JUnitRunner])
class LoginSpec extends Specification with Tables {

  def applicationWithBrowser = {
    new GuiceApplicationBuilder()
      .configure("app.filesPath" -> "files", "app.host" -> "localhost", "play.mailer.mock" -> true)
      .build()
  }

  "Login" should {
    "Login with valid or invalid emails" in new WithBrowser(webDriver = WebDriverFactory(HTMLUNIT), app = applicationWithBrowser) {
        "email"  | "result" |
        "julien.dauphant" + "@beta.gouv.fr"  ! "Regardez vos emails"|
        "wrong@beta.gouv.fr" ! "Il n'y a pas d'utilisateur avec cette adresse email"|>
          { (email, expected) =>
            val loginURL = controllers.routes.LoginController.login().absoluteURL(false, s"localhost:$port")

            browser.goTo(loginURL)
            browser.el("input[name='email']").fill().withText(email)
            browser.el("form").submit()

            browser.pageSource must contain(expected)
        }
    }
  }
}