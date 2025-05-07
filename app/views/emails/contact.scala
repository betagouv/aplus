package views

import constants.Constants
import scalatags.Text.all._
import scalatags.Text.tags2

object contact {

  def page(): Tag =
    views.main.publicLayout(
      "Contact - Administration+",
      div(cls := "fr-mb-15w")(
        h1(cls := "fr-mb-6w")("Comment pouvons-nous vous aider ?"),
        div(cls := "fr-callout")(
          h2(cls := "fr-callout__title")("Vous avez besoin de renseignements ?"),
          p(cls := "fr-callout__text")(
            a(
              href := "https://docs.aplus.beta.gouv.fr/",
              target := "_blank",
              rel := "noopener noreferrer"
            )(
              "Notre aide en ligne"
            ),
            " rassemble de nombreuses informations, comme des guides détaillés (en image et en vidéo) destinés ",
            a(
              href := "https://docs.aplus.beta.gouv.fr/guides-et-tutoriels/vous-etes-aidant",
              target := "_blank",
              rel := "noopener noreferrer"
            )(
              "aux aidants"
            ),
            ", ",
            a(
              href := "https://docs.aplus.beta.gouv.fr/guides-et-tutoriels/vous-etes-instructeur",
              target := "_blank",
              rel := "noopener noreferrer"
            )(
              "aux instructeurs"
            ),
            " et ",
            a(
              href := "https://docs.aplus.beta.gouv.fr/guides-et-tutoriels/responsable-de-groupe",
              target := "_blank",
              rel := "noopener noreferrer"
            )(
              "aux responsables de groupes"
            ),
            "."
          ),
          button(cls := "fr-btn")(
            a(
              href := "https://docs.aplus.beta.gouv.fr/",
              target := "_blank",
              rel := "noopener noreferrer"
            )(
              "Consulter notre aide en ligne"
            )
          )
        ),
        h2(cls := "fr-mt-8w fr-mb-6w")("Quelle est votre question ?"),
        div(cls := "fr-grid-row fr-grid-row--gutters")(
          accordion(
            "aplus-accordion-question-1",
            "Je n’arrive pas à me connecter, pouvez-vous m’aider ?",
            ul(
              li(
                "Si vous utilisez Internet Explorer, ",
                a(
                  href := "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/pourquoi-ne-plus-utiliser-le-navigateur-internet-explorer-de-microsoft",
                  target := "_blank",
                  rel := "noopener noreferrer"
                )("changez de navigateur"),
                ". Nous recommandons Firefox ou Chrome. Configurez l’un de ces 2 navigateurs comme navigateur par défaut pour plus de praticité.",
              ),
              li(
                "Si vous ne recevez pas d’e-mail après avoir vu l’écran « Consultez vos e-mails » : consultez votre dossier de spams. Si après 10 à 15 minutes vous n'avez toujours pas reçu l’e-mail, ",
                a(
                  href := "https://docs.aplus.beta.gouv.fr/contacter-lequipe",
                  target := "_blank",
                  rel := "noopener noreferrer"
                )("contactez notre équipe de support"),
                ".",
              ),
              li(
                "Le lien magique de connexion qui vous est envoyé par mail expire au bout de 30 minutes. Vérifiez que vous n’avez pas appuyé sur un lien déjà expiré.",
              ),
              li(
                "Si votre e-mail n’est pas reconnu durant votre connexion, essayez avec votre adresse nominative (du type prenom.nom@votre-structure.fr), si le problème persiste, ",
                a(
                  href := "https://docs.aplus.beta.gouv.fr/contacter-lequipe",
                  target := "_blank",
                  rel := "noopener noreferrer"
                )("contactez notre équipe de support"),
                "."
              )
            ),
          ),
          accordion(
            "aplus-accordion-question-2",
            "Mon adresse e-mail n’est pas reconnue, que faire ?",
            p(
              "Vous êtes un conseiller France services ? Notez que la plateforme France Services n’est pas relié à celle de Administration+ ! Si votre adresse e-mail n’est pas reconnue sur A+, cela veut dire qu’il n'y a pas de compte A+ lié à cette adresse e-mail. Si vous connaissez le responsable de groupe A+ de votre structure, merci de vous rapprocher de cet utilisateur pour qu’il vous puisse vous créer votre compte. Si ce n’est pas le cas, ",
              a(
                href := "https://docs.aplus.beta.gouv.fr/contacter-lequipe",
                target := "_blank",
                rel := "noopener noreferrer"
              )("contactez notre équipe de support"),
              "."
            )
          ),
          accordion(
            "aplus-accordion-question-3",
            "Comment modifier les informations, rôle et adresse e-mail d’un utilisateur ?",
            p(
              "Pour des raisons de sécurité et de traçabilité, aucun utilisateur (y compris les responsables) ne peut modifier l’adresse e-mail et le rôle associé à un compte A+ existant. Seules les informations relatives à l'utilisateur du compte (nom, prénom, fonction, numéro de téléphone) peuvent être modifiées par l'utilisateur même.",
              br,
              "Modifier ses informations (à partir de 0:12) : ",
              a(
                href := "https://www.youtube.com/watch?v=tvcgInfO-CI&t=12s",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Gérer son compte et son groupe A+"),
              br,
              "Pour modifier l'adresse e-mail d'un compte et/ou son rôle associé (aidant, instructeur, responsable de groupe), ",
              a(
                href := "https://docs.aplus.beta.gouv.fr/contacter-lequipe",
                target := "_blank",
                rel := "noopener noreferrer"
              )("contactez notre équipe de support"),
              "."
            )
          ),
          accordion(
            "aplus-accordion-question-4",
            "Comment ajouter le compte d’un collègue à mon groupe A+ ?",
            p(
              "Attention : seuls les « Responsables de groupe » peuvent créer de nouveaux comptes Administration+ et leur attribuer le rôle de responsable et/ou aidant et/ou instructeur.",
              br,
              "Vous pouvez consulter le guide en vidéo à l'adresse suivante (à partir de 1:56) : ",
              a(
                href := "https://www.youtube.com/watch?v=tvcgInfO-CI&t=116s",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Gérer son compte et son groupe A+"),
              br,
              "Ou bien consulter notre aide à ce sujet : ",
              a(
                href := "https://docs.aplus.beta.gouv.fr/guides-et-tutoriels/responsable-de-groupe/ajouter-un-utilisateur",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Responsable : ajouter et/ou créer un compte utilisateur")
            )
          ),
          accordion(
            "aplus-accordion-question-5",
            "Comment créer un compte sur A+ ?",
            p(
              "Dans le cadre du déploiement France Services, les comptes sont créés à la demande des préfectures, de l’ANCT ou des partenaires d’Administration+. Pour obtenir un compte, vous pouvez les contacter ou ",
              a(
                href := "https://docs.aplus.beta.gouv.fr/contacter-lequipe",
                target := "_blank",
                rel := "noopener noreferrer"
              )("contacter directement notre équipe de support"),
              ".",
              br,
              br,
              "Les utilisateurs qui ont un profil « responsable » peuvent créer des nouveaux comptes pour les utilisateurs de leur groupe."
            )
          ),
          accordion(
            "aplus-accordion-question-6",
            "Ma question ne figure pas dans cette liste",
            p(
              "Si votre question ne figure pas dans cette liste et que vous ne trouvez pas la réponse ",
              a(
                href := "https://docs.aplus.beta.gouv.fr/",
                target := "_blank",
                rel := "noopener noreferrer"
              )(
                "dans notre aide en ligne"
              ),
              ", contactez-nous par e-mail à l’adresse ",
              a(
                href := s"mailto:${Constants.supportEmail}",
              )(
                Constants.supportEmail
              ),
              ".",
              br,
              "Notre équipe de support répond généralement sous 1 à 2 jours ouvrés."
            )
          )
        )
      )
    )

  private def accordion(collapseId: String, title: String, content: Tag): Tag =
    tags2.section(cls := "fr-accordion")(
      h3(cls := "fr-accordion__title")(
        button(
          cls := "fr-accordion__btn",
          attr("aria-expanded") := "false",
          attr("aria-controls") := collapseId
        )(title)
      ),
      div(cls := "fr-collapse", id := collapseId)(content)
    )

}
