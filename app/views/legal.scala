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

  // v4
  def privacy(): Tag =
    views.main.publicLayout(
      "Politique de confidentialité - Administration+",
      div(
        h1(cls := "fr-mb-6w")("Administration+ – Politique de confidentialité"),
        p("Dernière mise à jour le 29/10/2024"),
        h2("Qui sommes-nous ?"),
        p(
          "Administration+ est un service public numérique développé au sein de l’Incubateur des territoires de l’Agence Nationale de la Cohésion des Territoires (ANCT). Il s’agit d’une plateforme qui met en relation des aidants professionnels avec des agents d’organismes publics pour régler rapidement des blocages administratifs complexes et urgents des usagers.",
        ),
        p(
          "Le responsable de traitement est l’ANCT, représentée par Monsieur Stanislas Bourron, Directeur général de l’Agence."
        ),
        h2("Pourquoi traitons-nous des données à caractère personnel ?"),
        p(
          "Administration+ traite des données à caractère personnel pour proposer une messagerie sécurisée dans le but de mettre en relation des aidants mandatés par les usagers avec des agents publics concernés par la demande de l’usager et indispensables pour résoudre le problème administratif."
        ),
        p(
          "Les données sont notamment traitées pour identifier les agents publics mandatés et les usagers concernés et diffuser la lettre d’information."
        ),
        h2("Quelles sont les données à caractère personnel que nous traitons ?"),
        ul(
          li(
            b("Données relatives à l’agent public"),
            " : nom, prénom, numéro de téléphone, adresse e-mail, messages échangés ;"
          ),
          li(
            b("Données relatives à l’usager"),
            " : nom, prénom, adresse e-mail, numéro de téléphone, adresse postale, date de naissance, champs libres, messages échangés, pièces jointes, données fiscales (en lien avec la DGFIP) ;"
          ),
          li(b("Données relatives à la traçabilité"), " : logs et adresse IP ;"),
          li(b("Données relatives à la lettre d’information"), " : nom, prénom, adresse e-mail."),
        ),
        h2("Qu’est-ce qui nous autorise à traiter des données à caractère personnel ?"),
        p(
          "Le traitement est nécessaire à l’exécution d’une mission d’intérêt public ou relevant de l’exercice de l’autorité publique dont est investie l’ANCT en tant que responsable de traitement, au sens de l’article 6-1 e) du RGPD."
        ),
        p(
          "Cette mission d’intérêt public se traduit en pratique notamment par l’article L. 1231-2 du code général des collectivités territoriales (CGCT)."
        ),
        div(cls := "fr-table")(
          div(cls := "fr-table__wrapper")(
            div(cls := "fr-table__container")(
              div(cls := "fr-table__content")(
                table(
                  caption(h2("Pendant combien de temps conservons-nous vos données ?")),
                  thead(
                    tr(
                      th(attr("scope") := "col")("Catégories de données"),
                      th(attr("scope") := "col")("Durée de conservation"),
                    )
                  ),
                  tbody(
                    tr(
                      td("Données relatives à l’agent public"),
                      td(
                        "2 ans à partir du dernier contact"
                      )
                    ),
                    tr(
                      td("Données relatives à l’usager"),
                      td(
                        "3 mois à partir du dernier contact (les pièces jointes sont supprimées au bout de 15 jours)"
                      )
                    ),
                    tr(
                      td("Données relatives à la traçabilité"),
                      td("1 an conformément à la LCEN")
                    ),
                    tr(
                      td("Données relatives à la lettre d’information"),
                      td(
                        "Jusqu’à la désinscription de l’utilisateur"
                      )
                    ),
                  )
                )
              )
            )
          )
        ),
        h2("Quels sont vos droits ?"),
        p("Vous disposez :"),
        ul(
          li("D’un droit d’information et d’accès à vos données ;"),
          li("D’un droit de rectification ;"),
          li("D’un droit d’opposition ;"),
          li("D’un droit à la limitation du traitement de vos données."),
        ),
        p(
          "Pour exercer vos droits, vous pouvez nous contacter à : ",
          a(href := s"mailto:contact@aplus.beta.gouv.fr")(
            "contact@aplus.beta.gouv.fr"
          )
        ),
        p(
          "Ou contacter la déléguée à la protection des données à : ",
          a(href := s"mailto:dpo@anct.gouv.fr")(
            "dpo@anct.gouv.fr"
          )
        ),
        p(
          "Puisque ce sont des droits personnels, nous ne traiterons votre demande que si nous sommes en mesure de vous identifier. Dans le cas contraire, nous pouvons être amenés à vous demander une preuve de votre identité."
        ),
        p(
          "Nous nous engageons à répondre à votre demande dans un délai raisonnable qui ne saurait excéder 1 mois à compter de la réception de votre demande. Si vous estimez que vos droits n’ont pas été respectés après nous avoir contactés, vous pouvez adresser une réclamation à la CNIL."
        ),
        h2("Qui peut avoir accès à vos données ?"),
        p("Les personnes suivantes ont accès à vos données en tant que destinataires :"),
        ul(
          li(
            "Les membres habilités de l’équipe d’Administration+ (administrateurs, développeurs notamment) ont accès à vos données, dans le cadre de leurs missions ;"
          ),
          li(
            "Les organismes publics et privés : CNAV, CNAM, CAF, CDAD, CRAMIF, Assurance Maladie, MSA, ASP, ministère des Finances, France Travail, ministère de l’Intérieur (préfectures) ;"
          ),
          li("La DILA dans le cadre de l’expérimentation « Place des Citoyens »."),
        ),
        h2("Qui nous aide à traiter vos données ?"),
        p(
          "Certaines données sont communiquées à des « sous-traitants » qui agissent pour le compte de l’ANCT, selon ses instructions."
        ),
        div(cls := "fr-table")(
          div(cls := "fr-table__wrapper")(
            div(cls := "fr-table__container")(
              div(cls := "fr-table__content")(
                table(
                  caption("Liste des sous-traitants"),
                  thead(
                    tr(
                      th(attr("scope") := "col")("Sous-traitant"),
                      th(attr("scope") := "col")("Traitement réalisé"),
                      th(attr("scope") := "col")("Pays destinataire"),
                      th(attr("scope") := "col")("Garanties"),
                    )
                  ),
                  tbody(
                    tr(
                      td("OVH"),
                      td("Hébergement"),
                      td("France"),
                      td(
                        a(
                          href := "https://us.ovhcloud.com/legal/data-processing-agreement/",
                          target := "_blank",
                          rel := "noopener",
                        )(
                          "https://us.ovhcloud.com/legal/data-processing-agreement/"
                        )
                      ),
                    ),
                    tr(
                      td("Brevo"),
                      td("Gestion de la lettre d’information"),
                      td("France"),
                      td(
                        a(
                          href := "https://www.brevo.com/legal/termsofuse/#data-processing-agreement-dpa",
                          target := "_blank",
                          rel := "noopener",
                        )(
                          "https://www.brevo.com/legal/termsofuse/#data-processing-agreement-dpa"
                        )
                      ),
                    ),
                    tr(
                      td("Zammad"),
                      td("Gestion du support"),
                      td("Allemagne"),
                      td(
                        a(
                          href := "https://zammad.com/en/company/privacy",
                          target := "_blank",
                          rel := "noopener",
                        )(
                          "https://zammad.com/en/company/privacy"
                        )
                      ),
                    ),
                  )
                )
              )
            )
          )
        ),
        h2("Cookies et traceurs"),
        p(
          "Un cookie est un fichier déposé sur votre terminal lors de la visite d’un site. Il a pour but de collecter des informations relatives à votre navigation et de vous adresser des services adaptés à votre terminal (ordinateur, mobile ou tablette)."
        ),
        p(
          "En application de l’article 5-3 de la directive ePrivacy, transposée à l’article 82 de la loi n° 78-17 du 6 janvier 1978 relative à l’informatique, aux fichiers et aux libertés, les cookies et traceurs suivent deux régimes distincts."
        ),
        p(
          "D’une part, les cookies strictement nécessaires au service ou ayant pour finalité exclusive de faciliter la communication par voie électronique, sont dispensés de consentement préalable.",
          br,
          "D’autre part, les cookies n’étant pas strictement nécessaires au service ou n’ayant pas pour finalité exclusive de faciliter la communication par voie électronique, doivent être consenti par l’utilisateur."
        ),
        p(
          "Ce consentement de la personne concernée constitue une base légale au sens du RGPD, à savoir l’article 6-1 a). Administration+ ne dépose aucun cookie tiers sur sa plateforme et ne nécessite aucun consentement."
        ),
        h2("Pour en savoir plus sur les cookies :"),
        ul(
          li(
            a(
              href := "https://www.cnil.fr/fr/cookies-et-autres-traceurs/regles/cookies/que-dit-la-loi",
              target := "_blank",
              rel := "noopener",
            )(
              "Cookies et traceurs : que dit la loi ?"
            )
          ),
          li(
            a(
              href := "https://www.cnil.fr/fr/cookies-et-autres-traceurs/comment-se-proteger/maitriser-votre-navigateur",
              target := "_blank",
              rel := "noopener",
            )(
              "Cookies les outils pour les maîtriser"
            )
          ),
        ),
      ),
      breadcrumbs = ("Politique de confidentialité", HomeController.privacy.url) :: Nil,
    )

}
