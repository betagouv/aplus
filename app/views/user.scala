package views

import controllers.routes.UserController
import models.User
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import views.helpers.forms.CSRFInput

object user {

  def deleteUserModal(userWithoutApplication: User)(implicit request: RequestHeader): Tag =
    tag("dialog")(
      id := "dialog-delete-user",
      cls := "mdl-dialog",
      h4(
        cls := "mdl-dialog__title",
        "Êtes-vous certain de vouloir supprimer le compte de cet utilisateur ?"
      ),
      form(
        action := UserController.deleteUnusedUserById(userWithoutApplication.id).path,
        method := UserController.deleteUnusedUserById(userWithoutApplication.id).method,
        CSRFInput,
        div(
          cls := "mdl-dialog__actions",
          button(
            `type` := "submit",
            cls := "mdl-button mdl-button--raised mdl-button--colored",
            "Supprimer"
          ),
          button(
            id := "delete-user-modal-quit-button",
            `type` := "button",
            cls := "mdl-button mdl-button--raised",
            "Annuler"
          )
        )
      )
    )

}
