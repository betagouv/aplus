package views.helpers

import cats.syntax.all._
import helper.MiscHelpers.intersperseList
import models.{Application, Organisation, User, UserGroup}
import models.Application.Status.{Archived, New, Processed, Processing, Sent, ToArchive}
import models.forms.ApplicationFormData
import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import scalatags.Text.all._
import views.helpers.common.contactLink
import views.helpers.forms.selectInput

object applications {

  def statusTag(currentUser: User, application: Application): Frag =
    frag(
      application.longStatus(currentUser) match {
        case Processing =>
          div(cls := "fr-tag fr-tag--sm aplus-tag--pending")(
            "en cours"
          )
        case Processed | ToArchive =>
          div(cls := "fr-tag fr-tag--sm aplus-tag--done")(
            "traité"
          )
        case Archived =>
          div(cls := "fr-tag fr-tag--sm  aplus-tag--archived")(
            "fermé"
          )
        case New =>
          div(cls := "fr-tag fr-tag--sm aplus-tag--new")(
            "Nouvelle demande"
          )
        case Sent =>
          div(cls := "fr-tag fr-tag--sm aplus-tag--sent")(
            "en attente de réponse"
          )
      }
    )

  def creatorGroup(form: Form[ApplicationFormData], creatorGroups: List[UserGroup])(implicit
      messagesProvider: Messages
  ): Frag = {
    val formCreatorGroup = form("creatorGroupId")
    frag(
      h5(
        cls := "title--addline",
        "Structure demandeuse"
      ),
      creatorGroups match {
        case Nil =>
          frag(
            p(
              cls := "mdl-color-text--red-A700",
              "Votre compte n’est rattaché à aucune structure, ",
              "la demande sera faite en votre nom. ",
            ),
            p(
              "S’il s’agit d’une erreur, il est conseillé de demander à un collègue ",
              "d’ajouter votre compte à son groupe via son onglet « Mes Groupes » ",
              "ou à défaut de contacter le support en précisant le nom des structures ",
              "auxquelles vous souhaitez être rattaché."
            )
          )
        case oneGroup :: Nil =>
          frag(
            p(
              span(
                cls := "single--font-size-16px single--font-weight-bold",
                oneGroup.name,
              ),
              " ",
              br,
              em(
                cls := "single--font-size-12px",
                "( Si le nom de la structure est incorrect, ",
                "il est conseillé de contacter le support en précisant ",
                "le nom de votre structure actuelle. )"
              )
            ),
            input(
              id := "aplus-application-form-creator-group-id",
              `type` := "hidden",
              name := "creatorGroupId",
              value := oneGroup.id.toString
            ),
            input(
              id := "aplus-application-form-creator-group-name",
              `type` := "hidden",
              name := "creatorGroupName",
              value := oneGroup.name
            )
          )
        case groups =>
          frag(
            selectInput(
              formCreatorGroup,
              ("Vous êtes membre de plusieurs structures, " +
                "veuillez choisir celle qui est mandatée pour faire cette demande"),
              false,
              groups.map { group =>
                val formValue: String = group.id.toString
                option(
                  value := formValue,
                  (formCreatorGroup.value === formValue.some).some
                    .filter(identity)
                    .map(_ => selected),
                  group.name
                )
              },
              fieldId = "aplus-application-form-creator-group".some,
              outerDivClass = "single--margin-bottom-8px",
              innerDivClass = "",
            ),
            p(
              em(
                cls := "single--font-size-12px",
                "( Si votre structure n’apparaît pas dans cette liste, ",
                "veuillez contacter le support en précisant ",
                "le nom de vos structures manquantes. )"
              )
            )
          )
      }
    )
  }

