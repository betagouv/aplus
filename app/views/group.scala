package views

import controllers.routes.GroupController
import models.UserGroup
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import views.helpers.forms.CSRFInput

object group {

  def deleteGroupModal(emptyGroup: UserGroup)(implicit request: RequestHeader): Tag =
    tag("dialog")(
      id := "dialog-delete-group",
      cls := "mdl-dialog dialog--50percent-width",
      h4(
        cls := "mdl-dialog__title",
        "Veuillez confirmer la suppression du groupe."
      ),
      form(
        action := GroupController.deleteUnusedGroupById(emptyGroup.id).path,
        method := GroupController.deleteUnusedGroupById(emptyGroup.id).method,
        CSRFInput,
        div(
          cls := "mdl-dialog__actions",
          button(
            `type` := "submit",
            cls := "mdl-button mdl-button--raised mdl-button--colored",
            "Supprimer"
          ),
          button(
            id := "dialog-delete-group-cancel",
            `type` := "button",
            cls := "mdl-button mdl-button--raised",
            "Annuler"
          )
        )
      )
    )

}
