package views

import cats.syntax.all._
import constants.Constants
import controllers.routes.{Assets, HomeController, LoginController}
import helpers.forms.CSRFInput
import models.forms.{PasswordChange, PasswordCredentials, PasswordRecovery}
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.i18n.MessagesProvider
import play.api.mvc.{Flash, RequestHeader}
import scalatags.Text.all._
import scalatags.Text.tags2

object password {

  def loginPage(
      passwordForm: Form[PasswordCredentials],
      successMessage: Option[(String, String)] = none,
      errorMessage: Option[(String, String)] = none,
  )(using RequestHeader): Tag =
    views.main.publicLayout(
      pageName = "Connexion par mot de passe",
      content = div(cls := "fr-grid-row fr-grid-row-gutters fr-grid-row--center")(
        div(cls := "fr-col-12 fr-col-md-8 fr-col-lg-6")(
          h1("Connexion à Administration+"),
          loginBlock(passwordForm, successMessage, errorMessage),
          p(cls := "fr-hr-or")("ou"),
          div(
            a(
              href := "/",
              "Revenir à la page d’accueil pour se connecter avec un lien à usage unique."
            )
          )
        )
      ),
      breadcrumbs = List(("Connexion par mot de passe", LoginController.passwordPage.url)),
      additionalHeadTags = frag(),
      additionalFooterTags = frag()
    )

  private def loginBlock(
      passwordForm: Form[PasswordCredentials],
      successMessage: Option[(String, String)],
      errorMessage: Option[(String, String)]
  )(using RequestHeader): Tag =
    div(
      form(
        id := "password-login",
        action := LoginController.tryLoginByPassword.path,
        method := LoginController.tryLoginByPassword.method,
      )(
        CSRFInput,
        fieldset(
          cls := "fr-fieldset",
          id := "password-login-fieldset",
          aria.labelledby := "password-login-fieldset-legend password-login-fieldset-messages"
        )(
          legend(
            cls := "fr-fieldset__legend",
            id := "password-login-fieldset-legend"
          )(
            h2("Se connecter avec son mot de passe")
          ),
          div(cls := "fr-fieldset__element")(
            fieldset(
              cls := "fr-fieldset",
              id := "password-login-credentials",
              aria.labelledby := "password-login-credentials-messages"
            )(
              div(cls := "fr-fieldset__element")(
                span(cls := "fr-hint-text")("Tous les champs sont obligatoires.")
              ),
              div(
                cls := "fr-messages-group",
                id := "password-login-credentials-messages",
                aria.live := "assertive"
              )(
                successMessage.map { case (title, description) =>
                  div(cls := "fr-alert fr-alert--success fr-mb-2w")(
                    h3(cls := "fr-alert__title")(title),
                    p(description)
                  )
                },
                errorMessage.map { case (title, description) =>
                  div(cls := "fr-alert fr-alert--error fr-mb-2w")(
                    h3(cls := "fr-alert__title")(title),
                    p(description)
                  )
                }
              ),
              div(cls := "fr-fieldset__element")(
                emailInput("password-login", passwordForm("email").value)
              ),
              div(cls := "fr-fieldset__element")(
                passwordInput(
                  "password-login",
                  "password",
                  "Mot de passe",
                  "current-password",
                  bottom = p(
                    a(
                      href := LoginController.passwordReinitializationEmailPage.path,
                      cls := "fr-link"
                    )(
                      "Mot de passe oublié ?"
                    )
                  )
                )
              )
            )
          ),
          div(cls := "fr-fieldset__element")(
            ul(cls := "fr-btns-group")(
              li(
                button(`type` := "submit", cls := "fr-mt-2v fr-btn")(
                  "Se connecter"
                )
              )
            )
          ),
          div(
            cls := "fr-messages-group",
            id := "password-login-fieldset-messages",
            aria.live := "assertive"
          )()
        )
      )
    )

  def reinitializationEmailPage(
      form: Form[PasswordRecovery],
      successMessage: Option[(String, String)] = none,
      errorMessage: Option[(String, String)] = none,
  )(using RequestHeader): Tag =
    views.main.publicLayout(
      pageName = "Mot de passe oublié",
      content = div(cls := "fr-grid-row fr-grid-row-gutters fr-grid-row--center")(
        div(cls := "fr-col-12 fr-col-md-8 fr-col-lg-6")(
          h1("Mot de passe oublié"),
          reinitializationEmailBlock(form, successMessage, errorMessage),
          div(
            a(
              href := "/",
              "Revenir à la page d’accueil pour se connecter avec un lien à usage unique."
            )
          ),
        )
      ),
      breadcrumbs = List(
        ("Connexion par mot de passe", LoginController.passwordPage.url),
        ("Mot de passe oublié", LoginController.passwordReinitializationEmailPage.url)
      ),
      additionalHeadTags = frag(),
      additionalFooterTags = frag()
    )

