package views

import controllers.routes.UserController
import helpers.forms.CSRFInput
import java.util.UUID
import models.User
import play.api.mvc.RequestHeader
import scalatags.Text.all._

object editMyGroupsPage {

  private def dialogId(groupId: UUID, userId: UUID): String =
    s"remove-user-from-group-dialog-$groupId-$userId"

  /** Important note: modals will pop up relative to the parent node in Firefox.
    *                 This means we need to put them as high as possible in the DOM.
    */
  def removeUserFromGroupDialog(otherUser: User, groupId: UUID)(implicit request: RequestHeader) =
    tag("dialog")(
      id := dialogId(groupId, otherUser.id),
      cls := "mdl-dialog mdl-dialog-fix",
      h4(
        cls := "mdl-dialog__title",
        "Êtes-vous sûr de vouloir retirer ",
        otherUser.name,
        " du groupe ?"
      ),
      form(
        action := UserController.removeFromGroup(otherUser.id, groupId).path,
        method := UserController.removeFromGroup(otherUser.id, groupId).method,
        CSRFInput,
        div(
          cls := "mdl-dialog__content",
        ),
        div(
          cls := "mdl-dialog__actions",
          button(
            `type` := "button",
            cls := "mdl-button mdl-button--raised close-modal",
            "Quitter"
          ),
          button(
            `type` := "submit",
            cls := "mdl-button mdl-button--raised mdl-button--colored",
            "Retirer"
          )
        )
      )
    )

}