  /** This is for the Application creation form Some fields here are from `Form`
    * https://www.playframework.com/documentation/2.8.x/api/scala/play/api/data/Form.html
    */
  def applicationTargetGroups(
      groups: List[UserGroup],
      formData: Map[String, String],
      formHasErrors: Boolean,
      formErrors: Seq[FormError]
  )(implicit messagesProvider: Messages): Tag =
    div(
      cls := "mdl-grid single--background-color-white",
      if (formHasErrors)
        p(
          cls := "global-errors",
          formErrors.map(_.format).mkString(", ")
        )
      else
        (),
      fieldset(
        cls := "single--margin-left-8px single--margin-right-24px single--margin-top-8px mdl-cell mdl-cell--12-col",
        groupCheckboxes(groups, formData)
      )
    )

  /** On the application view */
  def inviteTargetGroups(groups: List[UserGroup]): Tag =
    fieldset(
      cls := "single--margin-top-16px mdl-cell mdl-cell--12-col",
      legend(
        cls := "single--padding-top-16px single--padding-bottom-16px mdl-typography--title",
        "Inviter un organisme sur la discussion"
      ),
      groupCheckboxes(groups, Map.empty)
    )

  /** On the Group Form */
  def groupFormExample(group: UserGroup): Tag =
    div(
      cls := "single--margin-left-8px",
      div(
        cls := "single--margin-bottom-8px single--font-size-16px",
        "La ",
        b("description succincte"),
        " et la ",
        b("description détaillée"),
        " permettent d’orienter l’utilisateur souhaitant créer une demande. Ils lui sont affichés de la façon suivante :"
      ),
      div(
        cls := "mdl-grid single--background-color-white form-example",
        fieldset(
          cls := "single--margin-left-8px single--margin-right-24px single--margin-top-8px mdl-cell mdl-cell--12-col",
          groupCheckboxes(group :: Nil, Map("groups[]" -> group.id.toString))
        )
      )
    )

  /** Restriction: to work with the JS part, it must be unique per page */
  def groupCheckboxes(groups: List[UserGroup], formData: Map[String, String]): List[Tag] =
    groups.sortBy(_.name).map { group =>
      val organisation: Option[Organisation] = group.organisation
      val groupIsChecked =
        formData.exists { case (k, v) => k.startsWith("groups[") && v === group.id.toString }

      def publicNoteBox(inner: Frag) =
        tr(
          td(
            cls := "info-box-container",
            div(
              cls := "info-box info-box--no-spacing",
              inner
            )
          )
        )
      val publicNote: Frag =
        group.publicNote match {
          case Some(publicNote) =>
            publicNoteBox(
              frag(intersperseList(publicNote.split("\n").toList.map(s => frag(s)), br))
            )
          case None =>
            if (organisation.map(_.id).filter(_ === Organisation.cafId).nonEmpty) {
              publicNoteBox(
                frag(
                  "La CAF aura besoin du ",
                  b("numéro identifiant CAF"),
                  " et à défaut de la date de naissance. ",
                  "Vous pouvez le renseigner dans ",
                  b("Informations concernant l’usager"),
                  " ci-dessous."
                )
              )
            } else if (organisation.map(_.id).filter(_ === Organisation.cpamId).nonEmpty) {
              publicNoteBox(
                frag(
                  "La CPAM aura besoin du ",
                  b("numéro de sécurité sociale"),
                  " et à défaut de la date de naissance. ",
                  "Vous pouvez le renseigner dans ",
                  b("Informations concernant l’usager"),
                  " ci-dessous."
                )
              )
            } else if (organisation.map(_.id).filter(_ === Organisation.laPosteId).nonEmpty) {
              publicNoteBox(
                frag(
                  "Sur A+, La Poste répond aux demandes pour déclarer un changement d’adresse, informer sur la réexpédition d’un courrier ou l’identité numérique, créer un coffre-fort numérique La Poste.",
                  br,
                  "Pour les autres questions, veuillez orienter vers les services clients."
                )
              )
            } else if (
              organisation
                .find(entity =>
                  Set(Organisation.prefId, Organisation.sousPrefId).contains(entity.id)
                )
                .nonEmpty
            ) {
              publicNoteBox(
                frag(
                  "La préfecture (ou sous-préfecture) ne répondra pas forcément aux questions relative aux renouvellements de titre de séjour ou des certificats d’immatriculation. Pour les titres de séjour concernant les étudiants, vous pouvez utiliser la plateforme du ministère de l’intérieur ",
                  a(
                    href := "https://administration-etrangers-en-france.interieur.gouv.fr/particuliers/#/",
                    target := "_blank",
                    rel := "noopener",
                    "à cette adresse"
                  ),
                  ". Pour les certificats d’immatriculation, vous pouvez contacter l’ANTS ",
                  a(
                    href := "https://ants.gouv.fr/Contacter-l-ANTS/Contactez-nous",
                    target := "_blank",
                    rel := "noopener",
                    "à cette adresse"
                  ),
                  "."
                )
              )
            } else ()
        }

      div(
        cls := "single--padding-top-8px single--padding-bottom-16px single--padding-left-16px",
        label(
          `for` := s"invite-${group.id}",
          cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect single--height-auto",
          input(
            id := s"invite-${group.id}",
            cls := "mdl-checkbox__input application-form-invited-groups-checkbox",
            `type` := "checkbox",
            name := "groups[]",
            value := s"${group.id}",
            data("group-name") := s"${group.name}",
            data("organisation-id") := group.organisationId.map(_.id).getOrElse(""),
            if (groupIsChecked) checked := "checked"
            else ()
          ),
          span(
            cls := "mdl-checkbox__label",
            b(group.name),
            organisation.map(org => em(s" (${org.name})"))
          ),
          br,
          span(group.description)
        ),
        div(
          id := s"invite-${group.id}-additional-infos",
          cls := (if (groupIsChecked) "organisation-row" else "organisation-row invisible"),
          publicNote
        )
      )
    }

