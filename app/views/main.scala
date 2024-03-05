package views

import cats.syntax.all._
import controllers.routes.{
  ApplicationController,
  AreaController,
  Assets,
  GroupController,
  HomeController,
  JavascriptController,
  LoginController,
  UserController
}
import models.{Authorization, User}
import play.api.mvc.{Call, RequestHeader}
import scalatags.Text.all._
import scalatags.Text.tags2
import views.helpers.head.publicCss

object main {

  def layout(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      pageName: String,
      content: Frag,
      additionalHeadTags: Frag = frag(),
      additionalFooterTags: Frag = frag()
  )(implicit
      request: RequestHeader,
  ): Tag = dsfrLayout(
    pageName,
    breadcrumbs(pageName),
    content,
    loggedInQuickLinks(currentUser),
    loggedInNavBar(currentUserRights),
    additionalHeadTags,
    additionalFooterTags
  )

  def publicLayout(
      pageName: String,
      content: Frag,
      addBreadcrumbs: Boolean = true,
      additionalHeadTags: Frag = frag(),
      additionalFooterTags: Frag = frag()
  ): Tag = dsfrLayout(
    pageName,
    if (addBreadcrumbs) breadcrumbs(pageName) else frag(),
    content,
    frag(),
    frag(),
    additionalHeadTags,
    additionalFooterTags
  )

