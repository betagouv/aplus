package views

import controllers.routes.ApplicationController
import helper.Time
import models.{Application, Authorization, User, UserGroup}
import models.formModels.ApplicationsInfos
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import scalatags.Text.all._
import views.internalStats.{charts, Filters}

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object dashboard {

  object DashboardInfos {

    case class Group(
        group: UserGroup,
        newCount: Int,
        lateCount: Int
    )

  }

  case class DashboardInfos(
      newCount: Int,
      lateCount: Int,
      groupInfos: List[DashboardInfos.Group],
      chartFilters: Filters,
  )

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      infos: DashboardInfos,
      config: AppConfig,
  )(implicit
      request: RequestHeader,
  ): Tag =
    views.main.layout(
      "Dashboard",
      content(currentUser, currentUserRights, infos, config)
    )

  def content(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      infos: DashboardInfos,
      config: AppConfig,
  ): Tag =
    div()(
      h3(cls := "aplus-title")(s"Bonjour, ${currentUser.name}"),
      p("Bienvenue sur votre tableau de bord. Vous y trouverez le résumé de votre journée."),
      p(cls := "aplus-dashboard-date")(
        "aujourd’hui ",
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
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
            if (infos.groupInfos.nonEmpty)
              (
                table(cls := "fr-table fr-table--striped fr-table--compact")(
                  thead(
                    tr(
                      th("Groupes"),
                      th(s"Nouvelles demandes (${infos.newCount})"),
                      th(s"Demandes souffrantes (${infos.lateCount})"),
                    )
                  ),
                  tbody(
                    for (groupInfos <- infos.groupInfos) yield {
                      tr(
                        td(groupInfos.group.name),
                        td(groupInfos.newCount),
                        td(groupInfos.lateCount),
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
            charts(infos.chartFilters, config)
          )
        )
      )
    )

}
