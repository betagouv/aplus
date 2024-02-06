package views

import cats.syntax.all._
import controllers.routes.{AccountCreationController, JavascriptController}
import java.util.UUID
import models.formModels.AccountCreationFormData
import models.{Area, Organisation, User, UserGroup}
import play.api.data.{Field, Form}
import play.api.i18n.MessagesProvider
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import views.helpers.forms.CSRFInput

object accountCreation {

  def formSentPage(): Tag =
    views.main.publicLayout(
      "Formulaire d’inscription",
      div(
        h3("Votre demande de création de compte a bien été prise en compte."),
        p("Vous serez notifié(e) par email dès qu'un responsable l’aura validée.")
      )
    )

  def userAccountCreatedPage(user: User)(implicit request: RequestHeader): Tag =
    views.main.layout(
      "Compte utilisateur créé",
      div(
        h3("Félicitations !"),
        p(user.firstName, " ", user.lastName, " vient d’être ajoutée à votre groupe")
      )
    )

  def rejectionPage()(implicit request: RequestHeader): Tag =
    views.main.layout(
      "Reject du formulaire d’inscription",
      div(
        h3("La demande d’inscription a bien été rejetée."),
        p("Vous serez notifié(e) par email dès qu'un responsable l’aura validée.")
      )
    )

  def accountTypePage()(implicit request: RequestHeader): Tag =
    views.main.publicLayout("Formulaire d’inscription", accountTypeForm())

  private def accountTypeForm()(implicit request: RequestHeader) =
    form(
      action := AccountCreationController.handleAccountTypeForm.path,
      method := AccountCreationController.handleAccountTypeForm.method,
      CSRFInput,
      fieldset(
        cls := "fr-fieldset",
        role := "group",
        aria.labelledby := "account-type-form-legend"
      )(
        legend(cls := "fr-fieldset__legend", id := "account-type-form-legend")(
          "Quel type de compte souhaitez-vous créer ?"
        ),
        div(cls := "fr-fieldset__element")(
          div(cls := "fr-radio-group")(
            input(
              `type` := "radio",
              id := "account-type-form-named",
              name := "isNamedAccount",
              value := "true"
            ),
            label(cls := "fr-label", `for` := "account-type-form-named")(
              "Compte nominatif"
            )
          )
        ),
        div(cls := "fr-fieldset__element")(
          div(cls := "fr-radio-group")(
            input(
              `type` := "radio",
              id := "account-type-form-shared",
              name := "isNamedAccount",
              value := "false"
            ),
            label(cls := "fr-label", `for` := "account-type-form-shared")(
              "Compte partagé"
            )
          )
        ),
        button(`type` := "submit", cls := "fr-btn")(
          "Continuer"
        )
      )
    )

  def formGlobalErrors(form: Form[_])(implicit messages: MessagesProvider): Frag =
    if (form.hasErrors)
      div(cls := "fr-alert fr-alert--error")(
        h3(cls := "fr-alert__title")("Erreur(s) dans le formulaire"),
        if (form.hasGlobalErrors)
          ul(
            frag(form.globalErrors.map(error => li(error.format)))
          )
        else
          frag()
      )
    else
      frag()

  def textInput(inputId: String, inputLabel: String, field: Field)(implicit
      messages: MessagesProvider
  ): Frag =
    if (field.hasErrors)
      div(cls := "fr-input-group fr-input-group--error")(
        label(cls := "fr-label", `for` := inputId)(inputLabel),
        input(
          `type` := "text",
          cls := "fr-input fr-input--error",
          aria.describedby := s"$id-error-desc",
          id := inputId,
          name := field.name,
          field.value.map(v => value := v),
        ),
        p(id := s"$id-error-desc", cls := "fr-error-text")(
          field.errors.map(e => e.format).mkString(", ")
        )
      )
    else
      frag(
        label(cls := "fr-label", `for` := inputId)(inputLabel),
        input(
          `type` := "text",
          cls := "fr-input",
          id := inputId,
          name := field.name,
          field.value.map(v => value := v),
        )
      )

