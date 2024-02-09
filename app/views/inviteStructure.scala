package views

import scalatags.Text.all._
import models.{Application, Authorization, User}
import models.Area

object inviteStructure {

  def inviteStructure(application: Application, currentUser: User): Frag =
    frag(
      tag("dialog")(
        aria.labelledby := "fr-modal-title-modal-1",
        role := "dialog",
        id := "fr-modal-add-structure",
        cls := "fr-modal",
        div(
          cls := "fr-container fr-container--fluid fr-container-md",
          div(
            cls := "fr-grid-row fr-grid-row--center",
            div(
              cls := "fr-col-12 fr-col-md-8 fr-col-lg-6",
              div(
                cls := "fr-modal__body",
                div(
                  cls := "fr-modal__header",
                  button(
                    cls := "fr-btn--close fr-btn",
                    title := "Fermer la fenêtre modale",
                    aria.controls := "fr-modal-add-structure",
                    "Fermer"
                  )
                ),
                div(
                  cls := "fr-modal__content",
                  h1(
                    id := "fr-modal-title-modal-1",
                    cls := "fr-modal__title",
                    "Inviter une organisation à rejoindre conversation"
                  ),
                  form(cls := "aplus-form")(
                    fieldset(
                      cls := "fr-fieldset",
                      id := "checkboxes",
                      aria.labelledby := "checkboxes-legend checkboxes-messages",
                      select(
                        cls := "fr-select",
                        id := "structureIdSelect",
                        name := "structureIds",
                      )(
                        option(value := "null")("Selectionner une organisation"),
                        Area.all.map { area =>
                          option(value := area.id.toString(), area.name)
                        }
                      ),

                      div(
                        cls := "fr-fieldset__element",
                        id :="checkboxes-groups-container",
                      ),
                      div(
                        cls := "fr-messages-group",
                        id := "checkboxes-messages",
                        aria.live := "assertive"
                      )
                    ),
                    /*
                  h4()("Invitez une personne à rejoindre la conversation"),
                  div(cls := "aplus-spacer aplus-slimselect-hide-all")(
                    select(cls := "use-slimselect-in-message", id := "structure-slimselect", `name` := "structureIds[]", `multiple`)(
                      option(value := "all", "Toutes les organisations"),
                      option(value := "todo", "TODO structure")
                    ),
                  ),
                  p()("Si la personne ne figure pas dans la liste, vous avez la possibilité de lui envoyer une invitation pour rejoindre Administration Plus. Pour ce faire, veuillez remplir ce formulaire :"),
                  label(cls :="fr-label")(
                    "Prénom**",
                    input(cls := "fr-input", `type` := "text", name := "firstname", placeholder := "Prénom", required),
                  ),
                  label(cls :="fr-label")(
                    "Nom**",
                    input(cls := "fr-input", `type` := "text", name := "lastname", placeholder := "Nom", required),
                  ),
                  label(cls :="fr-label")(
                    "E-mail**",
                    input(cls := "fr-input", `type` := "email", name := "email", placeholder := "e-mail", required),
                  ),
                  label(cls :="fr-label")(
                    "Téléphone",
                    input(cls := "fr-input", `type` := "phone", name := "phone", placeholder := "Téléphone", required),
                  ),
                  label(cls :="fr-label")(
                    "Territoire concerné",
                    select(cls := "fr-select", name := "territory", required)(
                      option(value := "TODO", "TODO territoire"),
                    ),
                  ),
                  label(cls :="fr-label")(
                    "Organisme",
                    input(cls := "fr-input", `type` := "text", name := "structure", placeholder := "Organisme", required),
                  ),
                     */
                    label(cls := "fr-label")(
                      "Laissez-lui un message",
                      textarea(
                        cls := "fr-input",
                        `type` := "text",
                        name := "function",
                        placeholder := "Message...",
                        required
                      ),
                    ),
                    div(cls := "aplus-spacer aplus-align-right")(
                      button(cls := "fr-btn fr-btn--icon-right", `type` := "submit")("Inviter")
                    )
                  )
                )
              )
            )
          )
        )
      ),
    )

}
