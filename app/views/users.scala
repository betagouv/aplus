package views

import cats.syntax.all._
import controllers.routes.UserController
import helper.MiscHelpers.intersperseList
import helper.TwirlImports.toHtml
import models.{Area, Authorization, User}
import models.formModels.AddUsersFormData
import org.webjars.play.WebJarsUtil
import play.api.data.Form
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

  def addInstructorsConfirmationMessage(
      form: Form[AddUsersFormData],
      numberOfOldApplications: Int
  ): Tag =
    div(
      cls := "single--max-width-800px single--padding-left-16px single--padding-right-16px single--padding-bottom-16px single--align-self-center single--display-flex single--flex-direction-column single--background-color-white",
      h4(cls := "typography--text-align-center", "Confirmation"),
      div(
        cls := "single--margin-bottom-16px",
        p(
          "Attribuer le rôle d’instructeur aux agents suivants leur donnera accès au contenu ",
          em("(données personnelles des usagers, pièces-jointes)"),
          " des ",
          b(numberOfOldApplications.toString),
          " demandes en cours d’instruction du groupe ainsi qu’aux demandes à venir :",
        ),
        form.value.map(formData =>
          ul(
            frag(
              formData.users
                .filter(_.instructor)
                .map(newUser =>
                  li(
                    newUser.name,
                    " ",
                    newUser.lastName,
                    " ",
                    newUser.firstName,
                    " (",
                    newUser.email,
                    ")",
                  )
                )
            )
          )
        ),
        form.value.map { formData =>
          val nonInstructors = formData.users.filter(!_.instructor)
          if (nonInstructors.size <= 0)
            frag()
          else
            p(
              "Par ailleurs, ",
              (if (nonInstructors.size === 1) "le compte"
               else "les comptes"),
              " ",
              intersperseList[Frag](nonInstructors.map(newUser => newUser.name), ", "),
              " ",
              (if (nonInstructors.size === 1) "sera créé"
               else "seront créés"),
              " sans rôle d’instructeur. Pour effectuer des corrections, veuillez utiliser le bouton page précédente."
            )
        }
      ),
      div(
        cls := "single--margin-bottom-8px",
        label(
          `for` := "checkbox-instructors-confirmation",
          cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect single--margin-bottom-8px",
          input(
            id := s"checkbox-instructors-confirmation",
            cls := "mdl-checkbox__input",
            `type` := "checkbox",
            name := "confirmInstructors",
            value := "true",
          ),
          span(
            cls := "mdl-checkbox__label",
            "Je confirme que les personnes citées ci-dessus sont habilitées à recevoir le rôle d’instructeur sur Administration+"
          )
        ),
      ),
      button(
        cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored single--margin-top-8px single--margin-bottom-8px single--width-300px single--align-self-center",
        "Créer les comptes"
      )
    )

}
