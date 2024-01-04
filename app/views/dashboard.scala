package views

import play.twirl.api.Html
import scalatags.Text.all._
import models.{Application, Authorization, User, UserGroup}
import controllers.routes.ApplicationController
import models.formModels.ApplicationsInfos
import org.webjars.play.WebJarsUtil
import play.api.mvc.RequestHeader
import models.{Application, Authorization, User, UserGroup}
import models.Application.Status.{Archived, New, Processed, Processing, Sent, ToArchive}
import helper.Time
import internalStats.charts
import modules.AppConfig
import internalStats.Filters
import java.time.LocalDate

object dashboard {

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      applications: List[Application],
      groups: List[UserGroup],
      filters: ApplicationsInfos,
      config: AppConfig,
  )(implicit
      request: RequestHeader,
  ): Tag =
    views.main.layout(
      "Dashboard",
      frag(content(currentUser, currentUserRights, applications, groups, filters, config))
    )

  def content(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      applications: List[Application],
      groups: List[UserGroup],
      filters: ApplicationsInfos,
      config: AppConfig,
  ): Tag =
    div()(
      h3(cls := "aplus-title")(s"Bonjour, ${currentUser.name}"),
      p("Bienvenue sur votre tableau de bord. Vous y trouverez le résumé de votre journée."),
      p(cls := "aplus-dashboard-date")(
        s"aujourd’hui ${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
      ),
      div(cls := "fr-grid-row fr-grid-row--gutters fr-mb-1w")(
        div(cls := "fr_card__outer_container fr-col-md-6 fr-col")(
          div(
            cls := "fr_card fr-enlarge-link fr-card--horizontal fr-card--horizontal-half fr_card__highlight"
          )(
            div(cls := "fr_card__body")(
              div(cls := "fr_card__content")(
                div(cls := "fr_card__container")(
                  strong(cls := "fr_card__title")("Mon compte"),
                  a()(
                    i(cls := "material-icons material-icons-outlined")("edit"),
                  )
                ),
                div(cls := "fr_card__container")(
                  div(cls := "fr_card__desc__content")(currentUser.name),
                  p(cls := "fr-tag fr-tag--sm ")(currentUser.qualite)
                ),
                div(cls := "fr_card__container")(currentUser.email),
                div(cls := "fr_card__container")(currentUser.phoneNumber),
              )
            )
          )
        ),
        div(cls := "fr_card__outer_container fr-col-md-12 fr-col")(
          div(cls := "fr_card fr-enlarge-link fr-card--horizontal")(
            strong(cls := "fr_card__title")("Mon compte"),
            if (!currentUser.groupAdmin)
              (
                p(cls := "aplus-paragraph")(
                  a(cls := "aplus-alert")("Validation de compte")
                )
              ),
            if (groups.nonEmpty)
              (
                table(cls := "fr-table fr-table--striped fr-table--compact")(
                  thead(
                    tr(
                      th("Groupes"),
                      th("Nouvelles demandes (x)"),
                      th("Demandes souffrantes (x)"),
                    )
                  ),
                  tbody(
                    for (group <- groups) yield {
                      tr(
                        td(group.name),
                        td("yy"),
                        td("xx"),
                      )
                    }
                  )
                )
              )
            else
              (
                "vous n'êtes membre d'aucun groupe"
              )
          )
        ),
        div(cls := "fr_card__outer_container fr-col-md-12 fr-col")(
          div(cls := "fr_card fr-enlarge-link fr-card--horizontal aplus-flex")(
            charts(
              Filters(
                startDate = LocalDate.now().minusDays(30),
                endDate = LocalDate.now(),
                areaIds = Nil,
                organisationIds = Nil,
                creatorGroupIds = Nil,
                invitedGroupIds = Nil
              ),
              config
            )
          )
        )
      )
    )

}
