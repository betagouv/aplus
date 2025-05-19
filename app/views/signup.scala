package views

import cats.syntax.all._
import controllers.routes.{HomeController, SignupController}
import helper.UUIDHelper
import java.util.UUID
import models.{Area, Organisation, SignupRequest, UserGroup}
import models.forms.SignupFormData
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.i18n.MessagesProvider
import play.api.libs.json.Json
import play.api.mvc.{Flash, RequestHeader}
import scala.util.Try
import scalatags.Text.all._
import serializers.ApiModel.SelectableGroup
import serializers.Keys
import views.helpers.common.contactLink
import views.helpers.forms.{displayFormGlobalErrors, selectInput, textInput, CSRFInput}

object signup {

  def page(
      signupRequest: SignupRequest,
      signupForm: Form[SignupFormData],
      groups: List[UserGroup]
  )(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash,
      messages: MessagesProvider,
      request: RequestHeader
  ): Tag =
    helpers.common.loggedInPage(
      "Inscription",
      signupRequest.email,
      innerForm(signupRequest, signupForm, groups)
    )

  private def innerForm(
      signup: SignupRequest,
      signupForm: Form[SignupFormData],
      groups: List[UserGroup]
  )(implicit flash: Flash, messages: MessagesProvider, request: RequestHeader) =
    div(
      cls := "mdl-card mdl-shadow--2dp mdl-cell mdl-cell--12-col",
      div(
        cls := "mdl-grid single--max-width-800px",
        h3(
          cls := "mdl-cell mdl-cell--12-col single--margin-top-16px single--margin-bottom-16px",
          "Finalisation de votre inscription"
        ),
        form(
          action := SignupController.createSignup.path,
          method := SignupController.createSignup.method,
          cls := "mdl-cell mdl-cell--12-col mdl-grid mdl-grid--no-spacing",
          displayFormGlobalErrors(signupForm),
          CSRFInput,
          flash
            .get("redirect")
            .map(redirect => input(`type` := "hidden", name := "redirect", value := redirect)),
          groupsData(groups),
          organisationSelect(signupForm),
          areaSelect(signupForm),
          groupSelect(signupForm, groups),
          p(
            "Vous ne trouvez pas votre structure ? ",
            "Vous pouvez ",
            contactLink("contacter l’équipe Administration+"),
          ),
          h4("Votre compte"),
          sharedAccountRadio(signupForm),
          sharedAccountFields(signupForm),
          personalAccountFields(signupForm),
          phoneNumberField(signupForm),
          cguCheckbox,
          validationButton
        )
      )
    )

  private def groupsData(groups: List[UserGroup]): Tag =
    div(
      id := "signup-groups-id",
      data("signup-groups") := Json.stringify(
        Json.toJson(
          for {
            group <- groups
            organisationId <- group.organisationId.toList
            areaId <- group.areaIds
          } yield SelectableGroup(
            id = group.id,
            name = group.name,
            organisationId = organisationId.id,
            areaId = areaId
          )
        )
      )
    )

  private def organisationSelect(
      form: Form[SignupFormData]
  )(implicit messages: MessagesProvider): Tag = {
    val selectedValue = form(Keys.Signup.organisationId).value.orEmpty
    val options = ("Sélectionnez votre organisme", "") :: List[Organisation](
      Organisation.franceServices,
      Organisation.msap,
      Organisation.association
    ).map(organisation => (organisation.name, organisation.id.id))
    selectInput(
      form(Keys.Signup.organisationId),
      "Organisme",
      true,
      options.map { case (name, optValue) =>
        option(
          value := optValue,
          (optValue === selectedValue).some.filter(identity).map(_ => selected),
          name
        )
      },
      "signup-organisation-id".some
    )
  }

  private def areaSelect(form: Form[SignupFormData])(implicit messages: MessagesProvider): Tag = {
    val selectedValue = form(Keys.Signup.areaId).value.orEmpty
    selectInput(
      form(Keys.Signup.areaId),
      "Territoire",
      true,
      Area.all.map(area =>
        option(
          value := area.id.toString,
          (area.id.toString === selectedValue).some.filter(identity).map(_ => selected),
          area.name
        )
      ),
      "signup-area-id".some
    )
  }

  private def groupSelect(form: Form[SignupFormData], groups: List[UserGroup])(implicit
      messages: MessagesProvider
  ): Tag = {
    val selectedValue = form(Keys.Signup.groupId).value.orEmpty
    val selectedOrganisation = form(Keys.Signup.organisationId).value
    val selectedArea = form(Keys.Signup.areaId).value.flatMap(UUIDHelper.fromString)
    val selectableGroups =
      for {
        organisationId <- selectedOrganisation.toList
        areaId <- selectedArea.toList
        group <- groups
        if group.organisationId.map(_.id) === organisationId.some
        if group.areaIds.contains[UUID](areaId)
      } yield (group.name, group.id.toString)
    val options = ("Sélectionnez votre structure", "") :: selectableGroups
    selectInput(
      form(Keys.Signup.groupId),
      "Structure",
      true,
      options.map { case (name, optValue) =>
        option(
          value := optValue,
          (optValue === selectedValue).some.filter(identity).map(_ => selected),
          name
        )
      },
      "signup-group-id".some
    )
  }

