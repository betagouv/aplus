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
import org.checkerframework.checker.units.qual.g
import views.helpers.applications.statusTag

object myApplications {
  val TODOflag = true
  val TODOtimer = true

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
    views.main.layout("Mes demandes", frag(content(currentUser, currentUserRights, maxWidth = false, filters, applications, groups)), frag())
    
    def content(
        currentUser: User,
        currentUserRights: Authorization.UserRights,
        maxWidth: Boolean,
        filters: ApplicationsInfos,
        applications: List[Application],
        groups: List[UserGroup],
    ) : Tag = 
      div(
        cls := "mdl-cell mdl-cell--12-col mdl-grid--no-spacing",
        if (filters.allGroupsOpenCount <= 0 && filters.allGroupsClosedCount <= 0)
          noApplications(currentUser, currentUserRights)
        else
          openApplications(currentUser, currentUserRights, applications, groups, filters),
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
      applications: List[Application],
      groups: List[UserGroup],
      filters: ApplicationsInfos,
  ) =
    frag(
      div(cls := "fr-grid-row")(
        div(
          cls := "fr-col-8",
            div(
              h4(cls := "aplus-title")(
                "Mes demandes"
              )
            ),
            p()(
              "Ici, vous pouvez gérer vos propres demandes ainsi que celles de votre/vos groupe(s). ",
            ),
            if(filters.lateCount > 0)(
              div(cls :="fr-alert fr-alert--warning")(
                h3(cls := "fr-alert__title")("Attention"),
                p(s"Il y a ${filters.lateCount} dossier souffrants dans vos groupes")
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
          div(cls := " fr-search-bar  aplus-spacer")(
            label(cls := "fr-label", `for` := "application-search"),
            input(
              cls := "fr-input",
              `role` := "search",
              placeholder := "Rechercher",
              id := "application-search"
            ),
            button(cls :="fr-btn", title :="Rechercher")(
              "Rechercher"
            )
          )
        ),
        div(cls := "fr-col fr-col-4 fr-grid-row fr-grid-row--center")(
          a(cls := "fr-btn fr-btn--height-fix", href := ApplicationController.create.url)("+ Créer une demande")
        )
      ),
      div(
        cls := "mdl-grid mdl-grid--no-spacing single--margin-bottom-8px single--margin-top-24px",
        div(
          cls := "mdl-cell mdl-cell--12-col mdl-cell--12-col-phone",
          div(cls := "aplus-spacer aplus-slimselect-hide-all") (
            select(id := "application-slimselect", `name` := "groupIds[]", `multiple`)(
              option(value := "all", "Tous les groupes"),
              groups.map(group => option(value := s"${group.id}", group.name))
            ),
          ),
        ),
      ),
      div(cls := "fr-grid-row aplus-my-application") (
        div(cls := "fr-col fr-col-4 aplus-my-application--message-list")(
          applications
            .sortBy(_.closed)
            .map(application => 

              div(cls := "fr-card")(
                div(cls := "fr-card-inner")(
                  div(cls := "fr-card-header")(
                    div(cls := "fr-card-title")(
                        a(
                          cls := "aplus-card-title aplus-application-link",
                          href := ApplicationController.show(application.id).url,
                        )(
                            span(cls := "fr-card-title-text aplus-title aplus-bold")(
                              s"#${application.internalId}",
                            ),
                            i(cls := s"material-icons material-icons-outlined aplus-icons-small${if(TODOflag) " aplus-icon--active"}", attr("aria-describedby") := "tooltip-flag")("flag"),
                            span(cls := "fr-tooltip fr-placement", id := "tooltip-flag", attr("role") := "tooltip", attr("aria-hidden") :="true")(
                              "Demande urgente"
                            ),
                            span(cls := "aplus-text-small")(
                              Time.formatPatternFr(application.creationDate, "dd/mm/YYYY")
                            ),
                            i(cls := s"material-icons material-icons-outlined aplus-icons-small ${if(TODOtimer) " aplus-icon--active"}", attr("aria-describedby") := "tooltip-timer")("timer"),
                            span(cls := "fr-tooltip fr-placement", id := "tooltip-timer", attr("role") := "tooltip", attr("aria-hidden") :="true")(
                              "Il reste moins de 24h pour traiter la demande"
                            ),
                        )
                      )               
                    )
                  ),
                  div(cls := "fr-card-inner  aplus-card-section")(
                    div(cls := "fr-grid-row aplus-text-small fr_card__container")(
                      frag(statusTag(currentUser, application)),
                      span(cls := "aplus-nowrap")(
                        i(cls := "material-icons material-icons-outlined aplus-icons-small")("mail"),
                        s"${application.answers.length} messages",
                      )
                    ),
                  ),
                  
                  div(cls := "aplus-text-small  aplus-card-section")(
                      s"de ${application.userInfos.get(Application.UserFirstNameKey).get} ${application.userInfos.get(Application.UserLastNameKey).get}",
                    ),
                  div(cls := "aplus-bold aplus-card-section")(
                    application.subject
                  )
                )
              )
            ),
            div(cls := "fr-col fr-col-8", id := "application-message-container")(
              div(cls := "aplus-no-message--container")(
                  div(cls := "aplus-no-message")(
                    i(cls := "material-icons material-icons-outlined ")("forum"),
                    span("Ce champ est actuellement vide, mais une fois que vous aurez sélectionné la demande, vous pourrez effectuer et lire les échanges dans cet espace")
                  )
                )
              )
            )
        )

}
