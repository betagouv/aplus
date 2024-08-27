package views

import cats.syntax.all._
import constants.Constants
import controllers.routes.{Assets, HomeController, LoginController}
import helpers.forms.CSRFInput
import models.forms.{PasswordChange, PasswordCredentials}
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.i18n.MessagesProvider
import play.api.mvc.{Flash, RequestHeader}
import scalatags.Text.all._
import scalatags.Text.tags2

object password {

  def loginPage(
      passwordForm: Form[PasswordCredentials],
      successMessage: Option[String] = none,
      errorMessage: Option[String] = none,
  )(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash,
      request: RequestHeader,
      messages: MessagesProvider
  ): Tag =
    basePage(successMessage.toList, errorMessage.toList)(
      List(
        h1(cls := "mdl-typography--display-1", "Connexion"),
        form(
          action := LoginController.tryLoginByPassword.path,
          method := LoginController.tryLoginByPassword.method,
          cls := "mdl-cell mdl-cell--12-col mdl-grid mdl-grid--no-spacing single--display-flex single--flex-direction-column",
          CSRFInput,
          views.helpers.forms.simpleInput("Email", passwordForm("email")),
          views.helpers.forms.simpleInput("Mot de passe", passwordForm("password"), "password"),
          div(
            cls := "single--margin-bottom-16px single--margin-top-16px",
            "ou ",
            button(
              `type` := "submit",
              formaction := LoginController.login.path + "?nopassword",
              cls := "button-as-link",
              "m’envoyer un lien magique (connexion sans mot de passe)"
            ),
          ),
          button(
            `type` := "submit",
            cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--primary single--margin-top-10px aplus-color--blue mdl-color-text--white single--margin-bottom-16px",
            "Connexion"
          ),
          button(
            `type` := "submit",
            formaction := LoginController.sendPasswordRecoveryEmail.path,
            cls := "button-as-link",
            "J’ai perdu mon mot de passe"
          )
        )
      )
    )

  def recoveryPage(
      validToken: Option[String],
      passwordChangeForm: Form[PasswordChange],
      successMessage: Option[String] = none,
      errorMessage: Option[String] = none,
  )(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash,
      request: RequestHeader,
      messages: MessagesProvider
  ): Tag =
    validToken match {
      case None =>
        val error = "Lien de changement de mot de passe invalide."
        basePage(Nil, error :: Nil)(
          div(
            a(
              href := HomeController.index.url,
              "Retour sur la page principale"
            )
          )
        )
      case Some(token) =>
        val globalErrors: List[String] = passwordChangeForm.hasGlobalErrors.some
          .filter(identity)
          .toList
          .flatMap(_ => passwordChangeForm.globalErrors.map(_.format))
        basePage(successMessage.toList, errorMessage.toList ::: globalErrors)(
          List(
            h1(cls := "mdl-typography--display-1", "Modifier mon mot de passe"),
            form(
              action := LoginController.changePassword.path,
              method := LoginController.changePassword.method,
              cls := "mdl-cell mdl-cell--12-col mdl-grid mdl-grid--no-spacing single--display-flex single--flex-direction-column",
              autocomplete := "off",
              CSRFInput,
              input(
                `type` := "hidden",
                id := "token",
                name := "token",
                value := token,
              ),
              p(
                cls := "single--max-width-450px single--margin-bottom-32px single--margin-top-8px",
                "Recommandations : ",
                br,
                "- Utiliser un coffre-fort de mot de passe, par exemple Keepass, KeepassXC, etc.",
                br,
                "- Utiliser des chiffres et des caractères spéciaux* (les coffres-forts permettent la génération de mots de passe forts)",
              ),
              views.helpers.forms.simpleInput(
                "Mot de passe (8 caractères minimum)",
                passwordChangeForm("new-password"),
                "password"
              ),
              views.helpers.forms.simpleInput(
                "Confirmation du mot de passe",
                passwordChangeForm("password-confirmation"),
                "password"
              ),
              button(
                `type` := "submit",
                cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--primary single--margin-top-10px aplus-color--blue mdl-color-text--white single--margin-top-16px single--margin-bottom-16px",
                "Changer le mot de passe"
              ),
              p(
                cls := "single--max-width-450px single--margin-bottom-32px single--margin-top-8px",
                "* ",
                a(
                  href := "https://www.cnil.fr/fr/mots-de-passe-des-recommandations-de-securite-minimales-pour-les-entreprises-et-les-particuliers",
                  "Autres recommandations de la CNIL relatives aux mots de passe"
                )
              ),
            ),
          )
        )
    }

  private def basePage(
      successMessages: List[String],
      errorMessages: List[String],
  )(innerTags: Modifier)(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash,
  ): Tag =
    html(
      lang := "fr",
      views.helpers.head.main("Connexion par mot de passe"),
      body(
        cls := "single--height-100pc",
        div(
          cls := "mdl-layout mdl-js-layout",
          tags2.main(
            cls := "mdl-layout__content mdl-color--white",
            div(
              cls := "single--display-flex single--align-items-center single--justify-content-center single--height-100pc",
              div(
                cls := "single--display-flex single--align-items-center single--flex-direction-column single--margin-16px",
                div(
                  cls := "single--display-flex single--justify-content-center single--margin-bottom-24px",
                  a(
                    href := HomeController.index.url,
                    img(
                      cls := "logo-standalone",
                      src := Assets.versioned("images/logo_full_500x397.png").url
                    )
                  )
                ),
                (successMessages ::: flash.get(helpers.forms.flashSuccessKey).toList).map(message =>
                  div(
                    cls := "notification notification--success single--max-width-400px",
                    message
                  )
                ),
                (errorMessages ::: flash.get(helpers.forms.flashErrorKey).toList).map(message =>
                  div(
                    cls := "notification notification--error single--max-width-400px",
                    message
                  )
                ),
                innerTags
              )
            )
          )
        ),
        views.helpers.head.bottomScripts
      )
    )

}
