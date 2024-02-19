package views

import cats.syntax.all._
import controllers.routes.{Assets, HomeController, UserController}
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import scalatags.Text.tags2
import views.helpers.head.publicCss

object main {

  def generateLink(route: String, text: String)(implicit
      request: RequestHeader,
  ) =
    li(cls := "fr-nav__item")(
      a(
        href := route,
        title := text,
        cls := "fr-nav__link",
        target := "_self",
        attr("aria-current") := (if (request.path === route) "true" else "false")
      )(
        text
      )
    )

  def layout(
      pageName: String,
      content: Frag,
      additionalHeadTags: Frag = frag(),
      additionalFooterTags: Frag = frag()
  )(implicit
      request: RequestHeader,
  ) = dsfrLayout(
    pageName,
    breadcrumbs(pageName),
    content,
    loggedInNavBar(),
    additionalHeadTags,
    additionalFooterTags
  )

  def publicLayout(
      pageName: String,
      content: Frag,
      addBreadcrumbs: Boolean = true,
      additionalHeadTags: Frag = frag(),
      additionalFooterTags: Frag = frag()
  ) = dsfrLayout(
    pageName,
    if (addBreadcrumbs) breadcrumbs(pageName) else frag(),
    content,
    frag(),
    additionalHeadTags,
    additionalFooterTags
  )

  private def dsfrLayout(
      pageTitle: String,
      navigation: Frag,
      content: Frag,
      navBar: Frag,
      additionalHeadTags: Frag,
      additionalFooterTags: Frag
  ) =
    html(
      head(lang := "fr", attr("data-fr-scheme") := "system")(
        meta(charset := "utf-8"),
        meta(name := "format-detection", attr("content") := "telephone=no"),
        meta(
          name := "viewport",
          attr("content") := "width=device-width, initial-scale=1, shrink-to-fit=no"
        ),
        link(
          rel := "stylesheet",
          media := "screen,print",
          href := Assets.versioned("generated-js/index.css").url
        ),
        meta(name := "theme-color", attr("content") := "#000091"),
        link(
          attr("rel") := "icon",
          href := Assets.versioned("images/favicon.png").url,
          attr("type") := "image/svg+xml"
        ),
        publicCss("generated-js/dsfr/dsfr.min.css"),
        tags2.title(pageTitle),
        additionalHeadTags
      ),
      body(
        header(role := "banner", cls := "fr-header")(
          div(cls := "fr-header__body")(
            div(cls := "fr-container")(
              div(cls := "fr-header__body-row")(
                div(cls := "fr-header__brand fr-enlarge-link")(
                  div(cls := "fr-header__brand-top")(
                    div(cls := "fr-header__logo")(
                      div(cls := "fr-logo")(
                        "Agence",
                        br,
                        "Nationale",
                        br,
                        "de la Cohésion",
                        br,
                        "des Territoires"
                      ),
                      div(cls := "fr-header__operator")(
                        img(
                          cls := "fr-responsive-img",
                          src := Assets.versioned("images/logo_small_150x160.png").url,
                          alt := "logo administration +"
                        )
                      ),
                    )
                  )
                ),
                div(cls := "fr-header__service")(
                  a(
                    href := "/",
                    title := "Accueil - Administration +"
                  )(
                    p(cls := "fr-header__service-title")(
                      "Administration +"
                    ),
                    p(cls := "fr-header__service-tagline")(
                      "Ensemble pour débloquer, rapidement et efficacement"
                    )
                  )
                )
              )
            ),
          ),
          navBar
        )
      ),
      div(cls := "main-container")(
        tag("main")(role := "main")(
          navigation,
          content
        )
      ),
      footer(cls := "fr-footer", role := "contentinfo", id := "footer")(
        div(cls := "fr-container")(
          div(cls := "fr-footer__bottom")(
            ul(cls := "fr-footer__bottom-list")(
              li(cls := "fr-footer__bottom-item")(
                a(cls := "fr-footer__bottom-link")(
                  href := HomeController.index.url,
                  "Administration +"
                )
              ),
              li(cls := "fr-footer__bottom-item")(
                a(
                  href := HomeController.help.url,
                  cls := "fr-footer__bottom-link",
                )(
                  "Aide"
                )
              ),
              li(cls := "fr-footer__bottom-item")(
                a(
                  href := UserController.showValidateAccount.url,
                  cls := "fr-footer__bottom-link",
                )(
                  "CGU"
                )
              ),
            ),
            div(cls := "fr-footer__bottom-copy")(
              p(cls := "fr-footer__bottom-copy-text")(
                "Sauf mention explicite de propriété intellectuelle détenue par des tiers, les contenus de ce site sont proposés sous ",
                a(
                  href := "https://github.com/etalab/licence-ouverte/blob/master/LO.md",
                  target := "_blank",
                  rel := "noopener external",
                  title := "licence etalab - nouvelle fenêtre"
                )(
                  "licence etalab-2.0"
                )
              )
            )
          )
        ),
        script(
          `type` := "module",
          src := Assets.versioned("generated-js/dsfr/dsfr.module.min.js").url
        ),
        additionalFooterTags
      )
    )

  private def breadcrumbs(pageName: String): Tag =
    tag("nav")(cls := "fr-breadcrumb")(
      div(cls := "fr-collapse")(
        ol(cls := "fr-breadcrumb__list")(
          li()(
            a(
              href := "/",
              cls := "fr-breadcrumb__link"
            )(
              "Accueil"
            )
          ),
          li()(
            a(
              href := "/",
              attr("aria-current") := "page",
              cls := "fr-breadcrumb__link"
            )(
              pageName
            )
          )
        )
      )
    )

  def loggedInNavBar()(implicit request: RequestHeader) =
    div(cls := "fr-header__menu fr-modal")(
      div(cls := "fr-container")(
        tag("nav")(cls := "fr-nav")(
          ul(cls := "fr-nav__list")(
            generateLink(controllers.routes.ApplicationController.dashboard.url, "Accueil"),
            generateLink(
              controllers.routes.ApplicationController.myApplications.url,
              "Mes demandes"
            ),
            generateLink(
              controllers.routes.GroupController.showEditMyGroups.url,
              "Mes groupes"
            ),
            generateLink(controllers.routes.UserController.home.url, "Utilisateurs"),
            generateLink(controllers.routes.AreaController.all.url, "Déploiment"),
            generateLink(controllers.routes.ApplicationController.stats.url, "Stats"),
            generateLink(
              Assets.versioned("pdf/mandat_administration_plus_juillet_2019.pdf").url,
              "Mandat"
            )
          )
        )
      )
    )

}
