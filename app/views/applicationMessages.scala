package views

import scalatags.Text.all._
import models.{Answer, Application, Authorization, Organisation, User}
import views.helpers.applications.statusTag
import views.inviteStructure.inviteStructure
import java.time.format.DateTimeFormatter
import scala.annotation.meta.field
import controllers.routes.{ApplicationController, Assets}
import models.FileMetadata
import java.util.UUID
import modules.AppConfig
import cats.syntax.all._

object applicationMessages {

  def page(
      currentUser: User,
      userRights: Authorization.UserRights,
      application: Application,
      files: List[FileMetadata],
      config: AppConfig
  ): Tag =
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
            s"${application.userInfos.get(Application.UserFirstNameKey).get} ${application.userInfos.get(Application.UserLastNameKey).get} ",
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
              application.invitedUsers.map { case (userId, userName) =>
                div(cls := "aplus-message-infos--role aplus-text-small")(s"${userName}")
              }.toList
            )
          )
        ),
        div(cls := "fr-col fr-col-4 aplus-align-center")(
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
                div(cls := "fr-input-group")(
                  input(
                    cls := "fr-input",
                    `type` := "file",
                    name := "file[0]",
                    placeholder := "Ajouter une pièce jointe"
                  ),
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
              },
              hr(cls := "aplus-hr-in-card--top-margin"),
              div(cls := "aplus-spacer fr_card__container")(
                button(
                  cls := "fr-btn fr-btn--secondary", 
                  attr("formaction") := ApplicationController.answerApplicationHasBeenSolved(application.id).url
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
        }
      ),
      inviteStructure(application, currentUser)
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
        if (status === Available) {
          val extension = metadata.filename.split('.').lastOption
          val filename = metadata.filename.split('.').dropRight(1).mkString(".")
          frag(
            div(cls := "aplus-attach-link aplus-spacer--top-small")(
              a(href := ApplicationController.file(metadata.id).url, filename),
              i(cls := "aplus-dl-arrow icon material-icons icon--light", "arrow_downward"),
            ),
            div(cls := "aplus-text-small aplus-text--gray")(
              s"${extension.get} - ${numberToSize(metadata.filesize)}",
            )
          )
        } else
          s"${metadata.filename}"
      }

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
      link,
    )
  }

  def organisationIcon(
      userId: UUID,
      creatorUserName: String,
      usersOrganisations: Map[UUID, List[Organisation.Id]]
  ): Tag = {
    val icons = Map(
      Organisation.poleEmploiId -> "pe",
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
