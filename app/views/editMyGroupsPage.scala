package views

import cats.syntax.all._
import controllers.routes.UserController
import helpers.forms.CSRFInput
import java.util.UUID
import models.{Application, Authorization, User}
import play.api.mvc.RequestHeader
import scalatags.Text.all._

object editMyGroupsPage {

  def userLine(
      user: User,
      groupId: UUID,
      applications: List[Application],
      currentUser: User,
      currentUserRights: Authorization.UserRights
  )(implicit request: RequestHeader): Tag =
    tr(
      cls := "no-hover td--clear-border",
      td(
        cls := "mdl-data-table__cell--non-numeric" +
          (if (user.disabled) " text--strikethrough mdl-color-text--grey-600" else ""),
        (
          if (Authorization.canSeeEditUserPage(currentUserRights))
            a(href := UserController.editUser(user.id).url, user.name)
          else
            span(
              cls := (if (user.disabled) "mdl-color-text--grey-600" else "application__name"),
              user.name
            )
        ),
        br,
        span(
          cls := (if (user.disabled) "mdl-color-text--grey-600" else "application__subject"),
          user.qualite
        )
      ),
      td(
        cls := "mdl-data-table__cell--non-numeric" +
          (if (user.disabled) " text--strikethrough mdl-color-text--grey-600" else ""),
        user.email
      ),
      td(
        cls := "mdl-data-table__cell--non-numeric mdl-data-table__cell--content-size",
        userRoleTags(user)
      ),
      td(
        cls := "mdl-data-table__cell--non-numeric mdl-data-table__cell--content-size",
        div(
          cls := "vertical-align--middle",
          i(cls := "material-icons icon--light", "chat_bubble"),
          span(
            cls := "application__anwsers",
            s"${applications.count(_.creatorUserId === user.id)} demandes"
          ),
          br,
          i(cls := "material-icons icon--light", "question_answer"),
          span(
            cls := "application__anwsers",
            s"${applications.count(_.invitedUsers.contains(user.id))} sollicitations"
          )
        )
      ),
      td(
        cls := "remove-link-panel",
        lineActionButton(user, groupId, currentUser, currentUserRights)
      )
    )

  private def userRoleTags(user: User): Modifier =
    if (user.disabled)
      span(cls := "tag mdl-color--grey-400 mdl-color-text--black", "Désactivé")
    else
      frag(
        user.groupAdmin.some
          .filter(identity)
          .map(_ => span(cls := "tag tag--responsable", "Responsable")),
        " ",
        user.admin.some.filter(identity).map(_ => span(cls := "tag tag--admin", "Admin")),
        " ",
        user.instructor.some
          .filter(identity)
          .map(_ => span(cls := "tag tag--instructor", "Instructeur")),
        " ",
        user.helper.some.filter(identity).map(_ => span(cls := "tag tag--aidant", "Aidant")),
      )

  private def lineActionButton(
      user: User,
      groupId: UUID,
      currentUser: User,
      currentUserRights: Authorization.UserRights
  )(implicit request: RequestHeader): Modifier =
    if (user.id =!= currentUser.id) {
      if (user.disabled && Authorization.canEnableOtherUser(user)(currentUserRights))
        form(
          action := UserController.enableUser(user.id).path,
          method := UserController.enableUser(user.id).method,
          CSRFInput,
          button(
            `type` := "submit",
            cls := "remove-link",
            "Réactiver le compte"
          )
        )
      else if (
        user.groupIds.toSet.size === 1 &&
        Authorization.canAddOrRemoveOtherUser(groupId)(currentUserRights)
      )
        form(
          action := UserController.removeFromGroup(user.id, groupId).path,
          method := UserController.removeFromGroup(user.id, groupId).method,
          CSRFInput,
          button(
            `type` := "submit",
            cls := "remove-link",
            "Désactiver le compte"
          )
        )
      else if (
        user.groupIds.toSet.size > 1 &&
        Authorization.canAddOrRemoveOtherUser(groupId)(currentUserRights)
      )
        button(
          cls := "remove-link remove-user-from-group-button",
          data("user-id") := user.id.toString,
          data("group-id") := groupId.toString,
          "Retirer du groupe"
        )
      else
        ()
    } else ()

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