  def mandatPart(form: Form[ApplicationFormData]): Tag = {
    val formMandatGenerationType = form("mandatGenerationType")
    val hasGeneratedNewMandat =
      formMandatGenerationType.value === ApplicationFormData.mandatGenerationTypeIsNew.some

    div(
      cls := "mdl-grid mdl-grid--no-spacing single--margin-bottom-32px",
      div(
        cls := "mdl-cell mdl-cell--12-col single--display-flex single--flex-direction-column",
        label(
          cls := "mdl-radio mdl-js-radio mdl-js-ripple-effect single--font-size-14px single--margin-top-8px",
          id := "mandat-option-already-label",
          `for` := "mandat-option-already",
          input(
            `type` := "radio",
            id := "mandat-option-already",
            cls := "mdl-radio__button",
            name := "mandatGenerationType",
            value := "alreadyHaveOne",
            (formMandatGenerationType.value === "alreadyHaveOne".some).some
              .filter(identity)
              .map(_ => checked),
          ),
          span(
            cls := "mdl-radio__label",
            "Ma structure dispose déjà d’un mandat"
          )
        ),
        label(
          cls := "mdl-radio mdl-js-radio mdl-js-ripple-effect single--font-size-14px single--margin-top-8px",
          id := "mandat-option-generate-label",
          `for` := "mandat-option-generate",
          input(
            `type` := "radio",
            id := "mandat-option-generate",
            cls := "mdl-radio__button",
            name := "mandatGenerationType",
            value := ApplicationFormData.mandatGenerationTypeIsNew,
            hasGeneratedNewMandat.some.filter(identity).map(_ => checked),
          ),
          span(
            cls := "mdl-radio__label",
            "Créer un mandat via Administration+"
          ),
        ),
        div(
          id := "mandat-generation-box",
          cls := "hidden",
          div(
            cls := "mdl-card mdl-shadow--4dp mdl-card--resizable single--margin-left-32px single--margin-top-8px",
            div(
              cls := "mdl-card__supporting-text aplus-color-text--black single--display-flex single--justify-content-center",
              h5(
                cls := "single--margin-0px single--font-size-14px single--text-transform-uppercase",
                "Création d’un mandat"
              )
            ),
            div(
              id := "mandat-form-data",
              cls := "mdl-card__supporting-text aplus-color-text--black single--display-flex single--flex-direction-column",
              span(
                cls := "single--margin-bottom-8px",
                "Les informations suivantes seront utilisées pour créer un nouveau mandat :"
              ),
              span(
                cls := "single--margin-left-8px single--margin-bottom-8px",
                "• Prénom : ",
                span(
                  id := "mandat-form-data-prenom",
                  cls := "aplus-color-text--error",
                  "(invalide)"
                )
              ),
              span(
                cls := "single--margin-left-8px single--margin-bottom-8px",
                "• Nom : ",
                span(id := "mandat-form-data-nom", cls := "aplus-color-text--error", "(invalide)")
              ),
              span(
                cls := "single--margin-left-8px single--margin-bottom-8px",
                "• Date de naissance : ",
                span(
                  id := "mandat-form-data-birthdate",
                  cls := "aplus-color-text--error",
                  "(invalide)"
                )
              ),
              span(
                cls := "single--margin-left-8px single--margin-bottom-8px",
                "• Structure : ",
                span(
                  id := "mandat-form-data-creator-group",
                  "(aucune)"
                )
              )
            ),
            div(
              id := "mandat-generation-form-has-changed",
              cls := "mdl-card__supporting-text aplus-color-text--black hidden",
              div(
                cls := "notification notification--error",
                span(
                  "Les champs du mandat ont changé, vous pouvez en créer un nouveau en cliquant ",
                  "sur le bouton ci-dessous."
                )
              )
            ),
            div(
              cls := "mdl-card__actions single--display-flex single--justify-content-center single--margin-bottom-16px",
              div(
                id := "mandat-generate-button",
                cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored",
                "Imprimer ",
                i(cls := "material-icons material-icons--small-postfix", "open_in_new")
              )
            ),
            div(
              id := "mandat-generation-validation-failed",
              cls := "mdl-card__supporting-text aplus-color-text--black hidden",
              div(
                cls := "notification notification--error",
                span(
                  "Merci de renseigner ci-dessus les informations concernant l’usager : ",
                  "Prénom, Nom de famille et Date de naissance."
                )
              )
            ),
            div(
              id := "mandat-generation-error-server",
              cls := "mdl-card__supporting-text aplus-color-text--black hidden",
              div(
                cls := "notification notification--error",
                span(
                  "Une erreur est survenue sur le serveur Administration+. ",
                  "Si l’erreur persiste vous pouvez ",
                  contactLink("contacter l’équipe A+"),
                  ". Merci de fournir la date et l’heure à laquelle est survenue l’erreur."
                )
              )
            ),
            div(
              id := "mandat-generation-error-browser",
              cls := "mdl-card__supporting-text aplus-color-text--black hidden",
              div(
                cls := "notification notification--error",
                span(
                  "Une erreur est survenue dans votre navigateur. ",
                  "Si l’erreur persiste vous pouvez ",
                  contactLink("contacter l’équipe A+"),
                )
              )
            ),
            div(
              id := "mandat-generation-link",
              cls := "mdl-card__supporting-text hidden",
              a(
                cls := "mdl-navigation__link",
                href := "/",
                target := "_blank",
                rel := "noopener noreferrer",
                "Si votre navigateur a bloqué l’ouverture du mandat, ",
                "vous pouvez cliquer sur ce texte pour y accéder ",
                i(cls := "material-icons material-icons--small-postfix", "open_in_new")
              )
            )
          ),
          input(
            `type` := "hidden",
            name := "linkedMandat",
            id := "linked-mandat",
            form("linkedMandat").value.map(id => value := id),
          )
        )
      )
    )
  }

}
