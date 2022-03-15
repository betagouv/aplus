package views

import cats.syntax.all._
import controllers.routes.ApplicationController
import helper.Time
import models.Application.Status.{Archived, New, Processed, Processing, Sent, ToArchive}
import models.{Application, Authorization, User}
import scalatags.Text.all._

object myApplications {

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
          application.userInfos.get(Application.USER_LAST_NAME_KEY),
          " ",
          application.userInfos.get(Application.USER_FIRST_NAME_KEY)
        ),
        i(
          cls := "single--pointer-events-all",
          application.userInfos
            .get(Application.USER_CAF_NUMBER_KEY)
            .map(caf => s" (Num. CAF: $caf)"),
          application.userInfos
            .get(Application.USER_SOCIAL_SECURITY_NUMBER_KEY)
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
      cls := "mdl-data-table__cell--non-numeric mdl-data-table__cell--content-size hidden--small-screen",
      style := "width: 20px",
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
      cls := "mdl-cell mdl-cell--12-col pem-container",
      s"Toutes (${applications.size}) :  " +
        applications
          .groupBy(_.longStatus(currentUser))
          .view
          .mapValues(_.size)
          .map { case (status, number) => status.show + s" ( $number )" }
          .mkString(" / "),
      table(
        cls := "mdl-data-table mdl-js-data-table pem-table mdl-shadow--2dp",
        style := "white-space: normal;",
        tfoot(
          cls := "invisible",
          tr(
            td(
              cls := "mdl-data-table__cell--non-numeric",
              colspan := "5",
              style := "text-align: center",
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
