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

  def cgu(): Tag =
    views.main.publicLayout(
      "Conditions générales d’utilisation - Administration+",
      div(
        h1(cls := "fr-mb-6w")("Conditions générales d’utilisation"),
        p(
          "Du service public numérique « Administration+ » pour résoudre les blocages administratifs complexes et/ou urgents"
        ),
        p(
          "Les présentes conditions générales d’utilisation visent à réguler les interactions entre les utilisateurs de la plateforme Administration+."
        ),
        p("Conditions générales d’utilisation à partir du 1er octobre 2020"),
        h2("1. Le service Administration+"),
        h3("1.1. Objet du service"),
        p(
          "Mettre en relation des agents publics ou chargés d’une mission de service public via l’utilisation d’une plateforme numérique pour :"
        ),
        ul(
          li("résoudre des blocages administratifs complexes et/ou urgents des usagers ;"),
          li(
            "garantir l’accessibilité du service public aux personnes en situation de vulnérabilité ou d’urgence ;"
          ),
          li("participer à la lutte contre le non-recours aux droits ;"),
          li("faciliter les interactions entre les acteurs publics d’un territoire.")
        ),
        h3("1.2 L’usage du service"),
        p(
          "L’usage du service est réservé aux cas de blocages administratifs complexes et d’urgence sociale."
        ),
        h3("1.3 L’utilisation du service"),
        p(
          "L’utilisation du service repose sur une communauté d’utilisateurs habilités par leurs organismes respectifs."
        ),
        p("La « communauté Administration+ » repose sur 5 catégories de personnes :"),
        h4("1.3.1 Les aidants Administration+"),
        p(
          "Ce sont les agents en contact direct avec les usagers. Ils qualifient et reformulent les requêtes au nom de l’usager pour les soumettre à la communauté."
        ),
        p(
          "Il s’agit notamment des agents d’accueil France Services, des travailleurs sociaux publics, des délégués du Défenseur des droits et des élus des collectivités."
        ),
        h4("1.3.2 Les instructeurs Administration+ (opérateurs des administrations partenaires)"),
        p(
          "Ce sont les agents publics ou chargés d’une mission de service public, en poste dans des organismes de sécurité sociale ou des administrations déconcentrées de l’État ou des administrations décentralisées."
        ),
        p(
          "Ils ont pour mission de rechercher une réponse à une situation de blocage administratif complexe ou à une urgence sociale lorsqu’ils sont saisis par un aidant Administration+."
        ),
        p(
          "À noter : Ces opérateurs peuvent également initier une demande d’aide pour un usager sur la plateforme en recourant à la communauté Administration+."
        ),
        h4("1.3.3 Les responsables de groupe(s)"),
        p(
          "Ce sont des agents publics ou chargés d’une mission de service public désignés par leur direction pour gérer l’utilisation d’Administration+ dans leur organisme."
        ),
        p("Un responsable de groupe :"),
        ul(
          li(
            "crée et désactive les comptes des utilisateurs en cas de changement de poste ou à leur demande."
          ),
          li(
            "s’assure du bon suivi des demandes (délais de réponses et clôture des demandes après réponses)."
          ),
          li("accède à des outils statistiques pour mesurer la performance de son organisme.")
        ),
        p(
          "À noter : Il n’a pas accès au contenu des demandes instruites par les aidants ou les agents instructeurs de son organisme."
        ),
        h4("1.3.4 Les administrateurs"),
        p("Ce sont les membres de l’équipe Administration+ qui :"),
        ul(
          li("s’assurent du bon fonctionnement de l’outil,"),
          li("répondent aux interrogations des utilisateurs,"),
          li(
            "jouent un rôle de coordinateur entre les aidants Administration+ et les instructeurs Administration+."
          )
        ),
        p(
          "À noter : Ils n’ont pas accès au contenu des demandes conformément au Règlement général sur la protection des données (RGPD)."
        ),
        p(
          "Seuls 3 membres de l’équipe, en leur qualité « d’experts », peuvent être invités par tout membre de la communauté sur une demande. Ils sont également automatiquement saisis lorsque le délai de réponse est dépassé (cf. paragraphe 3.1). Ils n’ont pas accès aux fichiers téléversés sur la demande."
        ),
        h4("1.3.5 Les observateurs"),
        p(
          "Ce sont les pilotes et coordonnateurs d’administrations partenaires qui associent Administration+ à leur expérimentation :"
        ),
        ul(
          li(
            "La direction interministérielle de la transformation publique (DITP) pour ses programmes carte blanche et service-public +."
          ),
          li(
            "L’agence nationale de cohésion des territoires (ANCT) et la caisse des dépôts et consignation à travers la banque des territoires pour le programme France services."
          ),
          li(
            "Les coordonnateurs en préfecture, chargés de mission à la politique de la ville ou au développement du territoire, les coordonnateurs nationaux d’organismes et d’administrations centrales ou décentralisées."
          )
        ),
        h3("1.4 Fonctionnement général du service"),
        h4("1.4.1 La création de compte"),
        ul(
          li(
            "L’habilitation des utilisateurs est de la responsabilité de l’administration à laquelle ils appartiennent conformément à l’article ",
            a(
              href := "https://www.legifrance.gouv.fr/affichTexte.do?cidTexte=JORFTEXT000038029589&categorieLien=id",
              target := "_blank",
              rel := "noopener noreferrer"
            )("R-114-9-6"),
            " du code des relations du public avec l’administration (CRPA)."
          ),
          li(
            "Les administrations sont responsables de la traçabilité des actions de leurs agents conformément aux dispositions générales de la Commission nationale de l’informatique et des libertés (CNIL)."
          ),
          li(
            "Le compte Administration+ d’un utilisateur est créé par un responsable de groupe ou un administrateur."
          ),
          li(
            "Les partenaires peuvent renseigner une adresse e-mail nominative ou partagée pour l’utilisation d’Administration+."
          )
        ),
        h4("1.4.2 La connexion"),
        p("La connexion au compte Administration+ se fait avec l’adresse mail professionnelle."),
        p(
          "Dans le cas de l’utilisation d’une adresse e-mail partagée, toute réponse de l’utilisateur dans le cadre de l’échange devra mentionner son identité (prénom, nom) et le service auquel il appartient conformément au CRPA."
        ),
        p(
          "Exemples : Kevin Dupont - assistant social hôpital X ; Nadia Nguyen - référente finances publiques, service des impôts des particuliers."
        ),
        p(
          "Le lien de connexion est unique et valide 30 minutes. Il ne doit en aucun cas être transmis à un tiers."
        ),
        h4("1.4.3. La déconnexion"),
        p(
          "L’utilisateur d’Administration+ est invité à se déconnecter lorsqu’il a fini d’utiliser la plateforme. Cette déconnexion est impérative s’il s’est connecté depuis un poste de travail ou tout autre appareil partagé, pour éviter une connexion automatique par un tiers."
        ),
        h4("1.4.4 La désactivation de compte"),
        p(
          "Lorsqu’un utilisateur quitte ses fonctions, il incombe au responsable de groupe de désactiver son compte Administration+ et d’en informer l’équipe sur ",
          a(href := "mailto:contact@aplus.beta.gouv.fr", "contact@aplus.beta.gouv.fr"),
          "."
        ),
        h4("1.4.5 Ajout de fichiers à la demande"),
        p(
          "Tous les fichiers téléversés sur la demande sont supprimés dans un délai de 15 jours. Pour des raisons de sécurité, les fichiers n’ont pas de copie de sauvegarde et ne peuvent pas être restaurés en cas de panne du service."
        ),
        h2("2. Droits et devoirs des aidants Administration+"),
        h3("2.1 Cas d’utilisation de l’outil par l’aidant"),
        p(
          "Administration+ est un canal d’information complémentaire des outils professionnels existants. Il apporte une réponse de dernier recours dans les situations d’urgence sociale ou lorsque tous les dispositifs habituels connus de l’aidant Administration+ ont été épuisés (guichet, mail, téléphone, sites professionnels…)."
        ),
        h3("2.2 Consentement de l’usager"),
        p(
          "L’aidant Administration+ s’engage à informer et recueillir le consentement de l’usager pour l’utilisation de l’outil « d’Administration+ »."
        ),
        p(
          "L’aidant a l’obligation de faire signer un mandat à l’usager l’autorisant à instruire son cas par voie électronique. Le mandat est conservé le temps de la résolution de la demande. Il peut également être révoqué sur la demande de l’usager."
        ),
        p(
          "L’aidant transmet l’information de manière simple, claire, compréhensible et aisément accessible, comme prévu dans les dispositions de l’",
          a(
            href := "https://www.cnil.fr/fr/reglement-europeen-protection-donnees/chapitre3#Article12",
            target := "_blank",
            rel := "noopener noreferrer"
          )("article 12 du RGPD"),
          "."
        ),
        h3("2.3 Clôture de la demande"),
        p(
          "L’aidant Administration+ s’engage à clore la demande lorsqu’une solution a été apportée. La fermeture d’une demande entraîne une suppression des données personnelles de la plateforme sous 90 jours. Administration+ ne fournit pas un service d’archivage."
        ),
        p(
          "Au moment de la clôture, l’aidant Administration+ renseigne l’utilité de la réponse apportée par l’instructeur Administration+ pour l’usager en cliquant sur un de ces 3 émoticônes : 😐 ☹ 😊."
        ),
        h2("3. Droits et devoirs des instructeurs Administration+"),
        h3("3.1 Délais de réponse"),
        p(
          "Administration+ est un dispositif pour trouver une solution à un problème inextricable et/ou urgent."
        ),
        p(
          "L’instructeur Administration+ s’engage à répondre rapidement dans les jours suivant la création de la demande par l’aidant."
        ),
        p("Nous constatons généralement un délai de réponse de 4 jours."),
        h3("3.2 Cas d’une réponse non pertinente"),
        p(
          "L’outil intègre un bouton « Cette demande dispose d’une procédure standard que l’aidant aurait pu utiliser » pour réguler le flux de demandes entrant. L’instructeur Administration+ s’engage toutefois à indiquer brièvement la procédure standard existante s’il la connaît."
        ),
        p(
          "À noter : Le « silence gardé » pendant plus de 2 mois par certaines administrations sur une demande ou une démarche vaut accord, sauf exceptions. Pour certaines demandes, l’acceptation peut être acquise après un délai différent. Dans d’autres cas, le « silence gardé » sur une demande vaut refus. Voir le code des relations entre le public et l’administration : ",
          a(
            href := "https://www.legifrance.gouv.fr/affichCode.do?idSectionTA=LEGISCTA000031367609&cidTexte=LEGITEXT000031366350",
            target := "_blank",
            rel := "noopener noreferrer"
          )("article L231-1 à D231-3"),
          "."
        ),
        h2("4. Acceptation - manquement - modification des CGU"),
        h3("4.1 Acceptation"),
        p("L’utilisation de l’outil vaut acceptation de ces conditions générales d’utilisation."),
        h3("4.2 Manquement"),
        p(
          "4.2.1 Si je constate une utilisation abusive du service ou des messages contraires à la loi, je le signale sur ",
          a(href := "mailto:contact@aplus.beta.gouv.fr", "contact@aplus.beta.gouv.fr"),
          "."
        ),
        p(
          "4.2.2 En cas de non-respect des conditions générales d’utilisation, les administrateurs se réservent le droit d’exclure l’utilisateur du service et de lui notifier."
        ),
        h3("4.3 Modifications"),
        p(
          "Les présentes conditions générales d’utilisation peuvent être modifiées. Toutes modifications seront notifiées aux utilisateurs"
        ),
        h2("5. Responsabilité"),
        h3("5.1"),
        p(
          "La direction interministérielle du numérique (DINUM) ne saurait engager sa responsabilité en cas d’usage frauduleux de l’outil par un utilisateur."
        ),
        h3("5.2"),
        p(
          "Tout détournement de l’usage de la plateforme (point 1.1) ou manquement aux règles concernant la protection des données personnelles des usagers sera passible de poursuites pénales."
        )
      ),
      breadcrumbs = ("Conditions générales d’utilisation", HomeController.cgu.url) :: Nil,
    )

}