  def organisationSelect(inputId: String, inputLabel: String, field: Field)(implicit
      messages: MessagesProvider
  ): Tag = {
    val containerClass =
      "fr-select-group" + (if (field.hasErrors) " fr-select-group--error" else "")
    val selectClass =
      "fr-select" + (if (field.hasErrors) " fr-select--error" else "")
    div(cls := containerClass)(
      label(cls := "fr-label", `for` := inputId)(inputLabel),
      select(
        cls := selectClass,
        id := inputId,
        if (field.hasErrors) aria.describedby := s"$id-error-desc" else frag(),
        name := field.name,
        frag(
          Organisation.all.map(organisation =>
            option(
              value := organisation.id.id,
              field.value.filter(_ === organisation.id.id).map(_ => selected)
            )(organisation.name)
          )
        )
      ),
      field.hasErrors.some
        .filter(identity)
        .map(_ =>
          p(id := s"$id-error-desc", cls := "fr-error-text")(
            field.errors.map(_.format).mkString(", ")
          )
        )
    )
  }

  def areaMultiSelect(inputId: String, inputLabel: String, namePrefix: String, form: Form[_])(
      implicit messages: MessagesProvider
  ): Tag = {
    val keys = form.data.keys.filter(_.startsWith(s"$namePrefix[")).toList
    val fields = keys.map(key => form(key))
    val values = fields.flatMap(_.value).toSet

    div(cls := "fr-select-group")(
      label(cls := "fr-label", `for` := inputId)(inputLabel),
      select(
        cls := "fr-select use-slimselect",
        id := inputId,
        if (fields.exists(_.hasErrors)) aria.describedby := s"$id-error-desc" else frag(),
        name := namePrefix + "[]",
        multiple,
        frag(
          Area.all.map(area =>
            option(
              value := area.id.toString,
              (if (values.contains(area.id.toString)) selected else frag())
            )(area.name)
          )
        )
      ),
      (if (fields.exists(_.hasErrors))
         p(id := s"$id-error-desc", cls := "fr-error-text")(
           fields.flatMap(_.errors).map(_.format).mkString(", ")
         )
       else
         frag())
    )
  }

  def groupMultiSelect(
      inputId: String,
      inputLabel: String,
      namePrefix: String,
      form: Form[_],
      groups: List[UserGroup]
  )(implicit
      messages: MessagesProvider
  ): Tag = {
    val keys = form.data.keys.filter(_.startsWith(s"$namePrefix[")).toList
    val fields = keys.map(key => form(key))
    val values = fields.flatMap(_.value).toSet

    div(cls := "fr-select-group")(
      label(cls := "fr-label", `for` := inputId)(inputLabel),
      select(
        cls := "fr-select use-slimselect",
        id := inputId,
        if (fields.exists(_.hasErrors)) aria.describedby := s"$id-error-desc" else frag(),
        name := namePrefix + "[]",
        multiple,
        frag(
          groups.map(group =>
            option(
              value := group.id.toString,
              (if (values.contains(group.id.toString)) selected else frag())
            )(group.name)
          )
        )
      ),
      (if (fields.exists(_.hasErrors))
         p(id := s"$id-error-desc", cls := "fr-error-text")(
           fields.flatMap(_.errors).map(_.format).mkString(", ")
         )
       else
         frag())
    )
  }

  def checkbox(inputId: String, inputLabel: String, field: Field): Tag =
    div(cls := "fr-fieldset__element")(
      div(cls := "fr-checkbox-group")(
        input(
          name := field.name,
          id := inputId,
          `type` := "checkbox",
          aria.describedby := s"$inputId-messages",
          value := "true",
          (if (field.value === Some("true")) checked else frag())
        ),
        label(cls := "fr-label", `for` := inputId)(inputLabel),
        div(
          cls := "fr-messages-group",
          id := s"$inputId-messages",
          aria.live := "assertive"
        )
      )
    )

