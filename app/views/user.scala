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

  def passwordActivationButton(user: User)(implicit request: RequestHeader): Tag =
    div(
      cls := "single--display-flex single--justify-content-flex-end single--width-100pc single--margin-8px",
      form(
        action := UserController.activateUserPassword(user.id).path,
        method := UserController.activateUserPassword(user.id).method,
        CSRFInput,
        div(
          cls := "mdl-dialog__actions",
          button(
            `type` := "submit",
            cls := "mdl-button mdl-button--raised" +
              (if (user.passwordActivated) "" else " mdl-color--red-500 mdl-color-text--white"),
            if (user.passwordActivated)
              "Désactiver le mot de passe"
            else
              "Activer le mot de passe"
          ),
        )
      )
    )

}
