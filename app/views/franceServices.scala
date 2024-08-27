package views

import helper.TwirlImports.toHtml
import models.{Authorization, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Flash, RequestHeader}
import play.twirl.api.Html
import scalatags.Text.all._
import views.helpers.forms.CSRFInput

object franceServices {

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
  )(implicit
      flash: Flash,
      request: RequestHeader,
      webJarsUtil: WebJarsUtil,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)(
      s"France Services"
    )(Nil)(
      frag(
        h5(cls := "title--addline", "France Services"),
        CSRFInput,
        div(cls := "mdl-cell mdl-cell--12-col", id := "tabulator-france-services-table"),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "france-services-download-btn-csv",
            href := "#",
            i(cls := "fas fa-download"),
            " Téléchargement au format CSV"
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "france-services-download-btn-xlsx",
            href := "#",
            i(cls := "fas fa-download"),
            " Téléchargement au format XLSX"
          )
        ),
        div(id := "france-services-alerts", cls := "mdl-cell mdl-cell--12-col"),
        h5(cls := "title--addline", "Ajout"),
        div(cls := "mdl-cell mdl-cell--12-col", id := "tabulator-france-services-add-table"),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "add-france-services-new-line",
            href := "#",
            i(cls := "fas fa-plus"),
            " Ajouter une ligne vide"
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "add-france-services-csv",
            href := "#",
            i(cls := "fas fa-file"),
            " Ajouter un CSV"
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "add-france-services-download-csv",
            href := "#",
            i(cls := "fas fa-download"),
            " Télécharger en CSV"
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "add-france-services-dedup",
            href := "#",
            "Déduplication"
          )
        ),
        div(id := "france-services-add-alerts", cls := "mdl-cell mdl-cell--12-col"),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          button(
            id := "add-france-services-upload",
            cls := "mdl-button mdl-button--raised",
            i(cls := "fas fa-upload"),
            " Envoyer les ajouts"
          )
        )
      )
    )(
      views.helpers.head.publicScript("generated-js/xlsx.full.min.js")
    )

}
