package browser

import play.api.inject.guice.GuiceApplicationBuilder

trait BaseSpec {

  def applicationWithBrowser =
    new GuiceApplicationBuilder()
      .configure("app.filesPath" -> "files", "app.host" -> "localhost", "play.mailer.mock" -> true)
      .build()

}
