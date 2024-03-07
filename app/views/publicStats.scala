package views

import scalatags.Text.all._

object publicStats {

  def page: Tag =
    views.main.publicLayout(
      "Statistiques - Administration+",
      mainContent,
      addBreadcrumbs = false
    )

  private val mainContent: Frag =
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

}
