package views.applications

import cats.syntax.all._
import controllers.routes.ApplicationController
import java.time.format.DateTimeFormatter
import models.{Answer, Application, Authorization, FileMetadata, User}
import modules.AppConfig
import play.api.mvc.RequestHeader
import scalatags.Text.all._
import views.helpers.applications.statusTag
import views.helpers.forms.CSRFInput

object messageThread {

  def page(
      currentUser: User,
      userRights: Authorization.UserRights,
      application: Application,
      files: List[FileMetadata],
      config: AppConfig
  )(implicit request: RequestHeader): Tag =
    div()(
      div(cls := "fr-grid-row aplus-text-small aplus-")(
        div(cls := "fr-col fr-col-4 ")(
          div()(
            span(cls := "fr-card-title-text aplus-title aplus-bold")(
              s"#${application.internalId}",
            ),
            span(cls := "aplus-spacer--horizontal")(statusTag(currentUser, application))
          ),
          div(cls := "aplus-spacer--top-small")(
            s"Créée le ${application.creationDate.format(DateTimeFormatter.ofPattern("dd/mm/yyyy hh:mm"))} par :"
          ),
          div()(
            span(cls := "aplus-message-infos--author")(
              application.userInfos
                .get(Application.UserFirstNameKey)
                .zip(application.userInfos.get(Application.UserLastNameKey))
                .map { case (firstName, lastName) => s"$firstName $lastName" },
            ),
            span(cls := "aplus-message-infos--role")(application.creatorGroupName)
          )
        ),
        div(cls := "fr-col fr-col-4")(
          div(cls := "aplus-between")(
            span(cls := "aplus-nowrap")(
              i(cls := "material-icons material-icons-outlined aplus-icons-small")("mail"),
              s"${application.answers.length} messages",
            ),
            span(cls := "aplus-nowrap")(
              i(cls := "material-icons material-icons-outlined aplus-icons-small")("attach_file"),
              s"${files.length} documents"
            )
          ),
          div(cls := "aplus-spacer--top-small")(
            "Participants à la discussion :",
            div()(
              application.invitedUsers.map { case (_, userName) =>
                div(cls := "aplus-message-infos--role aplus-text-small")(s"${userName}")
              }.toList
            )
          )
        ),
        div(cls := "fr-col fr-col-4 aplus-align-right")(
          button(cls := "fr-btn fr-btn--height-fix fr-btn--secondary")("J’ai traité la demande")
        )
      ),
      div(cls := "aplus-spacer")(
        strong(application.subject)
      ),
      hr(cls := "aplus-hr-in-card"),
      application.answers.map { answer =>
        if (answer.declareApplicationHasIrrelevant) {
          div(cls := "aplus-message-status")(
            div()(answer.creationDate.format(DateTimeFormatter.ofPattern("dd/mm/yyyy hh:mm"))),
            div()(
              s"${answer.creatorUserName} à indiqué que son organisme n’était pas compétent pour résondre cette demande"
            )
          )

        } else {
          div(cls := "fr_card fr_dard--darker aplus-paragraph")(
            div(cls := "aplus-between")(
              div(cls := "aplus-text-small aplus-message-infos--role")(
                answer.creatorUserName,
              ),
              div(cls := "aplus-text-small")(
                s"Le ${answer.creationDate.format(DateTimeFormatter.ofPattern("dd/mm/yyyy"))}"
              )
            ),
            div(cls := "aplus-spacer--top-small")(
              answer.message
            ),
            answerFilesLinks(files, answer, application, currentUser, userRights, config)
          )
        }
      },
      div(cls := "aplus-spacer")(
        if (currentUser.instructor || currentUser.admin || currentUser.expert) {
          frag(
            form(
              cls := "aplus-form",
              action := ApplicationController.answer(application.id).url,
              method := "post",
              enctype := "multipart/form-data"
            )(
              CSRFInput,
              div(cls := "fr-input-group")(
                textarea(
                  cls := "fr-input",
                  `type` := "text",
                  name := "message",
                  placeholder := "Répondre à la demande"
                ),
              ),
              legend(cls := "fr-fieldset__legend")("Pièces jointes"),
              div(cls := "fr-fieldset__content")(
                div(cls := "fr-upload-group")(
                  input(
                    cls := "fr-upload",
                    `type` := "file",
                    name := "file[0]",
                    placeholder := "Ajouter une pièce jointe"
                  ),
                  label(cls := "fr-label", `for` := "file[0]")(
                    span(cls := "fr-hint-text")(
                      "Taille maximale : xx Mo. Formats supportés : jpg, png, pdf. Plusieurs fichiers possibles. Lorem ipsum dolor sit amet, consectetur adipiscing."
                    )
                  )
                )
              ),
              if (currentUser.instructor) {
                div(cls := "aplus-spacer")(
                  div(cls := "fr-radio-group  fr_card__container")(
                    input(
                      cls := "fr-radio",
                      `type` := "radio",
                      name := "radio",
                      id := "radio-1",
                      checked := "checked"
                    ),
                    label(
                      cls := "fr-label",
                      `for` := "radio-1",
                      name := "answer_type",
                      value := "workInProgress"
                    )("Je m’assigne à cette demande"),
                  ),
                  div(cls := "fr-radio-group fr_card__container")(
                    input(
                      cls := "fr-radio",
                      `type` := "radio",
                      name := "answer_type",
                      id := "radio-2",
                      value := "workInProgress"
                    ),
                    label(cls := "fr-label", `for` := "radio-2")(
                      "Mon organisme n’est pas compétent pour traiter cette demande"
                    ),
                  )
                )
              } else {
                frag()
              },
              hr(cls := "aplus-hr-in-card--top-margin"),
              div(cls := "aplus-spacer fr_card__container")(
                button(
                  cls := "fr-btn fr-btn--secondary",
                  attr("formaction") := ApplicationController
                    .answerApplicationHasBeenProcessed(application.id)
                    .url
                )(
                  "J’ai traité la demande"
                ),
                button(
                  cls := "fr-btn fr-btn--secondary",
                  `type` := "button",
                  attr("data-fr-opened") := "false",
                  attr("aria-controls") := "fr-modal-add-structure"
                )("+ Inviter un organisme"),
                button(cls := "fr-btn", `type` := "submit")("Répondre")
              )
            )
          )
        } else {
          frag()
        }
      ),
      inviteForm.modal(application, currentUser)
    )

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
        .filter(_.attached.answerIdOpt === Option.apply(answer.id))
        .map(file =>
          fileLink(
            Authorization.fileCanBeShown(config.filesExpirationInDays)(file.attached, application)(
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
        if (status === Available) {
          val extension = metadata.filename.split('.').lastOption
          val filename = metadata.filename.split('.').dropRight(1).mkString(".")
          frag(
            div(cls := "aplus-attach-link aplus-spacer--top-small")(
              a(href := ApplicationController.file(metadata.id).url, filename),
              i(cls := "aplus-dl-arrow icon material-icons icon--light", "arrow_downward"),
            ),
            div(cls := "aplus-text-small aplus-text--gray")(
              s"${extension.getOrElse("")} - ${numberToSize(metadata.filesize)}",
            )
          )
        } else
          s"${metadata.filename}"
      } else {
        frag()
      }

    div(
      cls := "vertical-align--middle mdl-color-text--black single--font-size-14px single--font-weight-600 single--margin-top-8px" + additionalClasses,
      link,
    )
  }

  def numberToSize(number: Int): String =
    if (number < 1024) {
      s"${number} octets"
    } else if (number < 1048576) {
      s"${(number / 1024)} Ko"
    } else if (number < 1073741824) {
      s"${(number / 1048576)} Mo"
    } else {
      s"${(number / 1073741824)} Go"
    }

}
