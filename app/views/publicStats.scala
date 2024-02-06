package views

import cats.syntax.all._
import controllers.routes.Assets
import scalatags.Text.all._
import scalatags.Text.tags2
import views.helpers.head.publicCss

object publicStats {

  def page: Tag = basePage(
    "Statistiques - Administration+",
    frag(
      div(
        cls := "fr-container fr-my-6w",
        h1("Statistiques d’Administration+")
      ),
      div(
        cls := "fr-container fr-my-6w",
        div(
          cls := "fr-grid-row fr-grid-row--gutters",
          div(
            cls := "fr-col-md-8 fr-col-sm-12 fr-col-lg-8",
            h2("Utilisation du service"),
            p(
              "Histogramme du nombre de demandes déposées chaque mois par les aidants ",
              "actifs sur la plateforme."
            )
          )
        ),
        div(
          cls := "fr-grid-row fr-grid-row--gutters fr-mb-4w",
          div(
            cls := "fr-col-md-12 fr-col-sm-12 fr-col-lg-12",
            iframe(
              src := "https://statistiques.aplus.beta.gouv.fr/public/question/e2ac028c-8311-42cd-9770-f7b28763294b",
              attr("frameborder") := "0",
              // Use attr here, otherwise width and height are put in the style attribute
              attr("width") := "100%",
              attr("height") := "400",
              attr("allowtransparency").empty,
            )
          )
        ),
        div(
          cls := "fr-grid-row fr-grid-row--gutters",
          div(
            cls := "fr-col-md-8 fr-col-sm-12 fr-col-lg-8",
            h2("Impact sur l’année"),
            p(
              "Les calculs sont effectués sur une année glissante. La mise à jour est journalière."
            )
          )
        ),
        div(
          cls := "fr-grid-row fr-grid-row--gutters",
          div(
            cls := "fr-col-md-6 fr-col-sm-12 fr-col-lg-6",
            iframe(
              src := "https://statistiques.aplus.beta.gouv.fr/public/question/2e205bc5-6498-4309-8ae9-3c4a26c47447",
              attr("frameborder") := "0",
              attr("width") := "100%",
              attr("height") := "400",
              attr("allowtransparency").empty,
            )
          ),
          div(
            cls := "fr-col-md-6 fr-col-sm-12 fr-col-lg-6",
            iframe(
              src := "https://statistiques.aplus.beta.gouv.fr/public/question/6f10c8fc-1d4b-458e-8bd4-f93051c5c5d6",
              attr("frameborder") := "0",
              attr("width") := "100%",
              attr("height") := "400",
              attr("allowtransparency").empty,
            )
          ),
        ),
        div(
          cls := "fr-grid-row fr-grid-row--gutters fr-mb-4w",
          div(
            cls := "fr-col-md-12 fr-col-sm-12 fr-col-lg-12",
            iframe(
              src := "https://statistiques.aplus.beta.gouv.fr/public/question/511fb17f-6eb1-4e31-802b-183e7187c95d",
              attr("frameborder") := "0",
              attr("width") := "100%",
              attr("height") := "400",
              attr("allowtransparency").empty,
            )
          )
        ),
      )
    )
  )

