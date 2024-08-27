package views

import controllers.routes.HomeController
import scalatags.Text.all._

object legal {

  def information(): Tag =
    views.main.publicLayout(
      "Mentions légales - Administration+",
      div(
        h1(cls := "fr-mb-6w")("Administration+ – Mentions légales"),
        h2("Éditeur de la plateforme"),
        p(
          "Administration+ est édité au sein de l’Incubateur des Territoires de l’Agence nationale de la cohésion des territoires (ANCT) située :",
          br,
          br,
          "20 avenue de Ségur",
          br,
          "75007 Paris",
          br,
          "France",
          br,
          br,
          "Téléphone : 01 85 58 60 00",
        ),
        h2("Directeur de la publication"),
        p(
          "Le directeur de publication est Monsieur Stanislas BOURRON, Directeur général de l’ANCT"
        ),
        h2("Hébergement de la plateforme"),
        p(
          "La plateforme est hébergée par :",
          br,
          br,
          "OVH",
          br,
          "2 rue Kellermann",
          br,
          "59100 Roubaix",
          br,
          "France",
        ),
        h2("Accessibilité"),
        p(
          "La conformité aux normes d’accessibilité numérique est un ",
          a(href := HomeController.declarationAccessibilite.url, "objectif ultérieur"),
          " mais nous tâchons de rendre cette plateforme accessible à toutes et à tous."
        ),
        h2("En savoir plus"),
        p(
          "Pour en savoir plus sur la politique d’accessibilité numérique de l’État : ",
          a(
            href := "https://accessibilite.numerique.gouv.fr/",
            target := "_blank",
            rel := "noopener"
          )(
            "https://accessibilite.numerique.gouv.fr/"
          )
        ),
        h2("Signaler un dysfonctionnement"),
        p(
          "Si vous rencontrez un défaut d’accessibilité vous empêchant d’accéder à un contenu ou une fonctionnalité de la plateforme, merci de nous en faire part : ",
          a(href := "mailto:contact@aplus.beta.gouv.fr", "contact@aplus.beta.gouv.fr"),
          br,
          br,
          "Si vous n’obtenez pas de réponse rapide de notre part, vous êtes en droit de faire parvenir vos doléances ou une demande de saisine au Défenseur des Droits.",
        ),
        h2("Sécurité"),
        p(
          "La plateforme est protégée par un certificat électronique, matérialisé pour la grande majorité des navigateurs par un cadenas. Cette protection participe à la confidentialité des échanges.",
          br,
          br,
          "En aucun cas, les services associés à la plateforme ne seront à l’origine d’envoi d’e-mails pour vous demander la saisie d’informations personnelles."
        )
      ),
      breadcrumbs = ("Mentions légales", HomeController.mentionsLegales.url) :: Nil,
    )

}
