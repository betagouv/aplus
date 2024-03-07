package views

import cats.syntax.all._
import constants.Constants
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
    quickLinks = loggedInQuickLinks(currentUser),
    navBar = loggedInNavBar(currentUserRights),
    footer = baseBodyFooter(isLoggedIn = true),
    additionalHeadTags = frag(
      link(
        rel := "stylesheet",
        media := "screen,print",
        href := Assets.versioned("generated-js/index.css").url
      ),
      additionalHeadTags,
    ),
    additionalFooterTags = frag(
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
    quickLinks = publicQuickLinks(),
    navBar = baseNavBar(frag()),
    footer = baseBodyFooter(isLoggedIn = false),
    additionalHeadTags,
    additionalFooterTags
  )

  def publicErrorLayout(
      pageName: String,
      content: Frag,
  ): Tag = dsfrLayout(
    pageName,
    frag(),
    content,
    quickLinks = frag(),
    navBar = baseNavBar(frag()),
    footer = baseBodyFooter(isLoggedIn = false),
    additionalHeadTags = frag(),
    additionalFooterTags = frag()
  )

  private def dsfrLayout(
      pageTitle: String,
      navigation: Frag,
      content: Frag,
      quickLinks: Frag,
      navBar: Frag,
      footer: Frag,
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
        meta(name := "theme-color", attr("content") := "#000091"),
        link(
          rel := "icon",
          `type` := "image/svg+xml",
          href := Assets.versioned("images/favicon.png").url,
        ),
        tags2.title(pageTitle),
        additionalHeadTags
      ),
      body(
        bodyHeader(quickLinks, navBar),
        tags2.main(cls := "main-container", role := "main")(
          navigation,
          content
        ),
        footer,
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
                    cls := "fr-responsive-img aplus-header-img",
                    src := Assets.versioned("images/logo_small_150x160.png").url,
                    alt := "Logo Administration+"
                  )
                ),
                div(cls := "fr-header__navbar")(
                  button(
                    cls := "fr-btn--menu fr-btn",
                    data.fr.opened := "false",
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
                  p(cls := "fr-header__service-title fr-link")(
                    "Administration+"
                  ),
                  p(cls := "fr-header__service-tagline")(
                    "Ensemble pour résoudre les blocages administratifs complexes ou urgents"
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
                )(
                  "Mon profil"
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

  private def publicQuickLinks() =
    div(cls := "fr-header__tools")(
      div(cls := "fr-header__tools-links")(
        ul(cls := "fr-btns-group")(
          li(
            a(
              href := "https://docs.aplus.beta.gouv.fr/faq",
              cls := "fr-btn",
              target := "_blank",
              rel := "noopener",
              "FAQ"
            )
          ),
          li(
            a(
              href := "/",
              cls := "fr-btn fr-icon-lock-line",
              "Se connecter"
            )
          )
        )
      )
    )

  private def loggedInNavBar(
      currentUserRights: Authorization.UserRights
  )(implicit request: RequestHeader) =
    baseNavBar(
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

  private def baseNavBar(innerNavBar: Frag) =
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
        innerNavBar
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

  private def baseBodyFooter(isLoggedIn: Boolean): Tag =
    footer(cls := "fr-footer", role := "contentinfo", id := "footer")(
      // Links here are internal links
      // cf https://www.systeme-de-design.gouv.fr/elements-d-interface/composants/pied-de-page
      if (isLoggedIn) footerInternalLinks() else frag(),
      div(cls := "fr-container")(
        div(cls := "fr-footer__body")(
          div(cls := "fr-footer__brand fr-enlarge-link")(
            p(cls := "fr-logo")(
              "Agence",
              br,
              "Nationale",
              br,
              "de la Cohésion",
              br,
              "des Territoires"
            ),
            a(
              cls := "fr-footer__brand-link",
              href := HomeController.index.url,
              title := "Retour à l’accueil du site - Administration+",
              img(
                cls := "fr-footer__logo aplus-footer-img",
                src := Assets.versioned("images/logo_small_150x160.png").url,
                alt := "Logo Administration+"
              )
            )
          ),
          div(cls := "fr-footer__content")(
            p(cls := "fr-footer__content-desc")(
              "Administration+ est le service qui permet de résoudre les blocages ",
              "administratifs complexes ou urgents et fait partie de l’",
              a(
                href := "https://incubateur.anct.gouv.fr/actions/startups-territoires/",
                target := "_blank",
                rel := "noopener",
                "Incubateur des Territoires",
              ),
              ", membre du réseau d’incubateurs ",
              a(
                href := "https://beta.gouv.fr/startups/aplus.html",
                target := "_blank",
                rel := "noopener",
                "beta.gouv.fr",
              ),
              ". "
            ),
            ul(cls := "fr-footer__content-list")(
              li(cls := "fr-footer__content-item")(
                a(cls := "fr-footer__content-link")(
                  target := "_blank",
                  rel := "noopener",
                  href := "https://legifrance.gouv.fr",
                  "legifrance.gouv.fr"
                )
              ),
              li(cls := "fr-footer__content-item")(
                a(cls := "fr-footer__content-link")(
                  target := "_blank",
                  rel := "noopener",
                  href := "https://gouvernement.fr",
                  "gouvernement.fr"
                )
              ),
              li(cls := "fr-footer__content-item")(
                a(cls := "fr-footer__content-link")(
                  target := "_blank",
                  rel := "noopener",
                  href := "https://service-public.fr",
                  "service-public.fr"
                )
              ),
              li(cls := "fr-footer__content-item")(
                a(cls := "fr-footer__content-link")(
                  target := "_blank",
                  rel := "noopener",
                  href := "https://data.gouv.fr",
                  "data.gouv.fr"
                )
              )
            )
          )
        ),
        div(cls := "fr-footer__bottom")(
          // Links here are for required and legal things, not internal links
          // cf https://www.systeme-de-design.gouv.fr/elements-d-interface/composants/pied-de-page
          ul(cls := "fr-footer__bottom-list")(
            li(cls := "fr-footer__bottom-item")(
              span(cls := "fr-footer__bottom-link", "Accessibilité : non conforme")
            ),
            li(cls := "fr-footer__bottom-item")(
              a(cls := "fr-footer__bottom-link")(
                href := "https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation",
                target := "_blank",
                rel := "noopener",
                "Mentions légales"
              )
            ),
            li(cls := "fr-footer__bottom-item")(
              a(cls := "fr-footer__bottom-link")(
                href := "https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation",
                target := "_blank",
                rel := "noopener",
                "Données personnelles et gestion des cookies"
              )
            ),
            li(cls := "fr-footer__bottom-item")(
              a(cls := "fr-footer__bottom-link")(
                href := "https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation",
                target := "_blank",
                rel := "noopener",
                "Conditions générales d’utilisation"
              )
            ),
          ),
          div(cls := "fr-footer__bottom-copy")(
            p(
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
      )
    )

  private def footerInternalLinks(): Tag =
    div(cls := "fr-footer__top")(
      div(cls := "fr-container")(
        div(cls := "fr-grid-row fr-grid-row--start fr-grid-row--gutters")(
          div(cls := "fr-col-12 fr-col-sm-3 fr-col-md-2")(
            h3(cls := "fr-footer__top-cat")("Administration+"),
            ul(cls := "fr-footer__top-list")(
              li(
                a(
                  cls := "fr-footer__top-link",
                  href := HomeController.help.url
                )("Aide")
              ),
              li(
                a(
                  cls := "fr-footer__top-link",
                  href := ApplicationController.showExportMyApplicationsCSV.url
                )("Exporter mes demandes en CSV")
              ),
            )
          ),
          div(cls := "fr-col-12 fr-col-sm-3 fr-col-md-2")(
            h3(cls := "fr-footer__top-cat")("Nous contacter"),
            ul(cls := "fr-footer__top-list")(
              li(
                a(
                  cls := "fr-footer__top-link",
                  href := s"mailto:${Constants.supportEmail}",
                  title := s"Email de contact - ${Constants.supportEmail}",
                )(Constants.supportEmail)
              ),
            )
          ),
        )
      )
    )

}
