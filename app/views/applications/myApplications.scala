package views.applications

import cats.syntax.all._
import constants.Constants
import controllers.routes.ApplicationController
import helper.Time
import models.{Answer, Application, Authorization, FileMetadata, User, UserGroup}
import models.forms.ApplicationsPageInfos
import modules.AppConfig
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import views.helpers.applications.statusTag

object myApplications {
  val TODOflag = false

  case class MyApplicationInfos(
      application: Application,
      creatorIsInFS: Boolean,
      creatorGroup: Option[UserGroup],
      invitedGroups: List[UserGroup],
      lastOperateurAnswer: Option[Answer],
      shouldBeAnsweredInTheNext24h: Boolean,
  )

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      applications: List[MyApplicationInfos],
      selectedApplication: Option[MyApplicationInfos],
      selectedApplicationFiles: List[FileMetadata],
      groups: List[UserGroup],
      filters: ApplicationsPageInfos,
      config: AppConfig,
  )(implicit request: RequestHeader): Tag =
    views.main.layout(
      currentUser,
      currentUserRights,
      "Mes demandes",
      ApplicationController.myApplications.url,
      frag(
        content(
          currentUser,
          currentUserRights,
          maxWidth = false,
          filters,
          applications,
          selectedApplication,
          selectedApplicationFiles,
          groups,
          config
        )
      ),
    )

  def content(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      maxWidth: Boolean,
      filters: ApplicationsPageInfos,
      applications: List[MyApplicationInfos],
      selectedApplication: Option[MyApplicationInfos],
      selectedApplicationFiles: List[FileMetadata],
      groups: List[UserGroup],
      config: AppConfig,
  )(implicit request: RequestHeader): Tag =
    div(
      cls := "mdl-cell mdl-cell--12-col mdl-grid--no-spacing",
      if (filters.allGroupsOpenCount <= 0 && filters.allGroupsClosedCount <= 0)
        noApplications(currentUser, currentUserRights)
      else
        openApplications(
          currentUser,
          currentUserRights,
          applications,
          selectedApplication,
          selectedApplicationFiles,
          groups,
          filters,
          config
        ),
    )

  private def noApplications(currentUser: User, currentUserRights: Authorization.UserRights) =
    div(
      cls := "info-box",
      h4("Bienvenue sur Administration+"),
      br,
      "La page d'accueil d'Administration+ vous permet de consulter la liste de vos demandes en cours. ",
      br,
      "Les demandes permettent la résolution d'un blocage administratif rencontré par un usager. ",
      br,
      br,
      currentUser.instructor.some
        .filter(identity)
        .map(_ =>
          frag(
            "Vous êtes instructeur : vous recevrez un email lorsqu'un aidant sollicitera votre administration ou organisme. ",
            br,
            br,
          )
        ),
      Authorization
        .canCreateApplication(currentUserRights)
        .some
        .filter(identity)
        .map(_ =>
          frag(
            "Pour créer votre première demande, vous pouvez utiliser le bouton suivant. ",
            br,
            button(
              cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--primary mdl-cell mdl-cell--4-col mdl-cell--12-col-phone onclick-change-location",
              data("location") := ApplicationController.create.url,
              "Créer une demande"
            ),
            br,
            br,
            i(
              "Un doute sur la nature du blocage ? Posez-nous la question par mail : ",
              a(href := "mailto:@Constants.supportEmail?subject=Question", Constants.supportEmail),
              "."
            )
          )
        )
    )

  private def openApplications(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      applications: List[MyApplicationInfos],
      selectedApplication: Option[MyApplicationInfos],
      selectedApplicationFiles: List[FileMetadata],
      groups: List[UserGroup],
      filters: ApplicationsPageInfos,
      config: AppConfig,
  )(implicit request: RequestHeader) =
    frag(
      div(cls := "fr-grid-row")(
        div(
          cls := "fr-col-6",
          div(
            h4(cls := "aplus-title")(
              "Mes demandes"
            )
          ),
          p()(
            "Ici, vous pouvez gérer vos propres demandes ainsi que celles de votre/vos groupe(s). ",
          ),
          if (filters.lateCount > 0)
            (
              div(cls := "fr-alert fr-alert--warning")(
                h3(cls := "fr-alert__title")("Attention"),
                p(s"Il y a ${filters.lateCount} dossier souffrants dans vos groupes")
              )
            )
          else
            frag(),
          div(
            Authorization
              .canCreateApplication(currentUserRights)
              .some
              .filter(identity)
              .map(_ =>
                button(
                  cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--primary onclick-change-location",
                  data("location") := ApplicationController.create.url,
                  "Créer une demande"
                )
              )
          ),
          div(cls := " fr-search-bar  aplus-spacer")(
            label(cls := "fr-label", `for` := "search-input"),
            input(
              cls := "fr-input",
              `role` := "search",
              placeholder := "Rechercher",
              id := "search-input"
            ),
            a(cls := "fr-btn", href := "#search-column")(
              "Rechercher"
            )
          )
        ),
        div(cls := "fr-col fr-col-4 fr-grid-row fr-grid-row--center")(
          a(cls := "fr-btn fr-btn--height-fix", href := ApplicationController.create.url)(
            "+ Créer une demande"
          )
        )
      ),
      div(
        cls := "fr-grid-row",
        div(
          cls := "fr-col-6",
          div(cls := "aplus-spacer aplus-slimselect-hide-all")(
            select(id := "application-slimselect", `name` := "groupIds[]", `multiple`)(
              option(value := "all", "Tous les groupes"),
              groups.map(group => option(value := s"${group.id}", group.name))
            ),
          ),
        ),
        div(cls := "fr-col-6")(
        )
      ),
      otherFilters(currentUser, filters),
      div(cls := "fr-grid-row aplus-my-application")(
        div(cls := "fr-col fr-col-4 aplus-my-application--message-list")(
          applications
            .sortBy(_.application.closed)
            .map { application =>
              frag(
                div(cls := "fr-card")(
                  a(
                    cls := "aplus-application-link",
                    id := "search-column",
                    href := ApplicationController.myApplications.url + s"?demande-visible=${application.application.id}"
                  )(
                    div(cls := "fr-card-inner")(
                      div(cls := "fr-card-header")(
                        div(cls := "fr-card-title")(
                          span(cls := "aplus-card-title")(
                            span(cls := "fr-card-title-text aplus-title aplus-bold")(
                              s"#${application.application.internalId}",
                            ),
                            if (TODOflag) {
                              frag(
                                i(
                                  cls := s"material-icons material-icons-outlined aplus-icons-small aplus-icon--active",
                                  attr("aria-describedby") := "tooltip-flag"
                                )("flag"),
                                span(
                                  cls := "fr-tooltip fr-placement",
                                  id := "tooltip-flag",
                                  attr("role") := "tooltip",
                                  attr("aria-hidden") := "true"
                                )(
                                  s"Demande urgente"
                                ),
                              )
                            } else {
                              frag()
                            },
                            span(cls := "aplus-text-small")(
                              Time
                                .formatPatternFr(application.application.creationDate, "dd/mm/YYYY")
                            ),
                            if (application.shouldBeAnsweredInTheNext24h) {
                              frag(
                                i(
                                  cls := "material-icons material-icons-outlined aplus-icons-small aplus-icon--active",
                                  attr(
                                    "aria-describedby"
                                  ) := s"tooltip-timer-${application.application.id}"
                                )("timer"),
                                span(
                                  cls := "fr-tooltip fr-placement",
                                  id := "tooltip-timer",
                                  attr("role") := "tooltip",
                                  attr("aria-hidden") := "true"
                                )(
                                  s"Il reste moins de 24h pour traiter la demande"
                                )
                              )
                            } else {
                              frag()
                            }
                          )
                        )
                      )
                    ),
                    div(cls := "fr-card-inner  aplus-card-section")(
                      div(cls := "aplus-align-right")(
                        span(
                          cls := "aplus-new-messages",
                          attr("aria-describedby") := s"tooltip-new-${application.application.id}"
                        )(
                          application.application.newAnswersFor(currentUser.id).length
                        ),
                        span(
                          cls := "fr-tooltip fr-placement",
                          id := s"tooltip-new-${application.application.id}",
                          attr("role") := "tooltip",
                          attr("aria-hidden") := "true"
                        )(
                          s"Vous avez ${application.application.newAnswersFor(currentUser.id).length} nouveaux messages"
                        ),
                      ),
                      div(cls := "fr-grid-row aplus-text-small fr_card__container")(
                        frag(statusTag(currentUser, application.application)),
                        span(cls := "aplus-nowrap")(
                          i(cls := "material-icons material-icons-outlined aplus-icons-small")(
                            "mail"
                          ),
                          s"${application.application.answers.length} messages",
                        )
                      ),
                    ),
                    div(cls := "aplus-text-small  aplus-card-section ")(
                      div(cls := "aplus-between")(
                        span(cls := "aplus-message-infos")(
                          application.application.userInfos
                            .get(Application.UserFirstNameKey)
                            .zip(application.application.userInfos.get(Application.UserLastNameKey))
                            .map { case (firstName, lastName) =>
                              s"de $firstName $lastName"
                            },
                        ),
                        if (application.invitedGroups.length > 1) {
                          frag(
                            span(cls := "aplus-message-infos-multi")(
                              s"à ${application.invitedGroups(0).name} + ${application.invitedGroups.length - 1} autres"
                            )
                          )
                        } else if (application.invitedGroups.length === 1) {
                          frag(
                            span(cls := "aplus-message-infos")(
                              s"à ${application.invitedGroups(0).name}"
                            )
                          )
                        } else {
                          frag()
                        }
                      ),
                      div(cls := "aplus-between")(
                        span(cls := "aplus-message-infos aplus-message-infos--role")(
                          application.creatorGroup.map(_.name),
                        ),
                        span(cls := "aplus-message-infos aplus-message-infos--last-reply")(
                          application.lastOperateurAnswer.map(answer =>
                            if (answer.creatorUserID === currentUser.id) "Vous"
                            else answer.creatorUserName
                          ),
                        ),
                      ),
                    ),
                    div(cls := "aplus-bold aplus-card-section")(
                      application.application.subject
                    ),
                    div(
                      cls := "searchable-row",
                      attr("data-search") := application.application.searchData
                    )
                  )
                )
              )
            }
        ),
        div(cls := "fr-col fr-col-8", id := "application-message-container")(
          selectedApplication.fold[Tag](
            div(cls := "aplus-no-message--container")(
              div(cls := "aplus-no-message")(
                i(cls := "material-icons material-icons-outlined ")("forum"),
                span(
                  "Ce champ est actuellement vide, mais une fois que vous aurez sélectionné la demande, vous pourrez effectuer et lire les échanges dans cet espace"
                )
              )
            )
          )(application =>
            views.applications.messageThread.page(
              currentUser,
              currentUserRights,
              application.application,
              selectedApplicationFiles,
              config
            )
          )
        )
      )
    )

  private def otherFilters(currentUser: User, infos: ApplicationsPageInfos) = {
    val filters = infos.filters

    val filterLink = (isSelected: Boolean, text: String, uri: String) =>
      div(
        cls := "aplus-filter-header--item " + (
          if (isSelected)
            "single--border-bottom-2px"
          else
            ""
        ),
        if (isSelected)
          span(
            cls := "mdl-color-text--black single--font-weight-500",
            text
          )
        else
          a(
            cls := "single--text-decoration-none",
            href := uri,
            text
          )
      )

    div(
      cls := "aplus-filter-header aplus-spacer--top-xl",
      filterLink(
        filters.hasNoStatus,
        s"Toutes (${infos.filteredByGroupsOpenCount}) ",
        filters.withoutStatus.toUrl,
      ),
      filterLink(
        filters.isMine,
        s"Mes demandes (${infos.interactedCount}) ",
        filters.withStatusMine.toUrl,
      ),
      filterLink(
        filters.isNew,
        s"Nouvelles demandes (${infos.newCount}) ",
        filters.withStatusNew.toUrl,
      ),
      filterLink(
        filters.isProcessing,
        s"En cours (${infos.processingCount}) ",
        filters.withStatusProcessing.toUrl,
      ),
      currentUser.instructor.some
        .filter(identity)
        .map(_ =>
          filterLink(
            filters.isLate,
            s"En souffrance (${infos.lateCount}) ",
            filters.withStatusLate.toUrl,
          )
        ),
      filterLink(
        filters.isArchived,
        s"A archiver (${infos.filteredByGroupsClosedCount}) ",
        filters.withStatusArchived.toUrl,
      ),
      div(cls := "fr-fieldset__element fr-fieldset__element--inline aplus-filter-header--item")(
        div(cls := "fr-checkbox-group fr-checkbox-group--sm")(
          input(
            name := "checkboxes-hint-el-sm-1",
            id := "checkboxes-inline-3",
            attr("type") := "checkbox",
            attr("aria-describedby") := "checkboxes-inline-3-messages"
          ),
        ),
      ),
      div(
        cls := "fr-fieldset__element fr-fieldset__element--inline aplus-filter-header--item aplus-float-right"
      )(
        div(cls := "fr-checkbox-group fr-checkbox-group--sm")(
          input(
            name := "checkboxes-inline-4",
            id := "checkboxes-inline-4",
            attr("type") := "checkbox",
            attr("aria-describedby") := "checkboxes-inline-4-messages"
          ),
          label(cls := "fr-label", attr("for") := "checkboxes-inline-4")(
            i(
              cls := "material-icons material-icons-outlined aplus-icons-small",
              attr("aria-describedby") := "tooltip-timer"
            )("timer"),
            span(
              cls := "fr-tooltip fr-placement",
              id := "tooltip-timer",
              attr("role") := "tooltip",
              attr("aria-hidden") := "true"
            )(
              s"Il reste moins de 24h pour traiter la demande"
            )
          ),
        )
      )
    )
  }

}
