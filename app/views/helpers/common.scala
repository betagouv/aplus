package views.helpers

import cats.syntax.all._
import constants.Constants
import controllers.routes.{ApplicationController, Assets, HomeController}
import org.webjars.play.WebJarsUtil
import play.api.mvc.Flash
import scalatags.Text.all._
import scalatags.Text.tags2

object common {

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
                  li(a(href := HomeController.help().url, "Aide")),
                  li(
                    a(
                      href := "https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation",
                      target := "_blank",
                      rel := "noopener noreferrer",
                      "CGU"
                    )
                  ),
                  li(
                    a(
                      href := ApplicationController.showExportMyApplicationsCSV().url,
                      "Exporter mes demandes en CSV"
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
                    href := controllers.routes.LoginController.disconnect().url,
                    title := "Déconnexion",
                    i(cls := "fas fa-sign-out-alt")
                  ),
                  span(
                    cls := "mdl-list__item-secondary-info",
                    a(
                      cls := "mdl-typography--font-regular mdl-color-text--grey-900 mdl-typography--text-nowrap",
                      href := controllers.routes.LoginController.disconnect().url,
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
              href := HomeController.welcome().url,
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
          img(
            src := Assets.versioned("images/contact_phone_104x104.png").url,
            cls := "contact-phone-icon"
          ),
          p(
            span(
              cls := "mdl-typography--subhead",
              "Besoin d’aide ?"
            ),
            br,
            "Contactez-nous :",
            br,
            br,
            a(
              href := s"mailto:${Constants.supportEmail}",
              cls := "mdl-color-text--cyan-600",
              Constants.supportEmail
            ),
            br
          )
        )
      )
    )

}
