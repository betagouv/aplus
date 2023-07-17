package views

import cats.syntax.all._
import controllers.routes.UserController
import helper.TwirlImports.toHtml
import models.{Area, Authorization, User}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Flash, RequestHeader}
import play.twirl.api.Html
import scalatags.Text.all._

object users {

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      selectedArea: Area,
  )(implicit
      flash: Flash,
      request: RequestHeader,
      webJarsUtil: WebJarsUtil,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)(
      s"Gestion des utilisateurs - ${selectedArea.name}"
    )(
      views.helpers.head.publicCss("stylesheets/newForm.css")
    )(
      frag(
        currentUser.admin.some
          .filter(identity)
          .map(_ =>
            frag(
              a(
                cls := "mdl-button mdl-js-button mdl-button--raised mdl-cell mdl-cell--4-col mdl-cell--12-col-phone",
                href := UserController.all(selectedArea.id).url + "?vue=classique",
                "Vue utilisateurs classique"
              ),
              a(
                cls := "mdl-button mdl-js-button mdl-button--raised mdl-cell mdl-cell--4-col mdl-cell--12-col-phone",
                href := controllers.routes.SignupController.signupRequests.url,
                "Préinscription Aidants"
              ),
              a(
                cls := "mdl-button mdl-js-button mdl-button--raised mdl-cell mdl-cell--4-col mdl-cell--12-col-phone",
                href := controllers.routes.CSVImportController.importUsersFromCSV.url,
                "Importer utilisateurs"
              )
            )
          ),
        (currentUser.areas.length > 1).some
          .filter(identity)
          .map(_ =>
            p(
              cls := "mdl-cell",
              "Territoire : ",
              select(
                id := "changeAreaSelect",
                name := "area-selector",
                data("current-area") := selectedArea.id.toString,
                frag(
                  (Area.allArea :: currentUser.areas.flatMap(Area.fromId)).map(area =>
                    option(
                      value := area.id.toString,
                      data("redirect-url") := UserController.all(area.id).url + "?vue=nouvelle",
                      (area.id === selectedArea.id).some.filter(identity).map(_ => selected),
                      if (area.id === Area.allArea.id)
                        "tous les territoires"
                      else
                        area.toString
                    )
                  )
                )
              )
            )
          ),
        div(id := "current-area-value", data("area-id") := selectedArea.id.toString),
        div(
          id := "user-role",
          data("is-admin") := currentUser.admin.toString,
          data("can-see-edit-user-page") := Authorization
            .canSeeEditUserPage(currentUserRights)
            .toString,
        ),
        currentUser.admin.some.filter(identity).map(_ => searchForm()),
        div(cls := "mdl-cell mdl-cell--12-col", id := "tabulator-users-table"),
        currentUser.admin.some
          .filter(identity)
          .map(_ => div(cls := "mdl-cell mdl-cell--12-col", id := "tabulator-groups-table")),
        currentUser.admin.some
          .filter(identity)
          .map(_ => views.addGroup.innerForm(currentUser, selectedArea)),
        div(
          cls := "mdl-cell",
          a(
            id := "users-download-btn-csv",
            href := "#",
            i(cls := "fas fa-download"),
            " Téléchargement au format CSV"
          )
        ),
        div(
          cls := "mdl-cell",
          a(
            id := "users-download-btn-xlsx",
            href := "#",
            i(cls := "fas fa-download"),
            " Téléchargement au format XLSX"
          )
        ),
      )
    )(
      views.helpers.head.publicScript("generated-js/xlsx.full.min.js")
    )

  def searchForm(): Tag =
    div(
      cls := "mdl-cell",
      label(
        `for` := "searchBox",
        "Rechercher : "
      ),
      input(
        `type` := "text",
        id := "searchBox",
        name := "searchBox"
      ),
    )

}
