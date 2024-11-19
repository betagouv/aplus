package views

import controllers.routes.{ApplicationController, Assets}
import helper.Time
import java.time.LocalDate
import play.api.libs.json.Json
import scalatags.Text.all._
import scalatags.Text.tags2
import serializers.ApiModel.DeploymentData

object publicStats {

  def page(deploymentData: DeploymentData): Tag =
    views.main.publicLayout(
      "Statistiques - Administration+",
      mainContent(deploymentData),
      breadcrumbs = ("Statistiques", ApplicationController.stats.url) :: Nil,
      additionalHeadTags = frag(
        link(
          rel := "stylesheet",
          href := Assets.versioned("generated-js/dsfr-chart/Charts/dsfr-chart.css").url
        )
      ),
      additionalFooterTags = frag(
        script(
          src := Assets.versioned("generated-js/dsfr-chart/Charts/dsfr-chart.umd.js").url
        ),
      ),
    )

  private def mainContent(deploymentData: DeploymentData): Frag =
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
        div(
          cls := "fr-grid-row fr-grid-row--gutters",
          deployment(deploymentData)
        ),
      )
    )

  def deployment(deploymentData: DeploymentData): Frag =
    div(
      cls := "fr-col-12",
      h2("Déploiement territorial"),
      div(cls := "fr-mb-4w")(
        deploymentMap(deploymentData),
      ),
      tags2.section(cls := "fr-accordion")(
        h3(cls := "fr-accordion__title")(
          button(
            cls := "fr-accordion__btn",
            aria.expanded := "false",
            aria.controls := "deployment-table-accordion"
          )("Chiffres de la couverture territoriale sous forme de tableau")
        ),
        div(cls := "fr-collapse", id := "deployment-table-accordion")(
          deploymentTable(deploymentData)
        )
      ),
    )

  def deploymentMap(deploymentData: DeploymentData): Frag = {
    val totalCount = deploymentData.organisationSets.length
    val data = Json.stringify(
      Json.obj(
        deploymentData.areasData.map(area =>
          area.areaCode -> area.numOfOrganisationSetWithOneInstructor
        ): _*
      )
    )
    div(cls := "part_container")(
      tag("map-chart")(
        id := "deploiment-map-chart",
        attr("data") := data,
        attr("valuenat") := totalCount,
        attr(
          "name"
        ) := "Nombre d’organismes ayant au moins un agent habilité à répondre aux demandes",
        attr("color") := "blue-ecume",
        attr("date") := Time.formatPatternFr(LocalDate.now(), "dd/MM/YYYY"),
      )
    )
  }

  def deploymentTable(deploymentData: DeploymentData): Frag = {
    val tableHeader = "Département" :: "Couverture" :: deploymentData.organisationSets.map(
      _.organisations.map(_.shortName).mkString(" / ")
    )

    val tableRows: List[List[Tag]] = deploymentData.areasData.map(area =>
      th(attr("scope") := "row")(area.areaName) ::
        td(cls := "fr-cell--right")(
          area.numOfOrganisationSetWithOneInstructor.toString + " / " + deploymentData.organisationSets.length
        ) ::
        deploymentData.organisationSets.map(orgSet =>
          td(cls := "fr-cell--right")(
            area.numOfInstructorByOrganisationSet.get(orgSet.id).map(_.toString).getOrElse("N/A")
          )
        )
    )

    div(cls := "fr-table fr-table--bordered")(
      div(cls := "fr-table__wrapper")(
        div(cls := "fr-table__container")(
          div(cls := "fr-table__content")(
            table(
              caption(
                "Couverture territoriale",
                div(cls := "fr-table__caption__desc")(
                  "La colonne de couverture indique le nombre d’organismes ayant au moins un agent habilité à répondre aux demandes sur Administration+. Les autres colonnes indiquent le nombre d’agents par organisme."
                )
              ),
              thead(
                tr(
                  frag(
                    tableHeader.map(header =>
                      th(attr("scope") := "col", cls := "fr-cell--multiline")(header)
                    )
                  )
                )
              ),
              tbody(
                frag(
                  tableRows.map(row =>
                    tr(
                      frag(row)
                    )
                  )
                )
              )
            )
          )
        )
      )
    )
  }

}