  private def reinitializationEmailBlock(
      passwordForm: Form[PasswordRecovery],
      successMessage: Option[(String, String)] = none,
      errorMessage: Option[(String, String)] = none,
  )(using RequestHeader): Tag =
    form(
      id := "password-reinitialization-email",
      action := LoginController.passwordReinitializationEmail.path,
      method := LoginController.passwordReinitializationEmail.method,
    )(
      CSRFInput,
      fieldset(
        cls := "fr-fieldset",
        id := "password-reinitialization-email-fieldset",
        aria.labelledby := "password-reinitialization-email-fieldset-legend password-reinitialization-email-fieldset-messages"
      )(
        legend(
          cls := "fr-fieldset__legend",
          id := "password-reinitialization-email-fieldset-legend"
        )(
          h2("Demander un lien de réinitialisation de mot de passe")
        ),
        div(cls := "fr-fieldset__element")(
          fieldset(
            cls := "fr-fieldset",
            id := "password-reinitialization-email-credentials",
            aria.labelledby := "password-reinitialization-email-credentials-messages"
          )(
            div(
              cls := "fr-messages-group",
              id := "password-reinitialization-email-credentials-messages",
              aria.live := "assertive"
            )(
              successMessage.map { case (title, description) =>
                div(cls := "fr-alert fr-alert--success fr-mb-2w")(
                  h3(cls := "fr-alert__title")(title),
                  p(description)
                )
              },
              errorMessage.map { case (title, description) =>
                div(cls := "fr-alert fr-alert--error fr-mb-2w")(
                  h3(cls := "fr-alert__title")(title),
                  p(description)
                )
              }
            ),
            if successMessage.isEmpty then
              div(cls := "fr-fieldset__element")(
                emailInput("password-reinitialization-email", passwordForm("email").value)
              )
            else frag(),
          )
        ),
        div(cls := "fr-fieldset__element")(
          ul(cls := "fr-btns-group")(
            li(
              if successMessage.isEmpty then
                button(`type` := "submit", cls := "fr-mt-2v fr-btn")(
                  "Envoyer un lien de réinitialisation"
                )
              else frag()
            )
          )
        ),
        div(
          cls := "fr-messages-group",
          id := "password-reinitialization-email-fieldset-messages",
          aria.live := "assertive"
        )(),
      )
    )

  def reinitializationPage(
      validToken: Option[String],
      passwordChangeForm: Form[PasswordChange],
      successMessage: Option[(String, String)] = none,
      errorMessage: Option[(String, String)] = none,
  )(using RequestHeader, MessagesProvider): Tag =
    views.main.publicLayout(
      pageName = "Réinitialiser mon mot de passe",
      content = div(cls := "fr-grid-row fr-grid-row-gutters fr-grid-row--center")(
        div(cls := "fr-col-12 fr-col-md-8 fr-col-lg-6")(
          h1("Réinitialiser mon mot de passe"),
          reinitializationBlock(validToken, passwordChangeForm, successMessage, errorMessage),
          div(
            a(
              href := "/",
              "Revenir à la page d’accueil pour se connecter avec un lien à usage unique."
            )
          ),
        )
      ),
      breadcrumbs = List(
        ("Réinitialiser mon mot de passe", LoginController.passwordReinitializationPage.url),
      ),
      additionalHeadTags = frag(),
      additionalFooterTags = frag()
    )

