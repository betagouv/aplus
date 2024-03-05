package views

import cats.syntax.all._
import controllers.routes.GroupController
import models.{Area, Organisation, User}
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import views.helpers.forms.CSRFInput

object addGroup {

  def innerForm(currentUser: User, selectedArea: Area)(implicit request: RequestHeader): Tag =
    div(
      cls := "mdl-cell mdl-cell--12-col mdl-grid",
      h4(
        cls := "mdl-cell mdl-cell--12-col",
        "Ajouter un groupe"
      ),
      br,
      form(
        action := GroupController.addGroup.path,
        method := GroupController.addGroup.method,
        cls := "mdl-cell mdl-cell--9-col",
        CSRFInput,
        div(
          cls := "mdl-cell mdl-cell--12-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label",
          input(
            cls := "mdl-textfield__input",
            `type` := "text",
            id := "name",
            name := "name",
            maxlength := models.UserGroup.nameMaxLength
          ),
          label(
            cls := "mdl-textfield__label",
            `for` := "name",
            "Nom du groupe"
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--12-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label",
          input(
            cls := "mdl-textfield__input",
            `type` := "text",
            id := "description",
            name := "description"
          ),
          label(cls := "mdl-textfield__label", `for` := "description", "Description du groupe")
        ),
        div(
          cls := "mdl-cell mdl-cell--12-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label",
          input(
            cls := "mdl-textfield__input",
            `type` := "text",
            id := "email",
            name := "email",
            maxlength := "200"
          ),
          label(
            cls := "mdl-textfield__label",
            `for` := "email",
            "Email du groupe (BAL générique pour inscription et notification, champ facultatif)"
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--12-col",
          select(
            id := "organisation",
            name := "organisation",
            option(
              selected,
              value := "",
              "Organisme non-référencé"
            ),
            Organisation.all.map(organisation =>
              option(
                value := organisation.id.id,
                s"${organisation.shortName} : ${organisation.name}"
              )
            )
          )
        ),
        div(
          cls := "mdl-cell mdl-cell--12-col",
          select(
            cls := "use-slimselect",
            id := "area-ids",
            name := "area-ids[]",
            multiple,
            size := "5",
            currentUser.areas
              .flatMap(Area.fromId)
              .map(area =>
                option(
                  value := area.id.toString,
                  (selectedArea.id === area.id).some.filter(identity).map(_ => selected),
                  area.name
                )
              )
          )
        ),
        button(
          cls := "mdl-button mdl-js-button mdl-button--raised",
          `type` := "submit",
          "Ajouter le groupe"
        )
      )
    )

}
