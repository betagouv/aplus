package views.helpers

import controllers.routes.{Assets, JavascriptController}
import org.webjars.play.WebJarsUtil
import scalatags.Text.all._
import scalatags.Text.tags2
import views.helpers.common.webJarAsset

object head {

  def main(headTitle: String, additionalTags: Modifier = modifier())(implicit
      webJarsUtil: WebJarsUtil
  ) =
    scalatags.Text.all.head(
      meta(charset := "utf-8"),
      meta(httpEquiv := "X-UA-Compatible", content := "IE=edge"),
      meta(
        name := "viewport",
        content := "width=device-width, initial-scale=1.0, minimum-scale=1.0"
      ),
      buildInfoMeta,
      link(
        rel := "icon",
        `type` := "image/png",
        href := Assets.versioned("images/favicon.png").url
      ),
      tags2.title(s"Administration+ - $headTitle"),
      webJarCss("material.min.css"),
      webJarCss("roboto-fontface.css"),
      webJarCss("material-icons.css"),
      webJarCss("fontawesome.css"),
      webJarCss("solid.css"),
      publicCss("stylesheets/main.css"),
      publicCss("stylesheets/mdl-extensions.css"),
      additionalTags,
      publicCss("generated-js/slimselect.min.css"),
      publicCss("generated-js/index.css"),
    )

  def bottomScripts(implicit webJarsUtil: WebJarsUtil): Frag =
    frag(
      publicScript("javascripts/stats.js"),
      tags2.noscript(
        p(img(src := "//stats.data.gouv.fr/piwik.php?idsite=42", style := "border:0;", alt := ""))
      ),
      webJarScript("material.min.js"),
      script(`type` := "text/javascript", src := JavascriptController.javascriptRoutes.url),
      publicScript("generated-js/index.js")
    )

  def buildInfoMeta: Frag =
    frag(gitHeadCommit, gitHeadCommitDate)

  def gitHeadCommit: Frag =
    constants.BuildInfo.gitHeadCommit match {
      case None => ()
      case Some(commit) =>
        meta(attr("property") := "build-git-version", content := commit)
    }

  def gitHeadCommitDate: Frag =
    constants.BuildInfo.gitHeadCommitDate match {
      case None => ()
      case Some(date) =>
        meta(attr("property") := "build-git-date", content := date)
    }

  def publicCss(path: String): Tag =
    link(
      rel := "stylesheet",
      media := "screen,print",
      href := Assets.versioned(path).url
    )

  def publicScript(path: String): Tag =
    script(
      `type` := "application/javascript",
      src := Assets.versioned(path).url
    )

  /** See
    * https://github.com/webjars/webjars-play/blob/v2.8.0-1/src/main/scala/org/webjars/play/WebJarsUtil.scala#L51
    * https://github.com/webjars/webjars-play/blob/v2.8.0-1/src/main/twirl/org/webjars/play/script.scala.html
    */
  def webJarScript(file: String)(implicit webJarsUtil: WebJarsUtil): Frag =
    webJarAsset(file, url => script(src := url))

  def webJarCss(file: String)(implicit webJarsUtil: WebJarsUtil): Frag =
    webJarAsset(file, url => link(rel := "stylesheet", `type` := "text/css", href := url))

}