  def roleCheckboxes(form: Form[_]): Tag = {
    val idPrefix = "account-creation-role"
    div(
      fieldset(
        cls := "fr-fieldset",
        id := idPrefix,
        aria.labelledby := s"$idPrefix-legend $idPrefix-messages"
      )(
        legend(
          cls := "fr-fieldset__legend--regular fr-fieldset__legend",
          id := s"$idPrefix-legend"
        )("Rôle(s) souhaité(s) dans Administration+"),
        checkbox("account-creation-is-manager", "Responsable", form("isManager")),
        checkbox("account-creation-is-instructor", "Instructeur", form("isInstructor")),
        div(cls := "fr-messages-group", id := s"$idPrefix-messages", aria.live := "assertive")
      )
    )
  }

  def signatureForm(index: Int, idPrefix: String, field: Field)(implicit
      messages: MessagesProvider
  ): Tag =
    div(
      h4(s"Signataire ${index + 1}"),
      textInput(
        s"$idPrefix-first-name",
        "Prénom",
        field("firstName"),
      ),
      textInput(
        s"$idPrefix-last-name",
        "Nom",
        field("lastName"),
      ),
      textInput(
        s"$idPrefix-phone-number",
        "Numéro de téléphone",
        field("phoneNumber"),
      ),
    )

  // TODO: this form needs some js to work correctly
  def signaturesForm(form: Form[_])(implicit messages: MessagesProvider): Tag = {
    val keys = form.data.keys.filter(_.startsWith(s"signatures[")).toList
    val indices = keys
      .flatMap(value => "^signatures\\[(\\d+)\\].*".r.findFirstMatchIn(value).map(_.group(1).toInt))
      .distinct
      .sorted
    val nextIndex: Int = indices.maxOption.map(_ + 1).getOrElse(0)

    div(
      frag(
        indices.map(i =>
          signatureForm(i, s"account-creation-signatures-$i", form(s"signatures[$i]"))
        )
      ),
      signatureForm(
        nextIndex,
        s"account-creation-signatures-$nextIndex",
        form(s"signatures[$nextIndex]")
      )
    )
  }

  def accountCreationPage(
      form: Form[AccountCreationFormData],
      isNamedAccount: Boolean,
  )(implicit
      messages: MessagesProvider,
      request: RequestHeader
  ): Tag =
    views.main.publicLayout(
      "Formulaire d’inscription",
      accountCreationForm(form, isNamedAccount),
      addBreadcrumbs = true,
      helpers.head.publicCss("generated-js/slimselect.min.css"),
      frag(
        script(`type` := "text/javascript", src := JavascriptController.javascriptRoutes.url),
        helpers.head.publicScript("generated-js/index.js"),
      )
    )

  private def accountCreationForm(
      accountCreationForm: Form[AccountCreationFormData],
      isNamedAccount: Boolean,
  )(implicit messages: MessagesProvider, request: RequestHeader) =
    div(
      h3("Formulaire d’inscription"),
      form(
        action := (if (isNamedAccount) AccountCreationController.handleNamedAccountCreationForm.path
                   else AccountCreationController.handleSharedAccountCreationForm.path),
        method := (if (isNamedAccount)
                     AccountCreationController.handleNamedAccountCreationForm.method
                   else AccountCreationController.handleSharedAccountCreationForm.method),
        formGlobalErrors(accountCreationForm),
        CSRFInput,
        input(
          `type` := "hidden",
          name := "isNamedAccount",
          value := isNamedAccount.toString,
        ),
        textInput(
          "account-creation-email",
          "Votre adresse email",
          accountCreationForm("email"),
        ),
        textInput(
          "account-creation-first-name",
          "Votre prénom",
          accountCreationForm("firstName"),
        ),
        textInput(
          "account-creation-last-name",
          "Votre nom",
          accountCreationForm("lastName"),
        ),
        textInput(
          "account-creation-phone-number",
          "Votre numéro de téléphone",
          accountCreationForm("phoneNumber"),
        ),
        (
          if (isNamedAccount)
            frag()
          else
            signaturesForm(accountCreationForm)
        ),
        areaMultiSelect(
          "account-creation-areas",
          "Départements",
          "area",
          accountCreationForm,
        ),
        textInput(
          "account-creation-qualite",
          "Votre qualité",
          accountCreationForm("qualite"),
        ),
        organisationSelect(
          "account-creation-organisation",
          "Organisme d’affectation (ex. CAF, pôle emploi, préfecture, etc.)",
          accountCreationForm("organisation"),
        ),
        textInput(
          "account-creation-misc-organisation",
          "Votre organisation diverse",
          accountCreationForm("miscOrganisation"),
        ),
        (
          if (isNamedAccount)
            roleCheckboxes(accountCreationForm)
          else
            frag()
        ),
        textInput(
          "account-creation-message",
          "Votre message",
          accountCreationForm("message"),
        ),
        button(`type` := "submit", cls := "fr-btn")(
          "Envoyer ma demande"
        )
      )
    )

