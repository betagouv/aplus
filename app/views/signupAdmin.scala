package views

import helper.TwirlImports.toHtml
import java.util.UUID
import models.{Authorization, Error, SignupRequest, User}
import models.formModels.AddSignupsFormData
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.mvc.{Flash, RequestHeader}
import play.twirl.api.Html
import scalatags.Text.all._

object signupAdmin {

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      signups: List[(SignupRequest, Option[UUID])],
      signupForm: Form[AddSignupsFormData]
  )(implicit webJarsUtil: WebJarsUtil, flash: Flash, request: RequestHeader): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)("Préinscription d’aidants")(
      views.helpers.head.publicCss("stylesheets/newForm.css")
    )(inner(signups))(Nil)

  def errorMessage(
      existingSignups: List[SignupRequest],
      existingUsers: List[User],
      miscErrors: List[(SignupRequest, Error)]
  ): Tag =
    div(
      cls := "single--display-flex single--width-100pc single--flex-wrap-wrap",
      miscErrors match {
        case Nil => ()
        case errors =>
          div(
            cls := "single--display-flex single--margin-bottom-16px single--width-100pc single--flex-wrap-wrap",
            div(
              cls := "single--width-100pc",
              b("Des erreurs de base de donnée se sont produites pour les emails suivants : ")
            ),
            // Note: this list is intended for quick copy-paste, descriptions are below
            frag(
              errors.map { case (request, _) =>
                div(
                  cls := "single--width-100pc",
                  request.email
                )
              }
            ),
            div(
              cls := "single--width-100pc",
              b("Liste des erreurs : "),
            ),
            frag(
              errors.map { case (request, error) =>
                div(
                  cls := "single--width-100pc",
                  request.email,
                  " - ",
                  error.eventType.code,
                  " - ",
                  error.description,
                  error.underlyingException.map(" - " + _)
                )
              }
            )
          )
      },
      existingSignups match {
        case Nil => ()
        case signups =>
          div(
            cls := "single--display-flex single--margin-bottom-16px single--width-100pc single--flex-wrap-wrap",
            div(
              cls := "single--width-100pc",
              b("Préinscriptions déjà existantes : ")
            ),
            frag(
              signups.map(signup =>
                div(
                  cls := "single--width-100pc",
                  signup.email
                )
              )
            )
          )
      },
      existingUsers match {
        case Nil => ()
        case users =>
          div(
            cls := "single--display-flex single--margin-bottom-16px single--width-100pc single--flex-wrap-wrap",
            div(
              cls := "single--width-100pc",
              b("Utilisateurs sont déjà présents : "),
            ),
            frag(
              users.map(user =>
                div(
                  cls := "single--width-100pc",
                  a(
                    href := controllers.routes.UserController.editUser(user.id).url,
                    user.email,
                    target := "_blank",
                    rel := "noopener"
                  )
                )
              )
            )
          )
      },
    )

  def successMessage(signups: List[SignupRequest]): Tag =
    div(
      cls := "single--display-flex single--width-100pc single--flex-wrap-wrap",
      div(
        cls := "single--display-flex single--margin-bottom-16px single--flex-wrap-wrap",
        div(
          cls := "single--width-100pc",
          b("Préinscriptions réussies : ")
        ),
        frag(
          signups.map(signup =>
            div(
              cls := "single--width-100pc",
              signup.email
            )
          )
        )
      )
    )

  private def inner(signups: List[(SignupRequest, Option[UUID])])(implicit request: RequestHeader) =
    div(
      cls := "mdl-cell mdl-cell--12-col",
      h4("Préinscrire des utilisateurs"),
      div(
        cls := "mdl-grid--no-spacing",
        form(
          action := controllers.routes.SignupController.addSignupRequests().path,
          method := controllers.routes.SignupController.addSignupRequests().method,
          cls := "mdl-grid--no-spacing",
          views.helpers.forms.CSRFInput,
          div(
            cls := "mdl-cell mdl-cell--12-col",
            div(
              cls := "single--display-flex single--align-items-center",
              div(
                cls := "single--margin-right-8px single--margin-bottom-8px mdl-color-text--red-A700",
                i(cls := "icon material-icons", "warning")
              ),
              p(
                cls := " mdl-color-text--red-A700",
                "Ne marche que pour des aidants"
              )
            )
          ),
          div(
            cls := "mdl-cell mdl-cell--12-col",
            div(
              cls := "single--display-flex single--align-items-center",
              div(
                cls := "single--margin-right-8px single--margin-bottom-8px mdl-color-text--red-A700",
                i(cls := "icon material-icons", "warning")
              ),
              p(
                cls := " mdl-color-text--red-A700",
                "Il faut créer les groupes avant d'ajouter les emails"
              )
            )
          ),
          div(
            cls := "mdl-cell mdl-cell--12-col",
            div(
              cls := "mdl-textfield mdl-js-textfield",
              label(
                `for` := "emails",
                "Liste d’emails séparés par des retours à la ligne"
              ),
              textarea(
                cls := "mdl-textfield__input",
                `type` := "text",
                rows := "3",
                id := "emails",
                name := "emails"
              ),
            )
          ),
          div(
            cls := "mdl-cell mdl-cell--12-col",
            button(
              cls := "mdl-button mdl-js-button mdl-button--raised",
              `type` := "submit",
              "Ajouter"
            )
          )
        ),
      ),
      h4("Liste des préinscriptions"),
      table(
        cls := "mdl-data-table mdl-js-data-table mdl-shadow--2dp single--background-color-white-important",
        thead(
          tr(
            th(cls := "mdl-data-table__cell--non-numeric aplus-color-text--black", "Email"),
            th(cls := "mdl-data-table__cell--non-numeric aplus-color-text--black", "Date"),
            th(cls := "mdl-data-table__cell--non-numeric aplus-color-text--black", "Créateur")
          )
        ),
        tbody(
          frag(
            signups.filter(_._2.isEmpty).map { case (signup, _) =>
              tr(
                td(cls := "mdl-data-table__cell--non-numeric", signup.email),
                td(signup.requestDate.toString),
                td(
                  cls := "mdl-data-table__cell--non-numeric",
                  a(
                    href := controllers.routes.UserController.editUser(signup.invitingUserId).url,
                    signup.invitingUserId.toString,
                    target := "_blank",
                    rel := "noopener"
                  )
                )
              )
            }
          )
        )
      )
    )

}
