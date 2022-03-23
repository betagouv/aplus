package views

import cats.syntax.all._
import helper.TwirlImports.toHtml
import java.util.UUID
import models.{Area, Authorization, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Flash, RequestHeader}
import play.twirl.api.Html
import scalatags.Text.all._

object applicationsAdmin {

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      selectedArea: Option[Area],
      numOfMonthsDisplayed: Int,
  )(implicit
      flash: Flash,
      request: RequestHeader,
      webJarsUtil: WebJarsUtil,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)(
      s"Metadonnées des demandes"
    )(Nil)(
      frag(
        areaSelect("applications-area-id", currentUser, selectedArea.getOrElse(Area.allArea).id),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          label(
            `for` := "num-of-months-displayed-box",
            "Nombre de mois : "
          ),
          input(
            `type` := "number",
            id := "num-of-months-displayed-box",
            cls := "single--width-48px",
            name := "num-of-months-displayed-box",
            value := numOfMonthsDisplayed.toString
          ),
        ),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "applications-download-btn-csv",
            href := "#",
            i(cls := "fas fa-download"),
            " Téléchargement au format CSV"
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--3-col",
          a(
            id := "applications-download-btn-xlsx",
            href := "#",
            i(cls := "fas fa-download"),
            " Téléchargement au format XLSX"
          )
        ),
        div(cls := "mdl-cell mdl-cell--12-col", id := "tabulator-applications-table"),
      )
    )(
      views.helpers.head.publicScript("generated-js/xlsx.full.min.js")
    )

  def areaSelect(selectId: String, currentUser: User, selectedAreaId: UUID): Frag =
    (currentUser.areas.length > 1).some
      .filter(identity)
      .map(_ =>
        p(
          cls := "mdl-cell mdl-cell--3-col",
          "Territoire : ",
          select(
            id := selectId,
            name := "area-selector",
            frag(
              (Area.allArea :: currentUser.areas.flatMap(Area.fromId)).map(area =>
                option(
                  value := area.id.toString,
                  (area.id === selectedAreaId).some.filter(identity).map(_ => selected),
                  if (area.id === Area.allArea.id)
                    "Tous les territoires"
                  else
                    area.toString
                )
              )
            )
          )
        )
      )

}
