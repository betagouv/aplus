package views

import cats.syntax.all._
import constants.Constants
import controllers.routes.ApplicationController
import helper.Time
import helper.TwirlImports.toHtml
import java.util.UUID
import models.formModels.ApplicationsInfos
import models.Application.Status.{Archived, New, Processed, Processing, Sent, ToArchive}
import models.{Application, Authorization, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Flash, RequestHeader}
import play.twirl.api.Html
import scalatags.Text.all._

object myApplications {

  def page(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      applications: List[Application],
      groups: List[UserGroup],
      filters: ApplicationsInfos,
  )(implicit
      flash: Flash,
      request: RequestHeader,
      webJarsUtil: WebJarsUtil,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)(
      "Mes demandes"
    )(frag())(
      div(
        cls := "mdl-cell mdl-cell--12-col mdl-grid--no-spacing",
        if (filters.allGroupsOpenCount <= 0 && filters.allGroupsClosedCount <= 0)
          noApplications(currentUser, currentUserRights)
        else
          openApplications(currentUser, currentUserRights, applications, groups, filters),
      )
    )(frag())

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
      applications: List[Application],
      groups: List[UserGroup],
      filters: ApplicationsInfos,
  ) =
    frag(
      div(
        cls := "mdl-grid mdl-grid--no-spacing",
        div(
          cls := "mdl-cell mdl-cell--8-col mdl-cell--12-col-phone single--display-flex",
          div(
            cls := "single--margin-right-32px",
            h4(
              cls := "single--margin-0px",
              "Mes demandes"
            )
          ),
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
        ),
        div(
          cls := " mdl-cell mdl-cell--4-col mdl-cell--12-col-phone",
          input(
            cls := "single--font-size-16px single--padding-4px single--width-100pc",
            `type` := "search",
            placeholder := "Rechercher",
            id := "search-input"
          )
        )
      ),
      div(
        cls := "mdl-grid mdl-grid--no-spacing single--margin-bottom-8px single--margin-top-24px",
        div(
          cls := "mdl-cell mdl-cell--12-col mdl-cell--12-col-phone",
          groupsFilters(groups, filters),
          otherFilters(currentUser, filters),
        )
      ),
      applicationsList(currentUser, currentUserRights, applications)
    )

  private def groupsFilters(groups: List[UserGroup], infos: ApplicationsInfos) =
    if (groups.length <= 1)
      frag()
    else
      div(
        cls := "single--display-flex",
        frag(
          groups.map(group =>
            div(
              label(
                `for` := s"group-filter-${group.id}",
                cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect single--height-auto single--margin-right-32px",
                input(
                  id := s"group-filter-${group.id}",
                  cls := "mdl-checkbox__input application-form-invited-groups-checkbox trigger-group-filter",
                  `type` := "checkbox",
                  name := ApplicationsInfos.groupFilterKey,
                  value := s"${group.id}",
                  data("on-checked-url") := infos.filters.withGroup(group.id).toUrl,
                  data("on-unchecked-url") := infos.filters.withoutGroup(group.id).toUrl,
                  if (infos.filters.groupIsFiltered(group.id)) checked := "checked" else (),
                ),
                span(
                  cls := "mdl-checkbox__label single--font-size-14px single--line-height-22px",
                  group.name,
                  " ",
                  infos.groupsCounts.get(group.id).map(count => s"($count)")
                ),
              ),
            ),
          )
        )
      )

  private def otherFilters(currentUser: User, infos: ApplicationsInfos) = {
    val filters = infos.filters

    val filterLink = (isSelected: Boolean, text: String, uri: String) =>
      div(
        cls := "single--margin-right-16px " + (
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
      cls := "single--display-flex single--margin-top-16px",
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
        s"Archivées (${infos.filteredByGroupsClosedCount}) ",
        filters.withStatusArchived.toUrl,
      ),
    )
  }

  private def statusTag(application: Application, user: User): Tag = {
    val status = application.longStatus(user)
    val classes: String = status match {
      case Processing =>
        "tag mdl-color--light-blue-300 mdl-color-text--black"
      case Processed | ToArchive =>
        "tag mdl-color--grey-500 mdl-color-text--white"
      case Archived =>
        "tag mdl-color--grey-200 mdl-color-text--black"
      case New =>
        "tag mdl-color--pink-400 mdl-color-text--white"
      case Sent =>
        "tag mdl-color--deep-purple-100 mdl-color-text--black"
    }
    span(
      cls := classes + " single--pointer-events-all",
      status.show
    )
  }

  private def applicationLine(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      application: Application
  ): Tag = {
    val borderClass =
      if (application.longStatus(currentUser) === New) "td--important-border"
      else "td--clear-border"
    val backgroundClass =
      if (application.hasBeenDisplayedFor(currentUser.id)) "" else "td--blue-background"
    val classes = s"searchable-row $borderClass $backgroundClass"
    tr(
      data("location") := ApplicationController.show(application.id).url,
      data("search") := application.searchData,
      cls := classes,
      statusCol(currentUser, currentUserRights, application),
      infosCol(application),
      creationCol(application),
      activityCol(currentUser, application),
      searchResultCol,
      externalLinkCol(application)
    )
  }

  private def backgroundLink(application: Application): Tag =
    a(
      href := ApplicationController.show(application.id).url,
      cls := "overlay-background"
    )

  private def statusCol(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      application: Application
  ): Tag =
    td(
      cls := "mdl-data-table__cell--non-numeric mdl-data-table__cell--content-size",
      div(
        cls := "typography--text-align-center typography--text-line-height-2 overlay-foreground single--pointer-events-none ",
        statusTag(application, currentUser),
        Authorization
          .isAdmin(currentUserRights)
          .some
          .filter(identity)
          .map(_ =>
            frag(
              br,
              span(
                cls := "mdl-typography--font-bold mdl-color-text--red-A700 single--pointer-events-all",
                application.internalId,
              )
            )
          )
      ),
      backgroundLink(application)
    )

  // Note: we use pointer-events to let the background link go through the foreground box
  //       this gives the effect that the text can be selected and background is a link
  private def infosCol(application: Application): Tag =
    td(
      cls := "mdl-data-table__cell--non-numeric",
      div(
        cls := "overlay-foreground single--pointer-events-none",
        span(
          cls := "application__name single--pointer-events-all",
          application.userInfos.get(Application.UserLastNameKey),
          " ",
          application.userInfos.get(Application.UserFirstNameKey)
        ),
        i(
          cls := "single--pointer-events-all",
          application.userInfos
            .get(Application.UserCafNumberKey)
            .map(caf => s" (Num. CAF: $caf)"),
          application.userInfos
            .get(Application.UserSocialSecurityNumberKey)
            .map(nir => s" (NIR: $nir)")
        ),
        br,
        span(cls := "application__subject single--pointer-events-all", application.subject)
      ),
      backgroundLink(application)
    )

  private def creationCol(application: Application): Tag =
    td(
      cls := "mdl-data-table__cell--non-numeric mdl-data-table__cell--content-size",
      div(
        id := s"date-${application.id}",
        cls := "vertical-align--middle overlay-foreground",
        span(
          cls := "application__age",
          "Créé il y a ",
          b(application.ageString)
        ),
        " ",
        i(cls := "icon material-icons icon--light", "info")
      ),
      div(
        cls := "mdl-tooltip",
        data("mdl-for") := s"date-${application.id}",
        Time.formatPatternFr(application.creationDate, "dd MMM YYYY - HH:mm")
      ),
      backgroundLink(application)
    )

  private def activityCol(currentUser: User, application: Application): Tag = {
    val newAnswers: Frag =
      if (application.newAnswersFor(currentUser.id).length > 0 && !application.closed)
        frag(
          " ",
          span(cls := "mdl-color--pink-500 badge", application.newAnswersFor(currentUser.id).length)
        )
      else frag()
    td(
      cls := "mdl-data-table__cell--non-numeric mdl-data-table__cell--content-size hidden--small-screen",
      div(
        id := s"answers-${application.id}",
        cls := "vertical-align--middle overlay-foreground",
        i(cls := "material-icons icon--light", "chat_bubble"),
        " ",
        span(
          cls := "application__anwsers badge-holder",
          s"${application.answers.length} messages",
          newAnswers
        )
      ),
      div(
        cls := "mdl-tooltip",
        `for` := s"answers-${application.id}",
        frag(
          application.answers.map(answer =>
            frag(
              Time.formatPatternFr(answer.creationDate, "dd MMM YYYY"),
              " : ",
              answer.creatorUserName.split("\\(").head,
              br
            )
          )
        )
      ),
      backgroundLink(application)
    )
  }

  private def searchResultCol: Tag =
    td(
      cls := "mdl-data-table__cell--non-numeric search-cell mdl-data-table__cell--content-size hidden--small-screen"
    )

  private def externalLinkCol(application: Application): Tag =
    td(
      cls := "mdl-data-table__cell--non-numeric mdl-data-table__cell--content-size hidden--small-screen single--width-20px",
      a(
        href := ApplicationController.show(application.id).url,
        cls := "mdl-button mdl-js-button mdl-js-ripple-effect mdl-button--icon overlay-foreground",
        i(cls := "material-icons", "info_outline")
      ),
      backgroundLink(application)
    )

  def applicationsList(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      applications: List[Application]
  ): Tag =
    div(
      table(
        cls := "mdl-data-table mdl-js-data-table mdl-shadow--2dp single--white-space-normal",
        tfoot(
          cls := "invisible",
          tr(
            td(
              cls := "mdl-data-table__cell--non-numeric typography--text-align-center-important",
              colspan := "5",
              button(
                id := "clear-search",
                cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored",
                "Supprimer le filtre et afficher toutes les demandes"
              )
            )
          )
        ),
        tbody(
          frag(
            applications
              .sortBy(_.closed)
              .map(application => applicationLine(currentUser, currentUserRights, application))
          )
        )
      )
    )

}
