package views

import scalatags.Text.all._
import constants.Constants

/** https://www.systeme-de-design.gouv.fr/elements-d-interface/modeles/page-d-erreurs */
object errors {

  def public403(): Tag =
    views.main.publicLayout(
      "Accès non autorisé - Administration+",
      public403Main(),
      addBreadcrumbs = false,
    )

  private def public403Main(): Tag =
    div(cls := "fr-container")(
      div(
        cls := "fr-my-7w fr-mt-md-12w fr-mb-md-10w fr-grid-row fr-grid-row--gutters fr-grid-row--middle fr-grid-row--center"
      )(
        div(cls := "fr-py-0 fr-col-12 fr-col-md-6")(
          h1("Accès non autorisé"),
          p(cls := "fr-text--sm fr-mb-3w")("Erreur 403"),
          p(cls := "fr-text--sm fr-mb-5w")(
            "Il semblerait que vous ayez tenté d’accéder à une partie sécurisée du site pour laquelle vous ne disposez pas des autorisations nécessaires.",
          ),
          p(cls := "fr-text--lead fr-mb-3w")(
            "Si vous pensez qu’il s’agit d’une omission, vous pouvez contacter le support Administration+ à l’adresse ",
            Constants.supportEmail,
            "."
          ),
        )
      )
    )

  def public500(): Tag =
    views.main.publicLayout(
      "Erreur inattendue - Administration+",
      public500Main(),
      addBreadcrumbs = false,
    )

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
            "Essayez de rafraîchir la page, sinon merci de réessayer plus tard. ",
            "Si le problème venait à persister, vous pouvez contacter le support Administration+ à l’adresse ",
            Constants.supportEmail,
            "."
          ),
        )
      )
    )

}
