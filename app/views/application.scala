package views

import cats.syntax.all._
import controllers.routes.ApplicationController
import models.{Application, Area, Authorization, User, UserGroup}
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import serializers.Keys

object application {

  def inviteForm(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      groupsWithUsersThatCanBeInvited: List[(UserGroup, List[User])],
      groupsThatCanBeInvited: List[UserGroup],
      application: Application,
      selectedArea: Area
  )(implicit request: RequestHeader): Tag =
    form(
      action := ApplicationController.invite(application.id).path,
      method := ApplicationController.invite(application.id).method,
      cls := "mdl-cell mdl-cell--12-col mdl-grid aplus-protected-form",
      input(
        `type` := "hidden",
        name := Keys.Application.areaId,
        readonly := true,
        value := selectedArea.id.toString
      ),
      div(
        "Territoire concerné : ",
        views.helpers
          .changeAreaSelect(selectedArea, Area.all, ApplicationController.show(application.id))
      ),
      views.helpers.forms.CSRFInput,
      groupsWithUsersThatCanBeInvited.nonEmpty.some
        .filter(identity)
        .map(_ =>
          fieldset(
            cls := "mdl-cell mdl-cell--12-col mdl-grid",
            legend(
              cls := "single--padding-top-16px single--padding-bottom-16px mdl-typography--title",
              "Inviter une autre personne sur la discussion"
            ),
            table(
              cls := "mdl-data-table mdl-js-data-table mdl-cell mdl-cell--12-col",
              style := "border: none;",
              thead(
                tr(
                  th(cls := "mdl-data-table__cell--non-numeric"),
                  th(cls := "mdl-data-table__cell--non-numeric", "Structure"),
                  th(cls := "mdl-data-table__cell--non-numeric", "Nom"),
                  th(cls := "mdl-data-table__cell--non-numeric", "Qualité")
                )
              ),
              tbody(
                groupsWithUsersThatCanBeInvited.sortBy(_._1.name).flatMap { case (group, users) =>
                  users
                    .sortBy(_.name)
                    .map(user =>
                      tr(
                        td(
                          label(
                            cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect mdl-js-ripple-effect--ignore-events",
                            input(
                              `type` := "checkbox",
                              cls := "mdl-checkbox__input",
                              name := "users[]",
                              value := user.id.toString
                            ),
                          )
                        ),
                        td(
                          cls := "mdl-data-table__cell--non-numeric",
                          group.name
                        ),
                        td(
                          cls := "mdl-data-table__cell--non-numeric",
                          user.name
                        ),
                        td(
                          cls := "mdl-data-table__cell--non-numeric",
                          user.qualite
                        )
                      )
                    )
                }
              )
            )
          )
        ),
      groupsThatCanBeInvited.nonEmpty.some
        .filter(identity)
        .map(_ => views.helpers.applications.inviteTargetGroups(groupsThatCanBeInvited)),
      div(
        cls := "mdl-textfield mdl-js-textfield mdl-textfield--floating-label mdl-cell mdl-cell--12-col",
        textarea(
          cls := "mdl-textfield__input",
          `type` := "text",
          rows := "5",
          id := "agents-invitation-message",
          style := "width: 100%;",
          name := "message"
        ),
        label(
          cls := "mdl-textfield__label",
          `for` := "agents-invitation-message",
          i(cls := "material-icons", style := "vertical-align: middle;", "message"),
          " Laisser ici un message pour l’invitation..."
        )
      ),
      (currentUser.instructor || currentUser.expert).some
        .filter(identity)
        .map(_ =>
          frag(
            div(
              id := "private-invitation",
              label(
                cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect mdl-js-ripple-effect--ignore-events vertical-align--middle",
                input(
                  `type` := "checkbox",
                  cls := "mdl-checkbox__input",
                  name := "privateToHelpers",
                  value := "true"
                ),
                span(cls := "mdl-checkbox__label"),
                "Restreindre le message d’invitation aux Agents Administration+ ",
                i(cls := "icon material-icons icon--light", "info")
              )
            ),
            div(
              cls := "mdl-tooltip",
              `for` := "private-invitation",
              "Le message d’invitation ne sera pas visible par l’aidant."
            )
          )
        ),
      br,
      button(
        id := "application-complete",
        cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored mdl-cell mdl-cell--12-col",
        "Inviter"
      )
    )

}
