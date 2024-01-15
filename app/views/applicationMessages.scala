package views

import scalatags.Text.all._
import models.{ User, Authorization, Application }
import views.helpers.applications.statusTag
import java.time.format.DateTimeFormatter
import scala.annotation.meta.field


object applicationMessages {
  def page(currentUser: User, userRights: Authorization.UserRights, application: Application) : Tag = div()(
    div(cls := "fr-grid-row aplus-text-small")(
      div(cls := "fr-col fr-col-4 ")(
        div()(
          span(cls := "fr-card-title-text aplus-title aplus-bold")(
            s"#${application.internalId}",
          ),
          statusTag(currentUser, application)
        ),
        div()(
          s"Créée le ${application.creationDate.format(DateTimeFormatter.ofPattern("dd/mm/yyyy"))} par :"
        ),
        div()(
            s"${application.userInfos.get(Application.UserFirstNameKey).get} ${application.userInfos.get(Application.UserLastNameKey).get}",
        )
      ),
      div(cls := "fr-col fr-col-4")(
        div()(
          span(cls := "aplus-nowrap")(
            i(cls := "material-icons material-icons-outlined aplus-icons-small")("mail"),
            s"${application.answers.length} messages",        
          ) 
        ),
        div()(
          "Participants à la discussion :"
        )
      ),
      div(cls := "fr-col fr-col-4")(
        button(cls := "fr-btn fr-btn--height-fix fr-btn--secondary")("J’ai traité la demande")
      )
    ),
    div(cls := "aplus-spacer")(
      strong(application.subject)
    ),
    hr(cls := "aplus-hr-in-card"),
    application.answers.map(answer => 
    
      div(cls := "fr_card fr_dard--darker aplus-paragraph")(
        div(cls := "fr_card__container")(
          div(cls := "aplus-text-small")(
            s"${application.userInfos.get(Application.UserFirstNameKey).get} ${application.userInfos.get(Application.UserLastNameKey).get}",
          ),
          div(cls := "aplus-text-small")(
            s"Le ${answer.creationDate.format(DateTimeFormatter.ofPattern("dd/mm/yyyy"))}"
          )
        ),
        answer.message
      )
    ),
    div(cls := "aplus-spacer")(
      form(cls := "aplus-form")(
        div(cls := "fr-input-group")(
          textarea(cls := "fr-input", `type` := "text", placeholder := "Répondre à la demande"),
        ),
        legend(cls := "fr-fieldset__legend")("Pièces jointes"),
        div(cls := "fr-fieldset__content")(
          div(cls := "fr-input-group")(
            input(cls := "fr-input", `type` := "file", placeholder := "Ajouter une pièce jointe"),
            button(cls := "fr-btn fr-btn--secondary")("Ajouter")
          )
        ),
      
        div(cls := "fr-radio-group  fr_card__container")(
          input(cls := "fr-radio", `type` := "radio", name := "radio", id := "radio-1", checked := "checked"),
          label(cls := "fr-label", `for` := "radio-1")("Je m’assigne à cette demande"),
        ),
        div(cls := "fr-radio-group fr_card__container")(
          input(cls := "fr-radio", `type` := "radio", name := "radio", id := "radio-2"),
          label(cls := "fr-label", `for` := "radio-2")("Mon organisme n’est pas compétent pour traiter cette demande"),
        ),
        
        div(cls := "aplus-spacer fr_card__container")(
          button(cls := "fr-btn fr-btn--secondary")("J’ai traité la demande"),
          button(cls := "fr-btn fr-btn--secondary")("+ Inviter un organisme"),
          button(cls := "fr-btn")("Répondre")
        )
      )
    )
  )
}