package views

import constants.Constants
import scalatags.Text.all._
import scalatags.Text.tags2

object contact {

  def page(): Tag =
    views.main.publicLayout(
      "Contact - Administration+",
      div(
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
        h2("Quelle est votre question ?"),
        div(cls := "fr-grid-row fr-grid-row--gutters")(
          question(
            "Je n’arrive pas à me connecter, pouvez-vous m’aider ?",
            "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/je-narrive-pas-a-me-connecter-pouvez-vous-maider"
          ),
          question(
            "Mon adresse e-mail n’est pas reconnue, que faire ?",
            "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/mon-adresse-e-mail-nest-pas-reconnue-que-faire"
          ),
          question(
            "Comment modifier les informations, rôle et adresse e-mail d’un utilisateur ?",
            "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/comment-modifier-mon-adresse-mail"
          ),
          question(
            "Comment ajouter le compte d’un collègue à mon groupe A+ ?",
            "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/comment-ajouter-le-compte-dun-collegue-a-mon-groupe-a+"
          ),
          question(
            "Comment créer un compte sur A+ ?",
            "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/comment-creer-un-compte-sur-a+"
          ),
          ul(
            li(
              a(
                href := "https://docs.aplus.beta.gouv.fr/",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Je n’arrive pas à me connecter, pouvez-vous m’aider ?")
            ),
            li(
              a(
                href := "https://docs.aplus.beta.gouv.fr/",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Mon adresse e-mail n’est pas reconnue, que faire ?")
            ),
            li(
              a(
                href := "https://docs.aplus.beta.gouv.fr/",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Comment modifier les informations, rôle et adresse e-mail d’un utilisateur ?")
            ),
            li(
              a(
                href := "https://docs.aplus.beta.gouv.fr/",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Comment ajouter le compte d’un collègue à mon groupe A+ ?")
            ),
            li(
              a(
                href := "https://docs.aplus.beta.gouv.fr/",
                target := "_blank",
                rel := "noopener noreferrer"
              )("Comment créer un compte sur A+ ?")
            )
          ),
          tags2.section(cls := "fr-accordion")(
            h3(cls := "fr-accordion__title")(
              button(
                cls := "fr-accordion__btn",
                attr("aria-expanded") := "false",
                attr("aria-controls") := "aplus-accordion-other-question"
              )("Ma question ne figure pas dans cette liste")
            ),
            div(cls := "fr-collapse", id := "aplus-accordion-other-question")(
              p(
                "Si votre question ne figure pas dans cette liste et que vous ne trouvez pas la réponse ",
                a(
                  href := "https://docs.aplus.beta.gouv.fr/",
                  target := "_blank",
                  rel := "noopener noreferrer"
                )(
                  "dans notre aide en ligne"
                ),
                " contactez-nous par e-mail à l’adresse ",
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
    )

  private def question(title: String, docHref: String): Tag =
    div(cls := "fr-col-12")(
      div(cls := "fr-tile fr-enlarge-link")(
        div(cls := "fr-tile__body")(
          div(cls := "fr-tile__content")(
            h3(cls := "fr-tile__title")(
              a(
                href := docHref,
                target := "_blank",
                rel := "noopener noreferrer"
              )(title)
            )
          )
        )
      )
    )

}
