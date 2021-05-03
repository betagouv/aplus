package views.helpers

import cats.syntax.all._
import constants.BuildInfo
import controllers.routes.{Assets, JavascriptController}
import org.webjars.play.WebJarsUtil
import play.api.Logger
import scala.util.{Failure, Success}
import scalatags.Text.all._
import scalatags.Text.tags2

object head {

  private val logger = Logger("views")

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
      webJarCss("slimselect.min.css"),
      additionalTags,
      webJarScript("material.min.js"),
      publicScript("javascripts/polyfills.js"),
      publicScript("javascripts/main.js"),
      publicCss("generated-js/index.css"),
      script(`type` := "text/javascript", src := JavascriptController.javascriptRoutes.url)
    )

  def bottomScripts(implicit webJarsUtil: WebJarsUtil): Frag =
    frag(
      script(
        raw("""
                var _paq = _paq || [];
                _paq.push(["setDomains", ["*.aplus.beta.gouv.fr"]]);
                _paq.push(['trackPageView']);
                (function() {
                    var u="//stats.data.gouv.fr/";
                    _paq.push(['setTrackerUrl', u+'piwik.php']);
                    _paq.push(['setSiteId', '42']);
                    var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0];
                    g.type='text/javascript'; g.async=true; g.defer=true; g.src=u+'piwik.js'; s.parentNode.insertBefore(g,s);
                })();
       """)
      ),
      webJarScript("slimselect.min.js"),
      publicScript("javascripts/main-page-bottom.js"),
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
    * https://github.com/webjars/webjars-play/blob/v2.8.0-1/src/main/scala/org/webjars/play/WebJarsUtil.scala#L35
    */
  private def webJarAsset(file: String, urlToTag: String => Tag)(implicit
      webJarsUtil: WebJarsUtil
  ): Frag = {
    val asset = webJarsUtil.locate(file)
    asset.url match {
      case Success(url) => urlToTag(url)
      case Failure(err) =>
        val errMsg = s"couldn't find asset ${asset.path}"
        logger.error(errMsg, err)
        frag()
    }
  }

  /** See
    * https://github.com/webjars/webjars-play/blob/v2.8.0-1/src/main/scala/org/webjars/play/WebJarsUtil.scala#L51
    * https://github.com/webjars/webjars-play/blob/v2.8.0-1/src/main/twirl/org/webjars/play/script.scala.html
    */
  def webJarScript(file: String)(implicit webJarsUtil: WebJarsUtil): Frag =
    webJarAsset(file, url => script(src := url))

  def webJarCss(file: String)(implicit webJarsUtil: WebJarsUtil): Frag =
    webJarAsset(file, url => link(rel := "stylesheet", `type` := "text/css", href := url))

}
