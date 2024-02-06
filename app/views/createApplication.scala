package views

import models.{User, Authorization}
import play.twirl.api.Html
import helper.TwirlImports.toHtml

import scalatags.Text.all._
import models.Area
import play.api.mvc.{RequestHeader}


object createApplication {

  def page(
    currentUser: User,
    userRights: Authorization.UserRights,
    currentArea: Area,
  )(implicit
    request: RequestHeader,
  ): Html =
    views.main.layout(
      "Créer une demande",
      frag(content()),
    )

  def content(
  ): Tag =
    div()(
      h2(cls := "fr-h2")("Nouvelle demande"),
      span(cls := "fr-hint")("Les champs marqués d'un * sont obligatoires"),
      form(cls := "fr-form")(
        fieldset()(
          select(
            cls := "fr-select",
            id := "input-7"
          )(
            option(value := "1")("Option 1"),
            option(value := "2")("Option 2"),
            option(value := "3")("Option 3")
          ),
          legend(cls := "fr-visually-hidden")("Créer une demande"),
          div(cls := "fr-field-group")(
            label(
              `for` := "input-1",
              cls := "fr-label"
            )("Nom de la demande"),
            input(
              cls := "fr-input",
              id := "input-1",
              `type` := "text",
              placeholder := "Nom de la demande"
            )
          ),
          div(cls := "fr-field-group")(
            label(
              `for` := "input-2",
              cls := "fr-label"
            )("Description de la demande"),
            textarea(
              cls := "fr-input",
              id := "input-2",
              placeholder := "Description de la demande"
            )
          ),
          div(cls := "fr-field-group")(
            label(
              `for` := "input-3",
              cls := "fr-label"
            )("Type de demande"),
            select(
              cls := "fr-select",
              id := "input-3"
            )(
              option(value := "1")("Option 1"),
              option(value := "2")("Option 2"),
              option(value := "3")("Option 3")
            )
          ),
          div(cls := "fr-field-group")(
            label(
              `for` := "input-4",
              cls := "fr-label"
            )("Date de début"),
            input(
              cls := "fr-input",
              id := "input-4",
              `type` := "date"
            )
          ),
          div(cls := "fr-field-group")(
            label(
              `for` := "input-5",
              cls := "fr-label"
            )("Date de fin"),
            input(
              cls := "fr-input",
              id := "input-5",
              `type` := "date"
            )
          ),
          div(cls := "fr-field-group")(
            label(
              `for` := "input-6",
              cls := "fr-label"
            )("Pièce jointe"),
            input(
              cls := "fr-input",
              id := "input-6",
              `type` := "file"
            )
          ),
        )
      )
    )
}
