package views.helpers

import cats.syntax.all._
import helper.MiscHelpers.intersperseList
import models.Application.Status.{Archived, New, Processed, Processing, Sent, ToArchive}
import models.{Application, Organisation, User, UserGroup}
import play.api.data.FormError
import play.api.i18n.Messages
import scalatags.Text.all._

object applications {

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
      val organisation: Option[Organisation] = group.organisationSetOrDeducted
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
            attr("data-group-name") := s"${group.name}",
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

}
