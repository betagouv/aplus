package views

import cats.syntax.all._
import controllers.routes.{ApplicationController, Assets, UserController}
import helper.Time
import java.util.UUID
import models.{
  Answer,
  Application,
  Area,
  Authorization,
  FileMetadata,
  Organisation,
  User,
  UserGroup
}
import modules.AppConfig
import play.api.data.Form
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import serializers.Keys
import views.helpers.forms.CSRFInput

object application {

  def applicationSentSuccessMessage(applicationId: UUID): Tag =
    span(
      "Votre demande a bien été créée. Vous pouvez la consulter ",
      a(href := ApplicationController.show(applicationId).url, "en cliquant ici"),
      ". Celle-ci n’est plus modifiable, cependant il vous est toujours possible d’envoyer un message pour apporter une précision ou corriger une erreur. ",
      "Pour créer une nouvelle demande, vous pouvez ",
      a(href := ApplicationController.create.url, "cliquer sur ce lien"),
      " ou cliquer sur le bouton « Créer une demande »."
    )

  def applicationFilesLinks(
      files: List[FileMetadata],
      application: Application,
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      config: AppConfig,
  ): Frag =
    frag(
      files
        .filter(_.attached.isApplication)
        .map(file =>
          fileLink(
            Authorization.fileCanBeShown(file.attached, application)(currentUserRights),
            file,
            application.creatorUserName,
            "",
            file.status,
            config
          )
        )
    )

  def answerFilesLinks(
      files: List[FileMetadata],
      answer: Answer,
      application: Application,
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      config: AppConfig,
  ): Frag =
    frag(
      files
        .filter(_.attached.answerIdOpt === answer.id.some)
        .map(file =>
          fileLink(
            Authorization.fileCanBeShown(file.attached, application)(currentUserRights),
            file,
            answer.creatorUserName,
            "mdl-cell mdl-cell--12-col typography--text-align-center",
            file.status,
            config
          )
        )
    )

  def fileLink(
      isAuthorized: Boolean,
      metadata: FileMetadata,
      uploaderName: String,
      additionalClasses: String,
      status: FileMetadata.Status,
      config: AppConfig,
  ): Tag = {
    import FileMetadata.Status._
    val link: Frag =
      if (isAuthorized) {
        if (status === Available)
          frag(
            "le fichier ",
            a(href := ApplicationController.file(metadata.id).url, metadata.filename)
          )
        else
          s"le fichier ${metadata.filename}"
      } else
        "un fichier"

    val statusMessage: Frag = status match {
      case Available   => ""
      case Scanning    => "(Scan par un antivirus en cours)"
      case Quarantined => "(Fichier supprimé par l’antivirus)"
      case Expired     => "(Fichier expiré et supprimé)"
      case Error       =>
        "(Une erreur est survenue lors de l’envoi du fichier, celui-ci n’est pas disponible)"
    }
    div(
      cls := "vertical-align--middle mdl-color-text--black single--font-size-14px single--font-weight-600 single--margin-top-8px" + additionalClasses,
      i(cls := "icon material-icons icon--light", "attach_file"),
      s" $uploaderName a ajouté ",
      link,
      statusMessage,
    )
  }

  def organisationIcon(
      userId: UUID,
      creatorUserName: String,
      usersOrganisations: Map[UUID, List[Organisation.Id]]
  ): Tag = {
    val icons = Map(
      Organisation.poleEmploiId -> "france_travail",
      Organisation.msaId -> "msa",
      Organisation.cpamId -> "cpam",
      Organisation.cramId -> "cpam",
      Organisation.cnamId -> "cpam",
      Organisation.cafId -> "caf",
      Organisation.cnavId -> "cnav",
      Organisation.carsatId -> "cnav",
      Organisation.ddfipId -> "dgfip",
      Organisation.drfipId -> "dgfip",
    )
    val iconName: Option[String] = for {
      organisations <- usersOrganisations.get(userId)
      name <- organisations.flatMap(id => icons.get(id)).headOption
    } yield name

    iconName.orElse(
      Map(
        "A+" -> "aplus",
        "Défenseur des droits".toUpperCase() -> "ddd"
      ).find { case (name, _) => creatorUserName.toUpperCase.contains(name) }
        .map { case (_, icon) => icon }
    ) match {
      case Some(icon) =>
        img(
          cls := "mdl-list__item-avatar",
          src := Assets.versioned("images/admin/" + icon + "-icon.png").url
        )
      case None =>
        i(cls := "material-icons mdl-list__item-avatar", "person")
    }
  }

