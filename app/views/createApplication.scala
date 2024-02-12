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
      h4(cls := "aplus-title--background")("Nouvelle demande"),
      span(cls := "fr-hint aplus-hint aplus-text-small")("Les champs marqués d'un * sont obligatoires"),
      form(cls := "aplus-form")(
        fieldset(cls := "fr-fieldset")(
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
          ),
        ),
        fieldset(cls := "fr-fieldset")(
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Structure demandeuse",
          ),
        ),
        fieldset(cls := "fr-fieldset")(
          label(
            cls := "fr-label aplus-bold",
            `for` := "areaIdSelect",
            "Selectionner les organismes concernés sur la zone",
          ),
        ),
        fieldset(cls := "fr-fieldset aplus-fieldset")(
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
          div(cls := "aplus-fieldset-title aplus-bold")("information concernant l’usager"),
          label(
            cls := "fr-label",
            `for` := "areaIdSelect",
            "Prénom",
            input(
              cls := "fr-input",
              `type` := "text",
              name := "firstname",
            ),
            "Nom de famille",
            input(
              cls := "fr-input",
              `type` := "text",
              name := "surname",
            ),
            "Date de naissance",
            input(
              cls := "fr-input",
              `type` := "date",
              name := "birthdate",
              placeholder := "jj/mm/aaaa",
            ),
          ),
          fieldset(cls := "fr-fieldset aplus-fieldset")(
            legend(
              cls := "fr-label",
              `for` := "areaIdSelect",
              "Autorisation de l’usager",
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
          ),
          fieldset(cls := "fr-fieldset aplus-fieldset")(
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
          fieldset(cls := "fr-fieldset aplus-fieldset")(
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
    )
}
