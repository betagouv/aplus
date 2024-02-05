package views

import scalatags.Text.all._

object errors {

  def public500(): Tag =
    views.main.publicLayout(
      "Erreur inattendue",
      public500Main(),
      addBreadcrumbs = false,
    )

  /** https://www.systeme-de-design.gouv.fr/elements-d-interface/modeles/page-d-erreurs */
  private def public500Main(): Tag =
    div(cls := "fr-container")(
      div(
        cls := "fr-my-7w fr-mt-md-12w fr-mb-md-10w fr-grid-row fr-grid-row--gutters fr-grid-row--middle fr-grid-row--center"
      )(
        div(cls := "fr-py-0 fr-col-12 fr-col-md-6")(
          h1("Erreur inattendue"),
          p(cls := "fr-text--sm fr-mb-3w")("Erreur 500"),
          p(cls := "fr-text--sm fr-mb-5w")(
            "Désolé, le service rencontre un problème. ",
            "Celui-ci est probablement temporaire. ",
          ),
          p(cls := "fr-text--lead fr-mb-3w")(
            "Essayez de rafraîchir la page ou bien ressayez plus tard. ",
            "Si le problème venait à persister, vous pouvez contacter le support Administration+."
          ),
        )
      )
    )

}