  def managerValidationPage(
      formId: UUID,
      form: Form[AccountCreationFormData],
      isNamedAccount: Boolean,
      groups: List[UserGroup],
  )(implicit
      messages: MessagesProvider,
      request: RequestHeader
  ): Tag =
    views.main.layout(
      "Demande de création de compte",
      managerValidationForm(formId, form, isNamedAccount, groups),
      helpers.head.publicCss("generated-js/slimselect.min.css"),
      frag(
        script(`type` := "text/javascript", src := JavascriptController.javascriptRoutes.url),
        helpers.head.publicScript("generated-js/index.js"),
      )
    )

  // TODO: show accounts with same email
  private def managerValidationForm(
      formId: UUID,
      accountCreationForm: Form[AccountCreationFormData],
      isNamedAccount: Boolean,
      groups: List[UserGroup],
  )(implicit messages: MessagesProvider, request: RequestHeader) =
    div(
      h3("Bonjour (Responsable), voici une personne qui voudrait rejoindre Administration+"),
      form(
        action := AccountCreationController.handleManagerValidation(formId).path,
        method := AccountCreationController.handleManagerValidation(formId).method,
        formGlobalErrors(accountCreationForm),
        CSRFInput,
        input(
          `type` := "hidden",
          name := "isNamedAccount",
          value := isNamedAccount.toString,
        ),
        textInput(
          "account-creation-email",
          "Votre adresse email",
          accountCreationForm("email"),
        ),
        textInput(
          "account-creation-first-name",
          "Votre prénom",
          accountCreationForm("firstName"),
        ),
        textInput(
          "account-creation-last-name",
          "Votre nom",
          accountCreationForm("lastName"),
        ),
        textInput(
          "account-creation-phone-number",
          "Votre numéro de téléphone",
          accountCreationForm("phoneNumber"),
        ),
        (
          if (isNamedAccount)
            frag()
          else
            signaturesForm(accountCreationForm)
        ),
        areaMultiSelect(
          "account-creation-areas",
          "Départements",
          "area",
          accountCreationForm,
        ),
        textInput(
          "account-creation-qualite",
          "Votre qualité",
          accountCreationForm("qualite"),
        ),
        organisationSelect(
          "account-creation-organisation",
          "Organisme d’affectation (ex. CAF, pôle emploi, préfecture, etc.)",
          accountCreationForm("organisation"),
        ),
        textInput(
          "account-creation-misc-organisation",
          "Votre organisation diverse",
          accountCreationForm("miscOrganisation"),
        ),
        (
          if (isNamedAccount)
            roleCheckboxes(accountCreationForm)
          else
            frag()
        ),
        input(
          `type` := "hidden",
          name := accountCreationForm("message").name,
          accountCreationForm("message").value.map(v => value := v),
        ),
        accountCreationForm("message").value.map(v => p(v)),
        groupMultiSelect(
          "account-creation-groups",
          "Groupes",
          "groups",
          accountCreationForm,
          groups
        ),
        button(
          `type` := "submit",
          cls := "fr-btn fr-btn--secondary",
          formaction := AccountCreationController.handleManagerRejection(formId).path
        )(
          "Refuser"
        ),
        button(`type` := "submit", cls := "fr-btn")(
          "Ajouter dans un groupe"
        ),
      )
    )

}