  def closeApplicationModal(
      applicationId: UUID
  )(implicit request: RequestHeader): Tag =
    tag("dialog")(
      cls := "mdl-dialog",
      id := "dialog-terminate",
      h4(
        cls := "mdl-dialog__title",
        "Est-ce que la réponse vous semble utile pour l'usager ?"
      ),
      form(
        action := ApplicationController.terminate(applicationId).path,
        method := ApplicationController.terminate(applicationId).method,
        CSRFInput,
        div(
          cls := "mdl-dialog__content",
          div(
            cls := "inputs--row",
            input(
              id := "yes",
              cls := "input--sweet",
              `type` := "radio",
              name := "usefulness",
              value := "Oui"
            ),
            label(
              `for` := "yes",
              img(
                cls := "input__icon",
                src := Assets.versioned("images/twemoji/1f600.svg").url,
                "Oui"
              )
            ),
            input(
              id := "neutral",
              cls := "input--sweet",
              `type` := "radio",
              name := "usefulness",
              value := "Je ne sais pas"
            ),
            label(
              `for` := "neutral",
              img(
                cls := "input__icon",
                src := Assets.versioned("images/twemoji/1f610.svg").url
              ),
              span(style := "width: 100%", "Je ne sais pas")
            ),
            input(
              id := "no",
              cls := "input--sweet",
              `type` := "radio",
              name := "usefulness",
              value := "Non"
            ),
            label(
              `for` := "no",
              img(
                cls := "input__icon",
                src := Assets.versioned("images/twemoji/1f61e.svg").url,
                "Non"
              ),
            )
          ),
          br,
          b("Vous devez sélectionner une réponse pour fermer la demande.")
        ),
        div(
          cls := "mdl-dialog__actions",
          button(
            id := "close-dialog-quit",
            `type` := "button",
            cls := "mdl-button mdl-button--raised",
            "Quitter"
          ),
          button(
            id := "close-dialog-terminate",
            `type` := "submit",
            disabled := "disabled",
            cls := "mdl-button mdl-button--raised mdl-button--colored",
            "Fermer"
          )
        )
      )
    )

  def reopenButton(applicationId: UUID)(implicit request: RequestHeader): Tag =
    div(
      cls := "mdl-cell mdl-cell--3-col mdl-cell--9-offset-desktop mdl-cell--12-col-phone",
      form(
        action := ApplicationController.reopen(applicationId).path,
        method := ApplicationController.reopen(applicationId).method,
        CSRFInput,
        button(
          cls := "mdl-button mdl-button--raised mdl-button--primary mdl-js-button do-not-print single--width-100pc",
          "Réouvrir l’échange"
        )
      )
    )

  def answerFormError(form: Form[_]): Frag =
    form.hasErrors.some
      .filter(identity)
      .map(_ =>
        div(
          cls := "notification notification--error",
          span(
            a(
              href := s"#answer",
              "Votre réponse n’a pas été envoyée en raison d’une erreur dans le formulaire. ",
              i(cls := "fa-solid fa-arrow-down")
            )
          )
        )
      )

  def noAnswerError(form: Form[_]): Frag =
    form.hasErrors.some.filter(identity).map { _ =>
      val message = "Oups ! Il semblerait que vous ayez oublié de remplir le contenu du message " +
        "que vous souhaitez envoyer à vos interlocuteurs. Merci de reprendre la procédure. "
      div(
        cls := "notification notification--error",
        span(message)
      )
    }