  private def dsfrLayout(
      pageTitle: String,
      navigation: Frag,
      content: Frag,
      quickLinks: Frag,
      navBar: Frag,
      additionalHeadTags: Frag,
      additionalFooterTags: Frag
  ): Tag =
    html(lang := "fr", attr("data-fr-scheme") := "system")(
      head(
        meta(charset := "utf-8"),
        meta(name := "format-detection", attr("content") := "telephone=no"),
        meta(
          name := "viewport",
          attr("content") := "width=device-width, initial-scale=1, shrink-to-fit=no"
        ),
        publicCss("generated-js/dsfr/dsfr.min.css"),
        publicCss("generated-js/utility/utility.min.css"),
        publicCss("stylesheets/aplus-dsfr.css"),
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
        tags2.title(pageTitle),
        additionalHeadTags
      ),
      body(
        bodyHeader(quickLinks, navBar),
        div(cls := "main-container")(
          tags2.main(role := "main")(
            navigation,
            content
          )
        ),
        bodyFooter(additionalFooterTags)
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

  private val navBarResponsiveMenuModalId = "aplus-navbar-responsive-menu-modal"
  private val navBarResponsiveMenuModalButtonId = "aplus-navbar-responsive-menu-modal-button"

  private def bodyHeader(quickLinks: Frag, navBar: Frag): Tag =
    header(role := "banner", cls := "fr-header")(
      div(cls := "fr-header__body")(
        div(cls := "fr-container")(
          div(cls := "fr-header__body-row")(
            div(cls := "fr-header__brand fr-enlarge-link")(
              div(cls := "fr-header__brand-top")(
                div(cls := "fr-header__logo")(
                  p(cls := "fr-logo")(
                    "Agence",
                    br,
                    "Nationale",
                    br,
                    "de la Cohésion",
                    br,
                    "des Territoires"
                  )
                ),
                div(cls := "fr-header__operator")(
                  img(
                    cls := "fr-responsive-img",
                    src := Assets.versioned("images/logo_small_150x160.png").url,
                    alt := "Logo Administration+"
                  )
                ),
                div(cls := "fr-header__navbar")(
                  button(
                    cls := "fr-btn--menu fr-btn",
                    data("fr-opened") := "false",
                    aria.controls := navBarResponsiveMenuModalId,
                    aria.haspopup := "menu",
                    id := navBarResponsiveMenuModalButtonId,
                    title := "Menu"
                  )(
                    "Menu"
                  )
                )
              ),
              div(cls := "fr-header__service")(
                a(
                  href := HomeController.index.url,
                  title := "Accueil - Administration+"
                )(
                  p(cls := "fr-header__service-title")(
                    "Administration+"
                  ),
                  p(cls := "fr-header__service-tagline")(
                    "Ensemble pour débloquer, rapidement et efficacement"
                  )
                )
              )
            ),
            quickLinks
          )
        )
      ),
      navBar
    )

  private def loggedInQuickLinks(currentUser: User) =
    div(cls := "fr-header__tools")(
      div(cls := "fr-header__tools-links")(
        ul(cls := "fr-btns-group")(
          (
            if (currentUser.sharedAccount)
              frag()
            else
              li(
                a(
                  cls := "fr-btn fr-icon-account-circle-line",
                  href := UserController.editProfile.url,
                  title := s"Mon profil - ${currentUser.email}"
                )(
                  currentUser.email
                )
              )
          ),
          li(
            a(
              cls := "fr-btn fr-icon-logout-box-r-line",
              href := LoginController.disconnect.url
            )(
              "Se déconnecter"
            )
          )
        )
      )
    )

  private def loggedInNavBar(
      currentUserRights: Authorization.UserRights
  )(implicit request: RequestHeader) =
    div(
      cls := "fr-header__menu fr-modal",
      id := navBarResponsiveMenuModalId,
      aria.labelledby := navBarResponsiveMenuModalButtonId
    )(
      div(cls := "fr-container")(
        button(
          cls := "fr-btn--close fr-btn",
          aria.controls := navBarResponsiveMenuModalId,
          title := "Fermer"
        )(
          "Fermer"
        ),
        div(cls := "fr-header__menu-links"),
        tags2.nav(
          cls := "fr-nav",
          role := "navigation",
          aria.label := "Menu principal"
        )(
          ul(cls := "fr-nav__list")(
            navItem(
              "Mes demandes",
              true,
              ApplicationController.myApplications,
            ),
            navItem(
              "Mes groupes",
              true,
              GroupController.showEditMyGroups,
            ),
            navItem(
              "Admin : demandes",
              Authorization.canSeeApplicationsMetadata(currentUserRights),
              ApplicationController.applicationsAdmin,
              serializers.Keys.QueryParam.numOfMonthsDisplayed + "=3"
            ),
            navItem(
              "Utilisateurs",
              Authorization.canSeeUsers(currentUserRights),
              UserController.home,
            ),
            navItem(
              "Déploiment",
              Authorization.isAdminOrObserver(currentUserRights),
              AreaController.all,
            ),
            navItem(
              "Evénements",
              Authorization.isAdmin(currentUserRights),
              UserController.allEvents,
            ),
            navItem(
              "Stats",
              Authorization.canSeeStats(currentUserRights),
              ApplicationController.stats,
            ),
            li(cls := "fr-nav__item")(
              a(
                href := Assets.versioned("pdf/mandat_administration_plus_juillet_2019.pdf").url,
                cls := "fr-nav__link",
                target := "_blank",
                rel := "noopener noreferrer",
                attr("aria-current") := "false"
              )(
                "Mandat"
              )
            )
          )
        )
      )
    )

  private def navItem(text: String, hasAuthorization: Boolean, route: Call, queryParams: String*)(
      implicit request: RequestHeader,
  ): Frag =
    if (hasAuthorization)
      li(cls := "fr-nav__item")(
        a(
          href := (
            if (queryParams.isEmpty)
              route.url
            else
              route.url + "?" + queryParams.mkString("&")
          ),
          title := text,
          cls := "fr-nav__link",
          target := "_self",
          attr("aria-current") := (if (request.path === route.url) "true" else "false")
        )(
          text
        )
      )
    else
      frag()

  private def bodyFooter(additionalFooterTags: Frag): Tag =
    footer(cls := "fr-footer", role := "contentinfo", id := "footer")(
      div(cls := "fr-container")(
        div(cls := "fr-footer__bottom")(
          ul(cls := "fr-footer__bottom-list")(
            li(cls := "fr-footer__bottom-item")(
              a(cls := "fr-footer__bottom-link")(
                href := HomeController.index.url,
                "Administration+"
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
        defer,
        src := Assets.versioned("generated-js/dsfr/dsfr.module.min.js").url
      ),
      script(
        `type` := "application/javascript",
        defer,
        attr("nomodule").empty,
        src := Assets.versioned("generated-js/dsfr/dsfr.nomodule.min.js").url
      ),
      script(
        `type` := "text/javascript",
        src := JavascriptController.javascriptRoutes.url
      ),
      script(
        `type` := "application/javascript",
        defer,
        src := Assets.versioned("generated-js/index.js").url
      ),
      additionalFooterTags
    )

}