  private def basePage(
      pageTitle: String,
      inner: Frag,
      additionalHeadTags: Frag = frag(),
      additionalFooterTags: Frag = frag()
  ): Tag =
    html(
      lang := "fr",
      attr("data-fr-scheme") := "system",
      scalatags.Text.all.head(
        meta(charset := "utf-8"),
        meta(
          name := "viewport",
          content := "width=device-width, initial-scale=1, shrink-to-fit=no"
        ),
        publicCss("generated-js/dsfr/dsfr.min.css"),
        publicCss("generated-js/utility/utility.min.css"),
        meta(name := "theme-color", content := "#000091"),
        link(
          rel := "icon",
          `type` := "image/png",
          href := Assets.versioned("images/favicon.png").url
        ),
        tags2.title(pageTitle),
        additionalHeadTags
      ),
      body(
        header(
          role := "banner",
          cls := "fr-header",
          div(
            cls := "fr-header__body",
            div(
              cls := "fr-container",
              div(
                cls := "fr-header__body-row",
                div(
                  cls := "fr-header__brand fr-enlarge-link",
                  div(
                    cls := "fr-header__brand-top",
                    div(
                      cls := "fr-header__logo",
                      p(
                        cls := "fr-logo",
                        "Agence",
                        br,
                        "Nationale",
                        br,
                        "de la Cohésion",
                        br,
                        "des Territoires"
                      )
                    ),
                    div(
                      cls := "fr-header__navbar",
                      button(
                        cls := "fr-btn--menu fr-btn",
                        data.fr.opened := "false",
                        aria.controls := "modal-menu",
                        aria.haspopup := "menu",
                        id := "button-menu-mobile",
                        title := "Menu",
                        "Menu"
                      )
                    )
                  ),
                  div(
                    cls := "fr-header__service",
                    a(
                      href := "/",
                      cls := "fr-link",
                      title := "Accueil - Administration+",
                      span(cls := "fr-header__service-title", "Administration+")
                    ),
                    p(
                      cls := "fr-header__service-tagline",
                      "Résoudre les blocages administratifs complexes ou urgents"
                    )
                  )
                ),
                div(
                  cls := "fr-header__tools",
                  div(
                    cls := "fr-header__tools-links",
                    ul(
                      cls := "fr-links-group",
                      li(
                        a(
                          href := "https://docs.aplus.beta.gouv.fr/faq",
                          cls := "fr-link",
                          target := "_blank",
                          rel := "noopener",
                          "FAQ"
                        )
                      ),
                      li(
                        a(
                          href := "/",
                          cls := "fr-link fr-icon-lock-line",
                          "Se connecter"
                        )
                      )
                    )
                  )
                )
              )
            )
          ),
          div(
            cls := "fr-header__menu fr-modal",
            id := "modal-menu",
            aria.labelledby := "button-menu-mobile",
            div(
              cls := "fr-container",
              button(
                cls := "fr-btn--close fr-btn",
                aria.controls := "modal-menu",
                title := "Fermer",
                "Fermer"
              ),
              div(cls := "fr-header__menu-links"),
              tags2.nav(
                cls := "fr-nav",
                id := "navigation-menu",
                role := "navigation",
                aria.label := "Menu principal",
                ul(
                  cls := "fr-nav__list",
                  li(
                    cls := "fr-nav__item",
                    a(
                      cls := "fr-nav__link",
                      href := "/",
                      role := "tab",
                      "Accueil"
                    )
                  ),
                  li(
                    cls := "fr-nav__item",
                    a(
                      cls := "fr-nav__link",
                      href := "/stats",
                      role := "tab",
                      attr("aria-current") := "page",
                      "Statistiques"
                    )
                  ),
                )
              )
            )
          )
        ),
        tags2.main(
          inner
        ),
        footer(
          cls := "fr-footer",
          role := "contentinfo",
          id := "footer",
          div(
            cls := "fr-container",
            div(
              cls := "fr-footer__body",
              div(
                cls := "fr-footer__brand fr-enlarge-link",
                a(
                  href := "/",
                  title := "Retour à l’accueil du site - Administration+",
                  p(
                    cls := "fr-logo",
                    "Agence",
                    br,
                    "Nationale",
                    br,
                    "de la Cohésion",
                    br,
                    "des Territoires"
                  )
                )
              ),
              div(
                cls := "fr-footer__content",
                p(
                  cls := "fr-footer__content-desc",
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
                ul(
                  cls := "fr-footer__content-list",
                  li(
                    cls := "fr-footer__content-item",
                    a(
                      cls := "fr-footer__content-link",
                      target := "_blank",
                      rel := "noopener",
                      href := "https://legifrance.gouv.fr",
                      "legifrance.gouv.fr"
                    )
                  ),
                  li(
                    cls := "fr-footer__content-item",
                    a(
                      cls := "fr-footer__content-link",
                      target := "_blank",
                      rel := "noopener",
                      href := "https://gouvernement.fr",
                      "gouvernement.fr"
                    )
                  ),
                  li(
                    cls := "fr-footer__content-item",
                    a(
                      cls := "fr-footer__content-link",
                      target := "_blank",
                      rel := "noopener",
                      href := "https://service-public.fr",
                      "service-public.fr"
                    )
                  ),
                  li(
                    cls := "fr-footer__content-item",
                    a(
                      cls := "fr-footer__content-link",
                      target := "_blank",
                      rel := "noopener",
                      href := "https://data.gouv.fr",
                      "data.gouv.fr"
                    )
                  )
                )
              )
            ),
            div(
              cls := "fr-footer__bottom",
              ul(
                cls := "fr-footer__bottom-list",
                li(
                  cls := "fr-footer__bottom-item",
                  span(cls := "fr-footer__bottom-link", "Accessibilité : non conforme")
                ),
                li(
                  cls := "fr-footer__bottom-item",
                  a(
                    cls := "fr-footer__bottom-link",
                    href := "https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation",
                    target := "_blank",
                    rel := "noopener",
                    "Mentions légales"
                  )
                ),
                li(
                  cls := "fr-footer__bottom-item",
                  a(
                    cls := "fr-footer__bottom-link",
                    href := "https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation",
                    target := "_blank",
                    rel := "noopener",
                    "Données personnelles et gestion des cookies"
                  )
                ),
                li(
                  cls := "fr-footer__bottom-item",
                  a(
                    cls := "fr-footer__bottom-link",
                    href := "https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation",
                    target := "_blank",
                    rel := "noopener",
                    "Conditions générales d’utilisation"
                  )
                ),
                li(
                  cls := "fr-footer__bottom-item",
                  a(cls := "fr-footer__bottom-link", href := "/#contact", "Contact")
                )
              ),
              div(
                cls := "fr-footer__bottom-copy",
                p(
                  "Sauf mention explicite de propriété intellectuelle détenue par des tiers, les contenus de ce site sont proposés sous ",
                  a(
                    href := "https://github.com/etalab/licence-ouverte/blob/master/LO.md",
                    target := "_blank",
                    rel := "noopener",
                    "licence etalab-2.0"
                  )
                )
              )
            )
          )
        ),
        script(
          `type` := "module",
          src := Assets.versioned("generated-js/dsfr/dsfr.module.min.js").url
        ),
        script(
          `type` := "application/javascript",
          attr("nomodule").empty,
          src := Assets.versioned("generated-js/dsfr/dsfr.nomodule.min.js").url
        ),
        additionalFooterTags
      )
    )

}