  def answerThread(
      application: Application,
      attachments: List[FileMetadata],
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      usersOrganisations: Map[UUID, List[Organisation.Id]],
      config: AppConfig,
  ): Frag =
    frag(
      application.answers.map { answer =>
        frag(
          answer.invitedUsers.nonEmpty.some
            .filter(identity)
            .map(_ =>
              div(
                cls := "mdl-cell mdl-cell--12-col vertical-align--middle",
                style := "text-align: center; color: #000000d6; font-size: 14px; font-weight: 600; line-height: 16px;",
                i(cls := "icon material-icons icon--light", "people"),
                " ",
                answer.creatorUserName,
                " a invité ",
                answer.invitedUsers.values.mkString(", "),
                " - ",
                span(
                  id := s"date-inviteds-${answer.id}",
                  cls := "vertical-align--middle",
                  span(
                    cls := "application__age",
                    " Il y a ",
                    answer.ageString,
                    " (",
                    Time.formatPatternFr(answer.creationDate, "dd MMM YYYY - HH:mm"),
                    ")"
                  )
                )
              )
            ),
          Authorization
            .canSeeAnswer(answer, application)(currentUserRights)
            .some
            .filter(identity)
            .map { _ =>
              val showArchiveButton =
                answer.answerType === Answer.AnswerType.ApplicationProcessed &&
                  application.creatorUserId === currentUser.id &&
                  !application.closed &&
                  (answer.creatorUserID =!= currentUser.id)
              val showGenericApplicationProcessed =
                answer.answerType === Answer.AnswerType.ApplicationProcessed &&
                  !showArchiveButton
              val messageHasInfos =
                // Note: always true for admins "** Message de 0 caractères **"
                answer.message.nonEmpty ||
                  answer.declareApplicationHasIrrelevant ||
                  answer.userInfos.getOrElse(Map.empty).nonEmpty ||
                  showArchiveButton ||
                  showGenericApplicationProcessed
              frag(
                answerFilesLinks(
                  attachments,
                  answer,
                  application,
                  currentUser,
                  currentUserRights,
                  config
                ),
                messageHasInfos.some
                  .filter(identity)
                  .map(_ =>
                    div(
                      cls := ("mdl-card mdl-cell mdl-cell--10-col mdl-cell--12-col-phone answer" +
                        (if (answer.creatorUserID === currentUser.id)
                           " mdl-cell--2-offset mdl-cell--0-offset-phone"
                         else "")),
                      id := s"answer-${answer.id}",
                      div(
                        cls := ("answer-card mdl-card__supporting-text mdl-card--border" +
                          (if (!answer.visibleByHelpers) " mdl-color--grey-50" else "")),
                        (!answer.visibleByHelpers).some
                          .filter(identity)
                          .map(_ =>
                            frag(
                              div(
                                id := s"reserved-${answer.id}",
                                cls := "vertical-align--middle",
                                "Réponse réservée aux instructeurs ",
                                i(cls := "icon material-icons icon--light", "info"),
                              ),
                              div(
                                cls := "mdl-tooltip",
                                `for` := s"reserved-${answer.id}",
                                "L’aidant ne voit pas ce message"
                              )
                            )
                          ),
                        div(
                          cls := "mdl-list",
                          div(
                            cls := "mdl-list__item",
                            div(
                              cls := "mdl-list__item-primary-content",
                              organisationIcon(
                                answer.creatorUserID,
                                answer.creatorUserName,
                                usersOrganisations
                              ),
                              span(cls := "single--font-weight-600", answer.creatorUserName),
                              (currentUser.admin).some
                                .filter(identity)
                                .map(_ =>
                                  span(
                                    cls := "do-not-print mdl-color-text--red single--font-weight-bold",
                                    " ",
                                    a(
                                      href := UserController.editUser(answer.creatorUserID).url,
                                      " Voir fiche utilisateur "
                                    )
                                  )
                                )
                            ),
                            div(
                              cls := "mdl-list__item-secondary-content",
                              div(
                                id := s"date-${answer.id}",
                                cls := "vertical-align--middle",
                                span(
                                  cls := "application__age",
                                  s"Il y a ${answer.ageString} (",
                                  Time.formatPatternFr(answer.creationDate, "dd MMM YYYY - HH:mm"),
                                  ")"
                                )
                              )
                            )
                          )
                        ),
                        (answer.declareApplicationHasIrrelevant).some
                          .filter(identity)
                          .map(_ =>
                            div(
                              cls := "info-box info-box--orange do-not-print",
                              answer.creatorUserName,
                              " a indiqué qu’",
                              b(
                                "il existe une procédure standard que vous pouvez utiliser pour cette demande"
                              ),
                              ", vous aurez plus de détails dans sa réponse."
                            )
                          ),
                        (answer.userInfos
                          .getOrElse(Map.empty)
                          .nonEmpty)
                          .some
                          .filter(identity)
                          .map(_ =>
                            ul(
                              frag(
                                answer.userInfos.getOrElse(Map.empty).toList.map {
                                  case (key, value) =>
                                    li(key, ": ", b(value))
                                }
                              )
                            )
                          ),
                        p(cls := "answer__message", answer.message),
                        showArchiveButton.some
                          .filter(identity)
                          .map(_ =>
                            div(
                              cls := "info-box do-not-print",
                              "Cette demande a bien été traitée. Je vous invite à fermer l’échange en cliquant sur le bouton ci-dessous :",
                              br,
                              br,
                              button(
                                id := "archive-button-2",
                                cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--primary mdl-js-ripple-effect",
                                "Fermer l’échange"
                              ),
                              br,
                              br
                            )
                          ),
                        showGenericApplicationProcessed.some
                          .filter(identity)
                          .map(_ =>
                            div(
                              cls := "info-box",
                              answer.creatorUserName,
                              " a indiqué avoir traité la demande.",
                            )
                          ),
                      )
                    )
                  )
              )
            }
        )
      }
    )

  def applicationProcessedCheckbox(currentUser: User): Frag =
    currentUser.instructor.some
      .filter(identity)
      .map(_ =>
        frag(
          div(
            id := "application-processed-checkbox",
            cls := "mdl-cell mdl-cell--12-col",
            label(
              cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect mdl-js-ripple-effect--ignore-events vertical-align--middle",
              input(
                `type` := "checkbox",
                cls := "mdl-checkbox__input",
                name := "applicationHasBeenProcessed",
                value := "true"
              ),
              span(
                cls := "mdl-checkbox__label",
                "Indiquer que j’ai traité la demande"
              ),
              " ",
              i(cls := "icon material-icons icon--light", "info")
            )
          ),
          div(
            cls := "mdl-tooltip",
            `for` := "application-processed-checkbox",
            "Invite l’aidant à fermer la demande. ",
          )
        )
      )

