package views

import constants.Constants
import controllers.routes.HomeController
import scalatags.Text.all._
import scalatags.Text.tags2

object accessibility {

  def declaration(): Tag =
    views.main.publicLayout(
      "Accessibilité - Administration+",
      div(
        h1(cls := "fr-mb-6w")("Déclaration d’accessibilité"),
        p("Établie le 11 mars 2024."),
        p(
          "L’agence nationale de la cohésion des territoires s’engage à rendre son service accessible, conformément à l’article 47 de la loi n° 2005-102 du 11 février 2005."
        ),
        p(
          "À cette fin, Administration+ s’inscrit dans ",
          a(
            href := "https://beta.gouv.fr/accessibilite/schema-pluriannuel",
            target := "_blank",
            rel := "noopener"
          )(
            "le schéma pluriannuel de beta.gouv.fr"
          ),
          "."
        ),
        p(
          "Cette déclaration d’accessibilité s’applique à ",
          strong("Administration+"),
          " (",
          a(href := "https://aplus.beta.gouv.fr")("https://aplus.beta.gouv.fr"),
          ")."
        ),
        h2("État de conformité"),
        p(
          strong("Administration+"),
          " est ",
          strong(span(data.printfilter := "lowercase")("non conforme")),
          " avec le ",
          tags2.abbr(title := "Référentiel général d’amélioration de l’accessibilité")("RGAA"),
          ". ",
          "Le site n’a encore pas été audité."
        ),
        h2("Établissement de cette déclaration d’accessibilité"),
        p("Cette déclaration a été établie le 11 mars 2024."),
        h3("Technologies utilisées"),
        p(
          "L’accessibilité d’Administration+ s’appuie sur les technologies suivantes :"
        ),
        ul(cls := "technical-information technologies-used")(
          li("HTML"),
          li("WAI-ARIA"),
          li("CSS"),
          li("JavaScript")
        ),
        h2("Amélioration et contact"),
        p(
          "Si vous n’arrivez pas à accéder à un contenu ou à un service, vous pouvez contacter le responsable d’Administration+ pour être orienté vers une alternative accessible ou obtenir le contenu sous une autre forme."
        ),
        ul(cls := "basic-information feedback h-card")(
          li(
            "E-mail : ",
            a(href := s"mailto:${Constants.supportEmail}")(
              Constants.supportEmail
            )
          )
        ),
        p("Nous essayons de répondre dans les 5 jours ouvrés."),
        h2("Voie de recours"),
        p(
          "Cette procédure est à utiliser dans le cas suivant : vous avez signalé au responsable du site internet un défaut d’accessibilité qui vous empêche d’accéder à un contenu ou à un des services du portail et vous n’avez pas obtenu de réponse satisfaisante."
        ),
        p("Vous pouvez :"),
        ul(
          li(
            a(
              href := "https://formulaire.defenseurdesdroits.fr/",
              target := "_blank",
              rel := "noopener"
            )(
              "Écrire un message au Défenseur des droits"
            )
          ),
          li(
            a(
              href := "https://www.defenseurdesdroits.fr/saisir/delegues",
              target := "_blank",
              rel := "noopener"
            )(
              "Contacter le délégué du Défenseur des droits dans votre région"
            )
          ),
          li(
            "Envoyer un courrier par la poste (gratuit, ne pas mettre de timbre) :",
            br,
            "Défenseur des droits",
            br,
            "Libre réponse 71120 75342 Paris CEDEX 07"
          )
        ),
        hr,
        p(
          "Cette déclaration d’accessibilité a été créée le 11 mars 2024 en s’appuyant sur le ",
          a(
            href := "https://betagouv.github.io/a11y-generateur-declaration/#create",
            target := "_blank",
            rel := "noopener"
          )(
            "Générateur de Déclaration d’Accessibilité de BetaGouv"
          ),
          "."
        )
      ),
      breadcrumbs = ("Accessibilité", HomeController.declarationAccessibilite.url) :: Nil,
    )

}
