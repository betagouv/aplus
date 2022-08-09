package views

import cats.syntax.all._
import controllers.routes.{ApplicationController, Assets}
import helper.Crypto.{EncryptedField, KeySet}
import helpers.forms.CSRFInput
import java.util.UUID
import models.{Answer, Application, Area, Authorization, FileMetadata, User, UserGroup}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import serializers.Keys

object application {

  def applicationFilesLinks(
      files: List[FileMetadata],
      application: Application,
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      config: AppConfig,
  ): Frag = {
    val daysRemaining =
      Application.filesAvailabilityLeftInDays(config.filesExpirationInDays)(application)
    frag(
      files
        .filter(_.attached.isApplication)
        .map(file =>
          fileLink(
            Authorization.fileCanBeShowed(config.filesExpirationInDays)(file.attached, application)(
              currentUser.id,
              currentUserRights
            ),
            file,
            daysRemaining,
            application.creatorUserName,
            "",
            file.status,
            config
          )
        )
    )
  }

  def answerFilesLinks(
      files: List[FileMetadata],
      answer: Answer,
      application: Application,
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      config: AppConfig,
  ): Frag = {
    val daysRemaining = Answer.filesAvailabilityLeftInDays(config.filesExpirationInDays)(answer)
    frag(
      files
        .filter(_.attached.answerIdOpt === answer.id.some)
        .map(file =>
          fileLink(
            Authorization.fileCanBeShowed(config.filesExpirationInDays)(file.attached, application)(
              currentUser.id,
              currentUserRights
            ),
            file,
            daysRemaining,
            answer.creatorUserName,
            "mdl-cell mdl-cell--12-col typography--text-align-center",
            file.status,
            config
          )
        )
    )
  }

  def fileLink(
      isAuthorized: Boolean,
      metadata: FileMetadata,
      daysRemaining: Option[Int],
      uploaderName: String,
      additionalClasses: String,
      status: FileMetadata.Status,
      config: AppConfig,
  ): Tag = {
    import FileMetadata.Status._
    val link: Frag =
      if (isAuthorized) {
        // Note: legacy is not encrypted
        val decryptedFilename: String = metadata.filename
          .map(
            _.decrypt(config.fieldEncryptionKeys).toOption
              .getOrElse(
                metadata.filename.map(_.cipherTextBase64).getOrElse("Fichier non existant")
              )
          )
          .getOrElse("Fichier non existant")
        if (status === Available)
          frag(
            "le fichier ",
            a(href := ApplicationController.file(metadata.id).url, decryptedFilename)
          )
        else
          s"le fichier $decryptedFilename"
      } else
        "un fichier"

    val statusMessage: Frag = status match {
      case Available =>
        daysRemaining match {
          case None                   => "Fichier expiré et supprimé"
          case Some(expirationInDays) => s"Suppression du fichier dans $expirationInDays jours"
        }
      case Scanning    => "Scan par un antivirus en cours"
      case Quarantined => "Fichier supprimé par l’antivirus"
      case Expired     => "Fichier expiré et supprimé"
      case Error =>
        "Une erreur est survenue lors de l’envoi du fichier, celui-ci n’est pas disponible"
    }
    div(
      cls := "vertical-align--middle mdl-color-text--black single--font-size-14px single--font-weight-600 single--margin-top-8px" + additionalClasses,
      i(cls := "icon material-icons icon--light", "attach_file"),
      s" $uploaderName a ajouté ",
      link,
      " ( ",
      statusMessage,
      " ) "
    )
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
          b("Vous devez sélectionner une réponse pour archiver la demande.")
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
            "Archiver"
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

  def inviteForm(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
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
      div(
        "Territoire concerné : ",
        views.helpers
          .changeAreaSelect(selectedArea, Area.all, ApplicationController.show(application.id))
      ),
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
        cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored mdl-cell mdl-cell--12-col",
        "Inviter"
      )
    )

}