  private def reinitializationBlock(
      validToken: Option[String],
      passwordChangeForm: Form[PasswordChange],
      successMessage: Option[(String, String)],
      errorMessage: Option[(String, String)],
  )(using RequestHeader, MessagesProvider): Tag =
    validToken match {
      case None =>
        div(cls := "fr-alert fr-alert--error fr-mb-2w")(
          h2(cls := "fr-alert__title")("Lien de réinitialisation de mot de passe invalide"),
          p(
            "Le lien de réinitialisation de mot de passe n'est plus valide, vous pouvez en faire renvoyer un nouveau en ",
            a(
              href := LoginController.passwordReinitializationEmailPage.path,
              cls := "fr-link"
            )(
              "cliquant ici"
            ),
            "."
          )
        )
      case Some(token) =>
        val formErrors: List[(String, String)] =
          passwordChangeForm.errors.toList
            .map(field => ("Erreur dans le formulaire.", field.format))
            .distinct
        val errorMessages: List[(String, String)] = errorMessage.toList ::: formErrors
        form(
          id := "password-reinitialization",
          action := LoginController.passwordReinitialization.path,
          method := LoginController.passwordReinitialization.method,
        )(
          CSRFInput,
          input(
            `type` := "hidden",
            id := "token",
            name := "token",
            value := token,
          ),
          fieldset(
            cls := "fr-fieldset",
            id := "password-reinitialization-fieldset",
            aria.labelledby := "password-reinitialization-fieldset-messages"
          )(
            div(cls := "fr-fieldset__element")(
              fieldset(
                cls := "fr-fieldset",
                id := "password-reinitialization-credentials",
                aria.labelledby := "password-reinitialization-credentials-messages"
              )(
                div(cls := "fr-fieldset__element")(
                  span(cls := "fr-hint-text")("Tous les champs sont obligatoires.")
                ),
                div(
                  cls := "fr-messages-group",
                  id := "password-reinitialization-credentials-messages",
                  aria.live := "assertive"
                )(
                  successMessage.map { case (title, description) =>
                    div(cls := "fr-alert fr-alert--success fr-mb-2w")(
                      h3(cls := "fr-alert__title")(title),
                      p(description)
                    )
                  },
                  frag(errorMessages.map { case (title, description) =>
                    div(cls := "fr-alert fr-alert--error fr-mb-2w")(
                      h3(cls := "fr-alert__title")(title),
                      p(description)
                    )
                  })
                ),
                div(cls := "fr-fieldset__element")(
                  passwordInput(
                    "password-reinitialization",
                    "new-password",
                    "Nouveau mot de passe",
                    "new-password",
                    frag(
                      p(cls := "fr-message")("Votre mot de passe doit contenir :"),
                      p(cls := "fr-message fr-message--info")("12 caractères minimum"),
                      p(cls := "fr-message")(
                        "Il est de plus recommandé (mais non obligatoire) d’utiliser :",
                        br,
                        "- un coffre-fort de mot de passe, par exemple Keepass, KeepassXC, etc.",
                        br,
                        "- des chiffres et des caractères spéciaux (les coffres-forts permettent la génération de mots de passe forts)",
                      ),
                      p(cls := "fr-message")(
                        a(
                          href := "https://www.cnil.fr/fr/mots-de-passe-des-recommandations-de-securite-minimales-pour-les-entreprises-et-les-particuliers",
                          cls := "fr-link",
                          target := "_blank",
                          rel := "noopener",
                        )(
                          "Autres recommandations de la CNIL relatives aux mots de passe"
                        )
                      )
                    )
                  ),
                ),
                div(cls := "fr-fieldset__element")(
                  passwordInput(
                    "password-reinitialization",
                    "password-confirmation",
                    frag(
                      "Confirmation du mot de passe",
                      span(cls := "fr-hint-text")(
                        "Merci de réécrire votre mot de passe à l’identique"
                      )
                    ),
                    "new-password",
                  )
                )
              )
            ),
            div(cls := "fr-fieldset__element")(
              ul(cls := "fr-btns-group")(
                li(
                  button(`type` := "submit", cls := "fr-mt-2v fr-btn")(
                    "Changer le mot de passe"
                  )
                )
              )
            ),
            div(
              cls := "fr-messages-group",
              id := "password-reinitialization-fieldset-messages",
              aria.live := "assertive"
            )()
          )
        )
    }

  private def emailInput(
      prefix: String,
      inputValue: Option[String],
  ): Tag =
    div(cls := "fr-input-group")(
      label(cls := "fr-label", `for` := s"$prefix-email")(
        "Adresse électronique",
        span(cls := "fr-hint-text")(
          "Format attendu : nom@domaine.fr"
        )
      ),
      input(
        id := s"$prefix-email",
        `type` := "email",
        cls := "fr-input",
        name := "email",
        autocomplete := "email",
        aria.required := "true",
        aria.describedby := s"$prefix-email-messages",
        inputValue.map(value := _),
      ),
      div(
        cls := "fr-messages-group",
        id := s"$prefix-email-messages",
        aria.live := "assertive"
      )()
    )

  private def passwordInput(
      prefix: String,
      inputName: String,
      inputLabel: Frag,
      inputAutocomplete: String,
      messages: Frag = frag(),
      bottom: Frag = frag(),
  ): Tag =
    div(cls := "fr-password", id := s"$prefix-$inputName")(
      label(cls := "fr-label", `for` := s"$prefix-$inputName-input")(
        inputLabel
      ),
      div(cls := "fr-input-wrap")(
        input(
          id := s"$prefix-$inputName-input",
          `type` := "password",
          cls := "fr-password__input fr-input",
          name := inputName,
          autocomplete := inputAutocomplete,
          aria.describedby := s"$prefix-$inputName-input-messages",
          aria.required := "true",
        )
      ),
      div(
        cls := "fr-messages-group",
        id := s"$prefix-$inputName-input-messages",
        aria.live := "assertive"
      )(
        messages
      ),
      div(cls := "fr-password__checkbox fr-checkbox-group fr-checkbox-group--sm")(
        input(
          aria.label := "Afficher le mot de passe",
          id := s"$prefix-$inputName-show",
          `type` := "checkbox",
          aria.describedby := s"$prefix-$inputName-show-messages"
        ),
        label(
          cls := "fr-password__checkbox fr-label",
          `for` := s"$prefix-$inputName-show"
        )(
          "Afficher"
        ),
        div(
          cls := "fr-messages-group",
          id := s"$prefix-$inputName-show-messages",
          aria.live := "assertive"
        )()
      ),
      bottom
    )

}
