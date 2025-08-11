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
        p("En vigueur à partir du 01/08/2025"),
        h2("Article 1 - Champ d’application"),
        p(
          "Les présentes conditions générales d’utilisation (ci-après « CGU ») précisent le cadre juridique d’Administration+ (ci-après le « Produit numérique ») et définissent les conditions d’accès et d’utilisation des Services par l’Utilisateur."
        ),
        p(
          "L’annexe relative à l’accord sur le traitement des données, au sens de l’article 28-3 du RGPD, fait partie intégrante des présentes CGU."
        ),
        h2("Article 2 - Objet du Produit numérique"),
        p(
          "Administration+ est un produit numérique développé au sein de l’Agence nationale de la cohésion des territoires (ANCT) dans le but de mettre en relation des aidants professionnels mandatés dans le cadre de leur fonction (ci-après « Aidant ») par des usagers avec des agents publics habilités (ci-après « Instructeur ») pour résoudre des blocages administratifs complexes et/ou urgents d’usagers."
        ),
        h2("Article 3 - Définitions"),
        p(
          "“Administration” désigne tout personne morale chargée d’une mission de service public administratif conformément à l’article L. 100-3 du code des relations entre le public et l’administration (CRPA) qui emploie des Aidants et Instructeurs."
        ),
        p(
          "“Administrateur” désigne toute personne physique, membre de l’équipe du Produit numérique qui s’assure du bon fonctionnement de l’outil et de la parfaite mise en relation entre l’Aidant et l’Instructeur."
        ),
        p(
          "“Aidant” désigne toute personne physique, agent public habilité par son Administration qui a conclu un mandat écrit généré par le Produit numérique avec l’usager accompagné (par le biais de son Administration ou via le Produit numérique) et qualifie/reformule ses requêtes auprès de l’Instructeur."
        ),
        p(
          "“Éditeur” désigne la personne morale qui met à la disposition du public le Produit numérique, à savoir l’ANCT."
        ),
        p(
          "“Instructeur” désigne toute personne physique, agent public habilité par son Administration qui est saisi et mandaté par écrit par un Aidant pour débloquer rapidement une situation administrative complexe et/ou urgente."
        ),
        p(
          "“Produit numérique” désigne le service numérique qui permet aux agents publics de résoudre les problématiques administratives complexes et/ou urgentes des usagers."
        ),
        p(
          "“Responsable de groupe” désigne toute personne physique, agent public habilité par son Administration chargé de gérer l’utilisation du Produit numérique dans son ou ses établissements et sur son territoire."
        ),
        p(
          "“Services” désigne les fonctionnalités proposées par le Produit numérique pour répondre à ses finalités"
        ),
        p(
          "“Utilisateur” désigne toute personne physique, agent public habilité, qui s’inscrit sur le Produit numérique."
        ),
        h2("Article 4 - Fonctionnalités"),
        h3("4.1 Inscription sur le Produit numérique"),
        p(
          "Chaque Administration désigne un Responsable de groupe primaire dont elle communique le nom et les coordonnées à l’Administrateur du produit. Sur cette base, l’administrateur crée un compte pour ce Responsable de groupe primaire. C’est ensuite au Responsable de groupe primaire, une fois son compte créé, de créer les comptes de son groupe, c’est-à-dire des utilisateurs de son administration. Il peut également désigner des Responsables de groupe qui eux-mêmes pourront créer des comptes d’Utilisateur pour leur groupe."
        ),
        p(
          "Les responsables, primaire ou non, auront également la responsabilité de supprimer les comptes de leur groupe."
        ),
        h3("4.2 Connexion au compte sur le Produit numérique"),
        p(
          "Pour se connecter à son compte, l’Utilisateur renseigne son adresse courriel professionnelle sur le Produit numérique et reçoit un lien de connexion. Il peut aussi se connecter en renseignant un identifiant et un mot de passe."
        ),
        p(
          "Le lien de connexion est unique et valide pendant une durée de trente (30) minutes."
        ),
        h3("4.3 Rôles de l’Utilisateur"),
        p(
          "L’Utilisateur est soit :"
        ),
        ul(
          li(
            b("Un Aidant"),
            " qui est en contact direct avec les usagers, pour qualifier et reformuler les requêtes avant de les soumettre à l’Instructeur dans le cas où leur intervention peut permettre une résolution. L’Aidant a, au préalable, conclu un mandat écrit avec l’usager généré par le Produit numérique ;"
          ),
          li(
            b("Un Instructeur"),
            " qui soit a été saisi par un Aidant pour répondre à un problème administratif urgent et/ou complexe, ou soit qui invite ou est invité par un autre Instructeur sur une demande. Les instructeurs apportent une réponse une directe aux usagers à travers les canaux de communication personnalisés qu’ils utilisent d’ordinaire. Ils peuvent également apporter une réponse à l’Aidant si nécessaire ;"
          ),
          li(
            b("Un Responsable de groupe primaire"),
            " désigné par son Administration qui est le premier responsable d’un groupe. Ce responsable peut nommer d’autres responsables au sein de son groupe, créer des comptes et retirer des comptes du groupe."
          ),
          li(
            b("Un Responsable"),
            " de groupe qui gère également l’utilisation du Produit numérique au sein de son Administration (création et désactivation des comptes utilisateurs, suivi des demandes, délai de réponse, clôture des demandes après réponse). Par principe, il n’a pas accès au contenu des demandes instruites par l’Aidant ou l’Instructeur de son organisme sauf s’il bénéficie du rôle d’Instructeur ou qu’il a été invité par un Aidant ou un autre Instructeur sur la demande ;"
          ),
          li(
            b("Un Administrateur"),
            " qui s’assure du bon fonctionnement du Produit numérique, assiste les Utilisateurs en cas de problématique technique ou fonctionnelle et atteste de la parfaite mise en relation entre l’Aidant et l’Instructeur. L’Administrateur n’a pas accès aux contenus des demandes des usagers et aux fichiers téléversés, sauf invitation par un Utilisateur sur une demande de modération pour permettre le bon fonctionnement du produit"
          ),
        ),
        div(cls := "fr-table")(
          div(cls := "fr-table__wrapper")(
            div(cls := "fr-table__container")(
              div(cls := "fr-table__content")(
                table(
                  thead(
                    tr(
                      th(attr("scope") := "col")("Étape"),
                      th(attr("scope") := "col")("Acteurs concernés"),
                      th(attr("scope") := "col")("Accès aux données"),
                      th(attr("scope") := "col")("Durée"),
                      th(attr("scope") := "col")("Commentaire"),
                    )
                  ),
                  tbody(
                    tr(
                      td("Phase 1 : Ouverture demande"),
                      td("Aidant, Instructeur"),
                      td("Accès complet aux données et fichiers en pièce jointe"),
                      td("Jusqu'à fermeture"),
                      td("Phase active de traitement de la demande")
                    ),
                    tr(
                      td("Phase 2 : Fermeture de la demande"),
                      td("Aidant, Instructeur"),
                      td("Accès complet aux données (sans pièces jointes)"),
                      td("6 mois après la fermeture de la demande"),
                      td("L'aidant ferme la demande, un délai de 6 mois s'ouvre")
                    ),
                    tr(
                      td("Phase 3 : Anonymisation des données citoyens"),
                      td("Administrateurs uniquement"),
                      td(
                        "Accès aux médatonnées uniquement (suppression des données personnelles des citoyens)"
                      ),
                      td("2 mois après suppressions des données de la demande"),
                      td(
                        "Pour restauration technique en cas de problème technique sur la Plateforme pour un usage statistique"
                      )
                    ),
                    tr(
                      td("Phase 4 : Suppression définitive"),
                      td("Tous les utilisateurs"),
                      td("Toutes les données sont supprimées définitivement"),
                      td("Définitif"),
                      td("Pas de retour en arrière possible")
                    ),
                  )
                )
              )
            )
          )
        ),
        h3("4.4 Collecte de données hautement personnelles par certaines Administrations"),
        p(
          "Seules certaines Administrations habilitées, eu égard à leurs missions et compétences peuvent renseigner certaines informations notamment le numéro fiscal de référence, l’identifiant Caisse d’Allocation Familiale (CAF) et Mutuelle Sociale Agricole (MSA) sur le Produit numérique. Les champs permettant de les renseigner ne sont visibles que pour ces Administrations habilitées."
        ),
        p(
          "Les Administrations mentionnées par le décret n° 2019-341 du 19 avril 2019 peuvent renseigner le numéro d’inscription au répertoire (NIR) sur le Produit numérique."
        ),
        p(
          "Dans ce cadre, l’ANCT agit en qualité de sous-traitant, conformément au RGPD et à l’accord sur le traitement des données en annexe des présentes CGU."
        ),
        h2("Article 5 - Responsabilités"),
        h3("5.1 L’Éditeur du Produit numérique"),
        p(
          "Les sources des informations diffusées sur le Produit numérique sont réputées fiables mais l’Éditeur ne garantit pas qu’elle soit exempte de défauts, d’erreurs ou d’omissions."
        ),
        p(
          "L’Éditeur s’engage à la sécurisation du Produit numérique, notamment en prenant toutes les mesures nécessaires permettant de garantir la sécurité et la confidentialité des informations fournies. Il ne saurait être tenu responsable de tout contenu publié par l’Utilisateur sur le Produit numérique, notamment des fausses informations renseignées par ce dernier."
        ),
        p(
          "L’Éditeur fournit les moyens nécessaires et raisonnables pour assurer un accès continu au Produit numérique. Il se réserve le droit de faire évoluer, de modifier ou de suspendre, sans préavis, le Produit numérique pour des raisons de maintenance ou pour tout autre motif jugé nécessaire."
        ),
        p(
          "En aucun cas, l’Éditeur n’est responsable de l’habilitation d’un Utilisateur, celle-ci relève uniquement de la responsabilité de l’Administration dont il fait partie."
        ),
        p(
          "En cas de manquement à une ou plusieurs des stipulations des présentes CGU, l’Éditeur se réserve le droit de suspendre ou de supprimer le compte de l’Utilisateur responsable."
        ),
        h3("5.2 L’Utilisateur"),
        p(
          "L’Utilisateur s’assure de garder son lien de connexion, son identifiant et mot de passe secrets. Toute divulgation du lien, quelle que soit sa forme, est strictement interdite. Il assume les risques liés à l’utilisation de son adresse courriel."
        ),
        p(
          "Lors de la création d’un compte sur le Produit Numérique, le Responsable de groupe s’assure que la personne dont le compte est créé est bien habilitée par son Administration à répondre aux demandes de blocage administratif par le biais du Produit numérique. Lors de la première connexion, ce nouvel utilisateur devra accepter les CGU."
        ),
        p(
          "Toute information transmise par l’Utilisateur est de sa seule responsabilité. Il est rappelé que toute personne procédant à une fausse déclaration pour elle-même ou pour autrui s’expose notamment aux sanctions prévues à l’article 441-1 du code pénal, prévoyant des peines pouvant aller jusqu’à trois ans d’emprisonnement et 45 000 euros d’amende."
        ),
        p(
          "L’Utilisateur s’engage formellement par les présentes CGU à ne pas mettre en ligne de contenus ou informations contraires aux dispositions légales et réglementaires en vigueur. Il veille également à ne pas communiquer de données sensibles ou de secrets protégés par la loi, et à ne pas publier de contenus illicites notamment dans les zones de champs libres."
        ),
        p(
          "L’Aidant certifie avoir recueilli l’autorisation de l’usager pour le traitement de sa demande par un Instructeur sur le Produit numérique. Il s’engage également à clôturer la demande lorsque l’Instructeur indique qu’elle est résolue."
        ),
        p(
          "Le Responsable de groupe s’engage à désactiver le compte de tout Aidant ou Instructeur qui quitte ses fonctions. Si un compte Utilisateur est inactif pendant plus de 6 mois, son compte sera désactivé par l’Administrateur et le Responsable de groupe devra contacter l’Administrateur pour le faire réactiver."
        ),
        h2("Article 6 - Mise à jour des CGU"),
        p(
          "Les termes des présentes CGU peuvent être amendés à tout moment, en fonction des modifications apportées au Produit numérique, de l’évolution de la législation ou pour tout autre motif jugé nécessaire."
        ),
        p(
          "Chaque modification donne lieu à une nouvelle version qui doit être acceptée par l’Utilisateur selon les modalités prévues par le Produit numérique."
        ),
        h2("Article 7 - Loi applicable et juridiction compétente"),
        p(
          "Les présentes CGU sont soumises à la loi française. En cas de litige, l’Éditeur et l’Administration s’engagent à coopérer avec diligence et bonne foi en vue de parvenir à une solution amiable, par voie de transaction, de médiation et de conciliation."
        ),
        p(
          "Toutefois, si aucun accord n’est trouvé dans un délai de deux mois pour toute contestation ou litige relatif aux CGU, la juridiction compétente est le tribunal administratif de Paris."
        )
      ),
      breadcrumbs = ("Conditions générales d’utilisation", HomeController.cgu.url) :: Nil,
    )

}
