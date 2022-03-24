package browser

import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import play.api.inject.guice.GuiceApplicationBuilder

trait BaseSpec {

  def applicationWithBrowser =
    new GuiceApplicationBuilder()
      .configure("app.filesPath" -> "files", "app.host" -> "localhost", "play.mailer.mock" -> true)
      .build()

  def webDriver: WebDriver = {
    val options = new FirefoxOptions()
      .setHeadless(true)
    new FirefoxDriver(options)
  }

}
