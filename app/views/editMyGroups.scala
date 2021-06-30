package views

import cats.syntax.all._
import controllers.routes.{GroupController, UserController}
import helpers.forms.CSRFInput
import helper.TwirlImports.toHtml
import java.util.UUID
import models.{Application, Authorization, User, UserGroup}
import models.formModels.AddUserToGroupFormData
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.i18n.MessagesProvider
import play.api.mvc.{Flash, RequestHeader}
import play.twirl.api.Html
import scalatags.Text.all._

object editMyGroups {

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      addUserForm: Form[AddUserToGroupFormData],
      userGroups: List[UserGroup],
      users: List[User],
      applications: List[Application]
  )(implicit
      flash: Flash,
      messagesProvider: MessagesProvider,
      request: RequestHeader,
      webJarsUtil: WebJarsUtil,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights)("Mes groupes")(
      views.helpers.head.publicCss("stylesheets/newForm.css")
    )(
      frag(
        (for {
          userGroup <- userGroups.sortBy(_.name)
          user <- users
        } yield removeUserFromGroupDialog(user, userGroup.id)) ::: (
          for {
            userGroup <- userGroups.sortBy(_.name)
            groupUsers = users.filter(_.groupIds.contains(userGroup.id))
          } yield groupBlock(
            userGroup,
            groupUsers,
            applications,
            addUserForm,
            currentUser,
            currentUserRights
          )
        )
      )
    )(Nil)

  def groupBlock(
      group: UserGroup,
      users: List[User],
      applications: List[Application],
      addUserForm: Form[AddUserToGroupFormData],
      currentUser: User,
      currentUserRights: Authorization.UserRights
  )(implicit messagesProvider: MessagesProvider, request: RequestHeader): Tag =
    div(
      cls := "group-container mdl-cell mdl-cell--12-col mdl-shadow--2dp mdl-color--white",
      id := s"group-${group.id}",
      div(
        div(
          cls := "header",
          if (Authorization.canEditGroup(group)(currentUserRights)) {
            a(href := GroupController.editGroup(group.id).url, group.name)
          } else {
            group.name
          },
          span(cls := "text--font-size-medium", group.description)
        )
      ),
      table(
        cls := "group-table mdl-data-table mdl-js-data-table",
        div(
          cls := "sub-header",
          "Liste des membres du groupe"
        ),
        users
          .sortBy(user => (user.disabled, user.name))
          .map(user => userLine(user, group.id, applications, currentUser, currentUserRights))
      ),
      div(
        cls := "single--margin-top-24px single--margin-bottom--24px",
        div(
          form(
            action := UserController.addToGroup(group.id).path,
            method := UserController.addToGroup(group.id).method,
            CSRFInput,
            if (addUserForm.hasGlobalErrors) {
              div(cls := "global-errors", addUserForm.globalErrors.map(_.format).mkString(", "))
            } else (),
            div(cls := "sub-header", "Ajouter un membre au groupe"),
            div(
              cls := "add-new-user-panel single--display-flex", {
                val field = addUserForm("email")
                div(
                  div(
                    cls := "single--margin-bottom--8px",
                    "Adresse e-mail ",
                    span(cls := "mdl-color-text--red-500", "*"),
                    " :"
                  ),
                  div(
                    views.helpers.forms.minimalTextInput(
                      field,
                      input(
                        cls := "mdl-textfield__input",
                        `type` := "text",
                        name := field.name,
                        id := field.id,
                        field.value.map(value := _),
                        //label := "Saisir l’adresse e-mail"
                      ),
                      field.id,
                      fieldLabel = Some("Saisir l’adresse e-mail")
                    )
                  )
                )
              },
              button(
                cls := "single--margin-left-24px mdl-button mdl-js-button mdl-button--raised",
                `type` := "submit",
                "Ajouter au groupe"
              )
            )
          )
        )
      )
    )

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
  def removeUserFromGroupDialog(otherUser: User, groupId: UUID)(implicit
      request: RequestHeader
  ): Tag =
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
