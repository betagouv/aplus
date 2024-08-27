package views

import controllers.routes.ApplicationController
import models.{Area, Authorization, User}
import play.api.mvc.RequestHeader
import scalatags.Text.all._

object createApplication {

  def page(
      currentUser: User,
      userRights: Authorization.UserRights,
      currentArea: Area,
  )(implicit
      request: RequestHeader,
  ): Tag =
    views.main.layout(
      currentUser,
      userRights,
      "Créer une demande",
      ApplicationController.create.url,
      frag(content()),
    )

  def content(): Tag =
    div()(
      h4(cls := "aplus-title--background")("Nouvelle demande"),
      span(cls := "fr-hint aplus-hint aplus-text-small")(
        "Les champs marqués d'un * sont obligatoires"
      ),
      form(cls := "aplus-form")(
        fieldset(cls := "fr-fieldset aplus-fieldset")(
          span(cls := "aplus-form-step")("1/8"),
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Territoire concerné",
            select(
              cls := "fr-select",
            )(
              option(value := "null")("Selectionner un territoire"),
              Area.all.map { area =>
                option(value := area.id.toString(), s"${area.name} (${area.inseeCode})")
              }
            ),
            div(cls := "fr-notice fr-notice--info aplus-notice-small")(
              div(cls := "fr-container")(
                div(cls := "fr-notice__body")(
                  p(cls := "fr-notice__title")(
                    "(le changement de territoire est une fonctionnalité expérimentale)"
                  )
                )
              )
            )
          ),
        ),
        fieldset(cls := "fr-fieldset")(
          span(cls := "aplus-form-step")("2/8"),
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Structure demandeuse",
          ),
          "TODO",
          div(cls := "fr-notice fr-notice--info aplus-notice-small")(
            div(cls := "fr-container")(
              div(cls := "fr-notice__body")(
                p(cls := "fr-notice__title")(
                  "(le changement de territoire est une fonctionnalité expérimentale)"
                )
              )
            )
          )
        ),
        fieldset(cls := "fr-fieldset")(
          span(cls := "aplus-form-step")("3/8"),
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Selectionner les organismes concernés sur la zone",
          ),
          "TODO"
        ),
        fieldset(cls := "fr-fieldset aplus-fieldset")(
          span(cls := "aplus-form-step")("4/8"),
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Sujet de la demande",
            input(
              cls := "fr-input",
              `type` := "text",
              name := "subject",
            ),
          ),
        ),
        fieldset(cls := "fr-fieldset  aplus-fieldset")(
          span(cls := "aplus-form-step")("5/8"),
          legend(cls := "fr-legend")("information concernant l’usager"),
          label(
            cls := "fr-label",
            `for` := "areaIdSelect",
            "Prénom",
            input(
              cls := "fr-input",
              `type` := "text",
              name := "firstname",
            )
          ),
          label(
            cls := "fr-label",
            `for` := "areaIdSelect",
            "Prénom",
            "Nom de famille",
            input(
              cls := "fr-input",
              `type` := "text",
              name := "surname",
            )
          ),
          label(
            cls := "fr-label",
            `for` := "areaIdSelect",
            "Prénom",
            "Date de naissance",
            input(
              cls := "fr-input",
              `type` := "date",
              name := "birthdate",
              placeholder := "jj/mm/aaaa",
            ),
          ),
        ),
        fieldset(cls := "fr-fieldset aplus-fieldset")(
          span(cls := "aplus-form-step")("6/8"),
          legend(
            cls := "fr-label",
            `for` := "areaIdSelect",
            "Autorisation de l’usager",
          ),
          div(cls := "fr-radio-group")(
            input(
              cls := "fr-radio",
              `type` := "radio",
              id := "hasMandate",
              name := "authorization"
            ),
            label(cls := "fr-label", attr("for") := "hasMandate")(
              "Ma structure dispose déjà d’un mandat",
            ),
            input(
              cls := "fr-radio",
              `type` := "radio",
              id := "noMandate",
              name := "authorization"
            ),
            label(cls := "fr-label", attr("for") := "noMandate")(
              "Créer un manda via Administration+",
            )
          ),
          label(
            cls := "fr-label",
            `for` := "areaIdSelect",
            "Date et heure",
            input(
              cls := "fr-input",
              `type` := "text",
              required := "required",
              name := "dateTime",
              placeholder := "ex: 01/01/2021 14:00",
            ),
          ),
        ),
        fieldset(cls := "fr-fieldset")(
          span(cls := "aplus-form-step")("7/8"),
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Autres informations utiles pour traiter la demande (facultatif)",
            select(
              cls := "fr-select",
            )(
              option(value := "null")("Selectionner une information"),
              Area.all.map { area =>
                option(value := area.id.toString(), s"${area.name} (${area.inseeCode})")
              }
            ),
          ),
        ),
        fieldset(cls := "fr-fieldset")(
          span(cls := "aplus-form-step")("8/8"),
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Description du problème",
            textarea(
              cls := "fr-input",
              `type` := "text",
              name := "description",
            ),
          ),
        ),
      ),
    )

}
