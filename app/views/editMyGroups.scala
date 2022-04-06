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
      applications: List[Application],
      addRedirectQueryParam: String => String
  )(implicit
      flash: Flash,
      messagesProvider: MessagesProvider,
      request: RequestHeader,
      webJarsUtil: WebJarsUtil,
      mainInfos: MainInfos
  ): Html = {
    // Precompute here to speedup page rendering
    val (creationsCount, invitationsCount) = creationsAndInvitationsCounts(applications)
    val groupsWithTheirUsers: List[(UserGroup, List[User])] =
      for {
        userGroup <- userGroups.sortBy(_.name)
        groupUsers = users.filter(_.groupIds.contains(userGroup.id))
      } yield (userGroup, groupUsers)
    val dialogs =
      for {
        groupAndUsers <- groupsWithTheirUsers
        (group, users) = groupAndUsers
        user <- users
      } yield removeUserFromGroupDialog(user, group.id, addRedirectQueryParam)
    val blocks =
      for {
        groupAndUsers <- groupsWithTheirUsers
        (group, users) = groupAndUsers
      } yield groupBlock(
        group,
        users,
        creationsCount,
        invitationsCount,
        addUserForm,
        addRedirectQueryParam,
        currentUser,
        currentUserRights
      )

    views.html.main(currentUser, currentUserRights)("Mes groupes")(
      views.helpers.head.publicCss("stylesheets/newForm.css")
    )(
      frag(dialogs ::: blocks)
    )(Nil)
  }

  def creationsAndInvitationsCounts(
      applications: List[Application]
  ): (Map[UUID, Int], Map[UUID, Int]) = {
    val userCreations = scala.collection.mutable.HashMap.empty[UUID, Int]
    val userInvitations = scala.collection.mutable.HashMap.empty[UUID, Int]
    applications.foreach { application =>
      userCreations.updateWith(application.creatorUserId)(_.map(_ + 1).getOrElse(1).some)
      application.invitedUsers.foreach { case (userId, _) =>
        userInvitations.updateWith(userId)(_.map(_ + 1).getOrElse(1).some)
      }
    }
    (userCreations.toMap, userInvitations.toMap)
  }

  def groupBlock(
      group: UserGroup,
      users: List[User],
      applicationCreationsCount: Map[UUID, Int],
      applicationInvitationsCount: Map[UUID, Int],
      addUserForm: Form[AddUserToGroupFormData],
      addRedirectQueryParam: String => String,
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
          .map(user =>
            userLine(
              user,
              group.id,
              applicationCreationsCount,
              applicationInvitationsCount,
              addRedirectQueryParam,
              currentUser,
              currentUserRights
            )
          )
      ),
      (
        if (Authorization.canEditGroup(group)(currentUserRights)) {
          div(
            cls := "single--margin-top-24px",
            createAccountBlock(group, currentUser, currentUserRights)
          )
        } else ()
      ),
      div(
        cls := "single--margin-top-24px single--margin-bottom--24px",
        div(
          form(
            action := addRedirectQueryParam(GroupController.addToGroup(group.id).path),
            method := GroupController.addToGroup(group.id).method,
            CSRFInput,
            if (addUserForm.hasGlobalErrors) {
              div(cls := "global-errors", addUserForm.globalErrors.map(_.format).mkString(", "))
            } else (),
            div(cls := "sub-header", "Ajouter un membre existant au groupe"),
            div(
              cls := "single--margin-left-24px",
              "Un membre est un agent qui dispose déjà d’un compte Administration+"
            ),
            div(
              cls := "add-new-user-panel single--display-flex single--margin-top-16px", {
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
      ),
    )

  def createAccountBlock(
      group: UserGroup,
      currentUser: User,
      currentUserRights: Authorization.UserRights
  )(implicit request: RequestHeader): Tag =
    div(
      form(
        action := UserController.add(group.id).path,
        method := UserController.add(group.id).method,
        CSRFInput,
        div(cls := "sub-header", "Créer des nouveaux comptes dans ce groupe"),
        div(
          cls := "add-new-user-panel single--display-flex",
          div(
            cls := "mdl-textfield mdl-js-textfield mdl-textfield--floating-label single--max-width-160px",
            input(
              cls := "mdl-textfield__input",
              `type` := "text",
              pattern := """-?[0-9]*(\.[0-9]+)?""",
              id := "rows",
              name := "rows",
              value := "1"
            ),
            label(
              cls := "mdl-textfield__label",
              `for` := "rows",
              "Nombre de comptes à créer"
            ),
            span(cls := "mdl-textfield__error", "Ce n’est pas un nombre")
          ),
          button(
            cls := "single--margin-left-24px mdl-button mdl-js-button mdl-button--raised",
            `type` := "submit",
            "Créer des comptes dans ce groupe"
          )
        )
      )
    )

  def userLine(
      user: User,
      groupId: UUID,
      applicationCreationsCount: Map[UUID, Int],
      applicationInvitationsCount: Map[UUID, Int],
      addRedirectQueryParam: String => String,
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
            frag(
              a(href := UserController.editUser(user.id).url, user.name),
              " ",
              a(
                href := UserController.editUser(user.id).url,
                target := "_blank",
                i(cls := "fas fa-arrow-up-right-from-square")
              )
            )
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
            s"${applicationCreationsCount.getOrElse(user.id, 0)} demandes"
          ),
          br,
          i(cls := "material-icons icon--light", "question_answer"),
          span(
            cls := "application__anwsers",
            s"${applicationInvitationsCount.getOrElse(user.id, 0)} sollicitations"
          )
        )
      ),
      td(
        cls := "remove-link-panel",
        lineActionButton(user, groupId, addRedirectQueryParam, currentUser, currentUserRights)
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
      )

  private def lineActionButton(
      user: User,
      groupId: UUID,
      addRedirectQueryParam: String => String,
      currentUser: User,
      currentUserRights: Authorization.UserRights
  )(implicit request: RequestHeader): Modifier =
    if (user.id =!= currentUser.id) {
      if (user.disabled && Authorization.canEnableOtherUser(user)(currentUserRights))
        form(
          action := addRedirectQueryParam(GroupController.enableUser(user.id).path),
          method := GroupController.enableUser(user.id).method,
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
          action := addRedirectQueryParam(GroupController.removeFromGroup(user.id, groupId).path),
          method := GroupController.removeFromGroup(user.id, groupId).method,
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

  /** Important note: modals will pop up relative to the parent node in Firefox. This means we need
    * to put them as high as possible in the DOM.
    */
  def removeUserFromGroupDialog(
      otherUser: User,
      groupId: UUID,
      addRedirectQueryParam: String => String
  )(implicit
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
        action := addRedirectQueryParam(
          GroupController.removeFromGroup(otherUser.id, groupId).path
        ),
        method := GroupController.removeFromGroup(otherUser.id, groupId).method,
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
