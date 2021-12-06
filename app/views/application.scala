package views

import cats.syntax.all._
import controllers.routes.ApplicationController
import helpers.forms.CSRFInput
import java.util.UUID
import models.{Application, Area, Authorization, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import serializers.Keys
import views.helpers.common.webJarImg

object application {

  def closeApplicationModal(
      applicationId: UUID
  )(implicit webJarsUtil: WebJarsUtil, request: RequestHeader): Tag =
    tag("dialog")(
      cls := "mdl-dialog",
      id := "dialog-terminate",
      h4(
        cls := "mdl-dialog__title",
        "Est-ce que la réponse vous semble utile pour l'usager ?"
      ),
      form(
        action := ApplicationController.terminate(applicationId).path,
        method := ApplicationController.terminate(applicationId).method,
        CSRFInput,
        div(
          cls := "mdl-dialog__content",
          div(
            cls := "inputs--row",
            input(
              id := "yes",
              cls := "input--sweet",
              `type` := "radio",
              name := "usefulness",
              value := "Oui"
            ),
            label(
              `for` := "yes",
              webJarImg("1f600.svg")(cls := "input__icon", "Oui"),
            ),
            input(
              id := "neutral",
              cls := "input--sweet",
              `type` := "radio",
              name := "usefulness",
              value := "Je ne sais pas"
            ),
            label(
              `for` := "neutral",
              webJarImg("1f610.svg")(cls := "input__icon"),
              span(style := "width: 100%", "Je ne sais pas")
            ),
            input(
              id := "no",
              cls := "input--sweet",
              `type` := "radio",
              name := "usefulness",
              value := "Non"
            ),
            label(
              `for` := "no",
              webJarImg("1f61e.svg")(cls := "input__icon", "Non"),
            )
          ),
          br,
          b("Vous devez sélectionner une réponse pour archiver la demande.")
        ),
        div(
          cls := "mdl-dialog__actions",
          button(
            id := "close-dialog-quit",
            `type` := "button",
            cls := "mdl-button mdl-button--raised",
            "Quitter"
          ),
          button(
            `type` := "submit",
            disabled := "disabled",
            cls := "mdl-button mdl-button--raised mdl-button--colored",
            "Archiver"
          )
        )
      )
    )

  def reopenButton(applicationId: UUID)(implicit request: RequestHeader): Tag =
    div(
      cls := "mdl-cell mdl-cell--3-col mdl-cell--9-offset-desktop mdl-cell--12-col-phone",
      form(
        action := ApplicationController.reopen(applicationId).path,
        method := ApplicationController.reopen(applicationId).method,
        CSRFInput,
        button(
          cls := "mdl-button mdl-button--raised mdl-button--primary mdl-js-button do-not-print single--width-100pc",
          "Réouvrir l’échange"
        )
      )
    )

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
                groupsWithUsersThatCanBeInvited.sortBy { case (group, _) => group.name }.flatMap {
                  case (group, users) =>
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
