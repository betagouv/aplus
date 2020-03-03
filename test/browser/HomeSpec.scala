package browser

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._

@RunWith(classOf[JUnitRunner])
class HomeSpec extends Specification with Tables with BaseSpec {

  "Home" should {
    "Stay on / when disconnected" in new WithBrowser(
      webDriver = WebDriverFactory(HTMLUNIT),
      app = applicationWithBrowser
    ) {
      val homeUrl = controllers.routes.HomeController.index().absoluteURL(false, s"localhost:$port")

      browser.goTo(homeUrl)

      browser.url must endWith(controllers.routes.HomeController.index().url.substring(1))
    }

    "Status up" in new WithBrowser(
      webDriver = WebDriverFactory(HTMLUNIT),
      app = applicationWithBrowser
    ) {
      val loginURL =
        controllers.routes.HomeController.status().absoluteURL(false, s"localhost:$port")

      browser.goTo(loginURL)

      browser.pageSource must contain("OK")
    }
  }
}
