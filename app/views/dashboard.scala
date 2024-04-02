package views

import controllers.routes.{GroupController, UserController}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import models.{Authorization, User, UserGroup}
import models.forms.ApplicationsPageInfos
import modules.AppConfig
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import views.internalStats.{charts, Filters}

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
      applicationsPageEmptyFilters: ApplicationsPageInfos.Filters,
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
      currentUser,
      currentUserRights,
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
      div(cls := "aplus-space-between")(
        h3(cls := "aplus-title")(s"Bonjour, ${currentUser.name}"),
        p(cls := "aplus-dashboard-date")(
          "aujourd’hui ",
          LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        ),
      ),
      p("Bienvenue sur votre tableau de bord. Vous y trouverez le résumé de votre journée."),
      div(cls := "fr-grid-row fr-grid-row--gutters fr-mb-1w")(
        div(cls := "fr_card__outer_container fr-col-md-6 fr-col")(
          div(
            cls := "fr_card fr-card--horizontal fr-card--horizontal-half fr_card__highlight"
          )(
            div(cls := "fr_card__body")(
              div(cls := "fr_card__content")(
                div(cls := "fr_card__container")(
                  strong(cls := "fr_card__title")("Mon compte"),
                  a(href := UserController.showEditProfile.url)(
                    i(cls := "material-icons material-icons-outlined")("edit"),
                  )
                ),
                div(cls := "fr_card__container")(
                  div(
                    div(cls := "fr_card__desc__content")(currentUser.name),
                    p(cls := "fr-tag fr-tag--sm ")(currentUser.qualite)
                  )
                ),
                div(cls := "fr_card__container")(currentUser.email),
                div(cls := "fr_card__container")(currentUser.phoneNumber),
              )
            )
          )
        ),
        div(cls := "fr-col-md-12 fr-col")(
          div(cls := "fr_card__outer_container fr-col-md-6 fr-col")(
            div(cls := "fr_card fr-card--horizontal")(
              strong(cls := "fr_card__title")("Mes groupes"),
              if (currentUser.admin)
                (
                  p(cls := "aplus-paragraph")(
                    a(href := GroupController.showEditMyGroups.url, cls := "aplus-alert")(
                      "Validation de compte"
                    )
                  )
                ),
              if (infos.groupInfos.nonEmpty)
                (
                  table(cls := "fr-table fr-table--striped fr-table--compact")(
                    thead(
                      tr(
                        th("Groupes"),
                        th(
                          a(href := infos.applicationsPageEmptyFilters.withStatusNew.toUrl)(
                            s"Nouvelles demandes (${infos.newCount})"
                          )
                        ),
                        th(
                          a(href := infos.applicationsPageEmptyFilters.withStatusLate.toUrl)(
                            s"Demandes souffrantes (${infos.lateCount})"
                          )
                        ),
                      )
                    ),
                    tbody(
                      for (groupInfos <- infos.groupInfos) yield {
                        tr(
                          td(
                            a(
                              href := infos.applicationsPageEmptyFilters
                                .withGroup(groupInfos.group.id)
                                .toUrl
                            )(
                              groupInfos.group.name,
                              i(cls := "material-icons material-icons-outlined external-link")(
                                "arrow_outward"
                              )
                            )
                          ),
                          td(
                            a(
                              href := infos.applicationsPageEmptyFilters
                                .withGroup(groupInfos.group.id)
                                .withStatusNew
                                .toUrl
                            )(
                              groupInfos.newCount,
                              i(cls := "material-icons material-icons-outlined external-link")(
                                "arrow_outward"
                              )
                            )
                          ),
                          td(
                            a(
                              href := infos.applicationsPageEmptyFilters
                                .withGroup(groupInfos.group.id)
                                .withStatusLate
                                .toUrl
                            )(
                              groupInfos.lateCount,
                              i(cls := "material-icons material-icons-outlined external-link")(
                                "arrow_outward"
                              )
                            ),
                          ),
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
          )
        ),
        div(cls := "fr_card__outer_container fr-col-md-12 fr-col")(
          div(cls := "fr_card fr-card--horizontal aplus-flex")(
            charts(infos.chartFilters, config)
          )
        )
      )
    )

}