  private def sharedAccountRadio(form: Form[SignupFormData]) = {
    val isSharedAccount =
      form("sharedAccount").value.flatMap(value => Try(value.toBoolean).toOption).getOrElse(false)
    div(
      cls := "mdl-cell mdl-cell--12-col",
      fieldset(
        label(
          cls := "mdl-radio mdl-js-radio mdl-js-ripple-effect",
          `for` := "signup-personal-account-radio-id",
          input(
            `type` := "radio",
            id := "signup-personal-account-radio-id",
            cls := "mdl-radio__button",
            name := "sharedAccount",
            value := "false",
            isSharedAccount.some.filterNot(identity).map(_ => checked)
          ),
          span(cls := "mdl-radio__label", "Compte Nominatif")
        ),
        label(
          cls := "mdl-radio mdl-js-radio mdl-js-ripple-effect single--margin-left-24px",
          `for` := "signup-shared-account-radio-id",
          input(
            `type` := "radio",
            id := "signup-shared-account-radio-id",
            cls := "mdl-radio__button",
            name := "sharedAccount",
            value := "true",
            isSharedAccount.some.filter(identity).map(_ => checked)
          ),
          span(cls := "mdl-radio__label", "Compte Partagé")
        )
      ),
      p("Les règles de conformité au RGPD et à la CNIL privilégient le compte nominatif.")
    )
  }

  private def sharedAccountFields(form: Form[SignupFormData])(implicit messages: MessagesProvider) =
    div(
      id := "signup-shared-account-fields-id",
      cls := "mdl-cell mdl-cell--12-col",
      div(
        cls := "single--display-flex single--align-items-center",
        div(
          cls := "single--margin-right-8px single--margin-bottom-8px mdl-color-text--red-A700",
          i(cls := "icon material-icons", "warning"),
        ),
        p(
          id := "signup-shared-account-warning-id",
          cls := " mdl-color-text--red-A700",
          "Votre choix d’opter pour un compte partagé entre agents vous impose de rester vigilant sur ",
          a(
            href := "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/peut-on-partager-un-compte-sur-administration+",
            target := "_blank",
            rel := "noopener noreferrer",
            "ce point de nos conditions générales d’utilisation."
          )
        ),
      ),
      textInput(form("sharedAccountName"), "signup-shared-account-name-id", "Nom du compte", true)
    )

  private def personalAccountFields(form: Form[SignupFormData])(implicit
      messages: MessagesProvider
  ) =
    div(
      id := "signup-personal-account-fields-id",
      cls := "mdl-cell mdl-cell--12-col",
      textInput(form("firstName"), "signup-first-name-id", "Prénom", true),
      textInput(form("lastName"), "signup-last-name-id", "Nom", true),
      textInput(form("qualite"), "signup-qualite-id", "Intitulé du poste", false)
    )

  private def phoneNumberField(form: Form[SignupFormData])(implicit messages: MessagesProvider) =
    textInput(
      form("phoneNumber"),
      "signup-phone-number-id",
      "Numéro de téléphone professionel",
      false
    )

  private def cguCheckbox =
    div(
      cls := "mdl-cell mdl-cell--12-col single--padding-bottom-16px",
      p(
        label(
          cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect",
          `for` := "checkbox-charte",
          input(
            `type` := "checkbox",
            id := "checkbox-charte",
            name := "cguChecked",
            cls := "mdl-checkbox__input",
            value := "true"
          ),
          span(
            cls := "mdl-checkbox__label",
            "J’atteste avoir pris connaissance des ",
            a(
              href := HomeController.cgu.url,
              "conditions générales d’utilisation"
            ),
            " et je m’engage à les respecter. ",
            span(cls := "mdl-color-text--red-A700", "*")
          )
        )
      ),
      // Because text is on 3 lines on a phone
      div(
        cls := "mdl-cell mdl-cell--4-col-phone mdl-cell--hide-tablet mdl-cell--hide-desktop single--margin-bottom-40px",
      )
    )

  private def validationButton =
    button(
      id := "signup-validation-id",
      `type` := "submit",
      cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored mdl-cell mdl-cell--4-col-phone mdl-cell--6-col-tablet mdl-cell--1-offset-tablet mdl-cell--10-col-desktop mdl-cell--1-offset-desktop",
      "Valider l’inscription"
    )

}
