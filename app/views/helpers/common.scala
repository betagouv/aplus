package views.helpers

import cats.syntax.all._
import controllers.routes.{ApplicationController, Assets, HomeController}
import org.webjars.play.WebJarsUtil
import play.api.Logger
import play.api.mvc.Flash
import scala.util.{Failure, Success}
import scalatags.Text.all._
import scalatags.Text.tags2
import views.MainInfos

object common {

  private val logger = Logger("views")

  def contactLink(text: String): Frag =
    a(href := HomeController.contact.url)(text)

  /** See
    * https://github.com/webjars/webjars-play/blob/v2.8.0-1/src/main/scala/org/webjars/play/WebJarsUtil.scala#L35
    */
  def webJarAsset(file: String, urlToTag: String => Tag)(implicit
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

  def webJarImg(file: String)(frags: Modifier*)(implicit webJarsUtil: WebJarsUtil): Frag =
    webJarAsset(file, url => img(src := url, frags))

  def optionalDemoVersionHeader(implicit mainInfos: MainInfos): Option[Frag] =
    mainInfos.isDemo.some
      .filter(identity)
      .map(_ =>
        div(
          cls := "mdl-layout__header-row mdl-color--pink-500 mdl-color-text--white",
          span(
            cls := "mdl-layout-title aplus-demo-header__title",
            "Version de démonstration d’Administration+"
          )
        )
      )

  private def displayHeader(headerLines: Option[Frag]*): Frag =
    headerLines.flatten match {
      case Nil => frag()
      case lines =>
        div(
          cls := "mdl-layout__header mdl-color-text--grey-500 do-not-print mdl-color--grey-100",
          frag(lines)
        )
    }

  def topHeaderMainPage(implicit mainInfos: MainInfos): Frag =
    displayHeader(
      optionalDemoVersionHeader,
      mainInfos.config.topHeaderPublicPagesAlertMessageHtml.map(htmlMessage =>
        div(
          cls := "mdl-layout__header-row mdl-color--red-A700 mdl-color-text--white single--height-auto single--padding-top-8px single--padding-bottom-8px",
          raw(htmlMessage)
        )
      )
    )

  def topHeaderConnected(implicit mainInfos: MainInfos): Frag =
    displayHeader(
      optionalDemoVersionHeader,
      mainInfos.config.topHeaderWarningMessage.map(message =>
        div(
          cls := "mdl-layout__header-row mdl-color--deep-purple-400 mdl-color-text--white",
          message
        )
      )
    )

  def loggedInPage(title: String, userEmail: String, inner: Frag)(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash
  ): Tag =
    html(
      lang := "fr",
      views.helpers.head.main(title),
      body(
        div(
          cls := "demo-layout mdl-layout mdl-js-layout mdl-layout--fixed-header mdl-layout--fixed-drawer",
          leftAndTopPanel(userEmail),
          tags2.main(
            cls := "mdl-layout__content mdl-color--grey-200",
            div(
              cls := "mdl-grid main__grid single--align-content-flex-start single--max-width-960px",
              views.helpers.forms.flashError,
              views.helpers.forms.flashErrorRawHtml,
              views.helpers.forms.flashSuccess,
              views.helpers.forms.flashSuccessRawHtml,
              inner
            ),
            footer(
              cls := "mdl-mega-footer mdl-mega-footer-fix do-not-print",
              div(
                cls := "mdl-mega-footer--bottom-section",
                div(
                  cls := "mdl-logo",
                  s"Administration+ ${java.time.LocalDate.now().getYear()} :"
                ),
                ul(
                  cls := "mdl-mega-footer--link-list",
                  li(
                    a(
                      rel := "noopener noreferrer",
                      href := "https://docs.aplus.beta.gouv.fr",
                      "Aide"
                    )
                  ),
                  li(
                    a(href := HomeController.cgu.url, "CGU")
                  ),
                  li(
                    a(
                      href := ApplicationController.showExportMyApplicationsCSV.url,
                      "Exporter mes demandes en CSV"
                    )
                  ),
                  li(
                    a(
                      href := HomeController.mentionsLegales.url,
                      "Mentions légales"
                    )
                  ),
                  li(
                    a(
                      href := HomeController.privacy.url,
                      "Politique de confidentialité"
                    )
                  ),
                  li(
                    a(
                      href := HomeController.declarationAccessibilite.url,
                      "Accessibilité : non conforme"
                    )
                  )
                )
              )
            )
          )
        ),
        views.helpers.head.bottomScripts
      )
    )

  private def leftAndTopPanel(userEmail: String): Frag =
    frag(
      div(
        cls := "mdl-layout__header mdl-color--grey-100 mdl-color-text--grey-600 do-not-print",
        div(
          cls := "mdl-layout__header-row",
          div(cls := "mdl-layout-spacer"),
          div(
            cls := "hidden--small-screen",
            ul(
              cls := "mdl-list",
              li(
                cls := "mdl-list__item mdl-list__item--two-line",
                span(
                  cls := "mdl-list__item-primary-content single--line-height-24px",
                  span(userEmail)
                ),
                span(
                  cls := "mdl-list__item-secondary-content single--align-items-center-important single--padding-left-40px",
                  a(
                    cls := "mdl-list__item-secondary-action mdl-button mdl-js-button mdl-button--icon mdl-js-ripple-effect",
                    href := controllers.routes.LoginController.disconnect.url,
                    title := "Déconnexion",
                    i(cls := "fas fa-sign-out-alt")
                  ),
                  span(
                    cls := "mdl-list__item-secondary-info",
                    a(
                      cls := "mdl-typography--font-regular mdl-color-text--grey-900 mdl-typography--text-nowrap",
                      href := controllers.routes.LoginController.disconnect.url,
                      title := "Déconnexion",
                      "Se déconnecter"
                    )
                  )
                )
              )
            )
          )
        )
      ),
      div(
        cls := "mdl-layout__drawer mdl-color--white do-not-print mdl-color-text--white",
        header(
          cls := "drawer-header",
          p(
            style := "line-height: 1.2; text-align: center;",
            a(
              href := HomeController.welcome.url,
              img(
                src := Assets.versioned("images/logo_full_500x397.png").url,
                alt := "Logo Administration+",
                cls := "logo"
              )
            )
          )
        ),
        div(cls := "mdl-layout-spacer mdl-color--deep-purple-900"),
        footer(
          cls := "drawer-footer mdl-color--deep-purple-900",
          p(
            i(
              cls := "material-icons mdl-color-text--cyan-600 single--font-size-50px",
              "contact_mail"
            ),
            br,
            span(
              cls := "mdl-typography--subhead",
              "Besoin d’aide ?"
            ),
            br,
            br,
            a(
              href := HomeController.contact.url,
              cls := "mdl-color-text--cyan-600",
              "Contactez l’équipe A+."
            ),
            br
          )
        )
      )
    )

}