  def inviteForm(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      userGroups: List[UserGroup],
      groupsWithUsersThatCanBeInvited: List[(UserGroup, List[User])],
      groupsThatCanBeInvited: List[UserGroup],
      application: Application,
      selectedArea: Area
  )(implicit request: RequestHeader): Tag =
    form(
      action := ApplicationController.invite(application.id).path,
      method := ApplicationController.invite(application.id).method,
      cls := "mdl-cell mdl-cell--12-col mdl-grid aplus-protected-form",
      input(
        `type` := "hidden",
        name := Keys.Application.areaId,
        readonly := true,
        value := selectedArea.id.toString
      ),
      if (
        currentUser.admin || Authorization
          .hasAccessToFranceServicesNetwork(userGroups)(currentUserRights)
      ) {
        div(
          "Territoire concerné : ",
          views.helpers
            .changeAreaSelect(
              selectedArea,
              Area.all,
              ApplicationController.show(application.id),
              "onglet" -> "invitation"
            )
        )
      } else {
        ()
      },
      views.helpers.forms.CSRFInput,
      groupsWithUsersThatCanBeInvited.nonEmpty.some
        .filter(identity)
        .map(_ =>
          fieldset(
            cls := "mdl-cell mdl-cell--12-col mdl-grid",
            legend(
              cls := "single--padding-top-16px single--padding-bottom-16px mdl-typography--title",
              "Inviter une autre personne sur la discussion"
            ),
            table(
              cls := "mdl-data-table mdl-js-data-table mdl-cell mdl-cell--12-col",
              style := "border: none;",
              thead(
                tr(
                  th(cls := "mdl-data-table__cell--non-numeric"),
                  th(cls := "mdl-data-table__cell--non-numeric", "Structure"),
                  th(cls := "mdl-data-table__cell--non-numeric", "Nom"),
                  th(cls := "mdl-data-table__cell--non-numeric", "Qualité")
                )
              ),
              tbody(
                groupsWithUsersThatCanBeInvited.sortBy { case (group, _) => group.name }.flatMap {
                  case (group, users) =>
                    users
                      .sortBy(_.name)
                      .map(user =>
                        tr(
                          td(
                            label(
                              cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect mdl-js-ripple-effect--ignore-events",
                              input(
                                `type` := "checkbox",
                                cls := "mdl-checkbox__input",
                                name := "users[]",
                                value := user.id.toString
                              ),
                            )
                          ),
                          td(
                            cls := "mdl-data-table__cell--non-numeric",
                            group.name
                          ),
                          td(
                            cls := "mdl-data-table__cell--non-numeric",
                            user.name
                          ),
                          td(
                            cls := "mdl-data-table__cell--non-numeric",
                            user.qualite
                          )
                        )
                      )
                }
              )
            )
          )
        ),
      groupsThatCanBeInvited.nonEmpty.some
        .filter(identity)
        .map(_ => views.helpers.applications.inviteTargetGroups(groupsThatCanBeInvited)),
      div(
        cls := "mdl-textfield mdl-js-textfield mdl-textfield--floating-label mdl-cell mdl-cell--12-col",
        textarea(
          cls := "mdl-textfield__input",
          `type` := "text",
          rows := "5",
          id := "agents-invitation-message",
          style := "width: 100%;",
          name := "message"
        ),
        label(
          cls := "mdl-textfield__label",
          `for` := "agents-invitation-message",
          i(cls := "material-icons", style := "vertical-align: middle;", "message"),
          " Laisser ici un message pour l’invitation..."
        )
      ),
      (currentUser.instructor || currentUser.expert).some
        .filter(identity)
        .map(_ =>
          frag(
            div(
              id := "private-invitation",
              label(
                cls := "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect mdl-js-ripple-effect--ignore-events vertical-align--middle",
                input(
                  `type` := "checkbox",
                  cls := "mdl-checkbox__input",
                  name := "privateToHelpers",
                  value := "true"
                ),
                span(cls := "mdl-checkbox__label"),
                "Restreindre le message d’invitation aux Agents Administration+ ",
                i(cls := "icon material-icons icon--light", "info")
              )
            ),
            div(
              cls := "mdl-tooltip",
              `for` := "private-invitation",
              "Le message d’invitation ne sera pas visible par l’aidant."
            )
          )
        ),
      br,
      button(
        id := "application-complete",
        cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored mdl-cell mdl-cell--12-col js-on-submit-disabled",
        "Inviter"
      )
    )

}
