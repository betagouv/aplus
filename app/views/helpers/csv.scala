package views.helpers

import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import scalatags.Text.all._

object csv {

  val errorsId: String = s"all-errors"
  def groupTitleId(groupIndex: Int): String = s"group-index-$groupIndex"

  def allErrors(form: Form[_])(implicit messagesProvider: Messages): Frag =
    if (form.hasErrors)
      div(
        id := errorsId,
        cls := "mdl-cell mdl-cell--12-col",
        form.errors.map { error =>
          displayError(error)
        }
      )
    else
      div(id := errorsId)

  private val groupIndexRegex = """^groups\[(\d+)\].*""".r

  def displayError(error: FormError)(implicit messagesProvider: Messages): Frag = {
    val linkToGroup: Frag =
      error.key match {
        case groupIndexRegex(rawIndex) =>
          val index = rawIndex.toInt
          a(
            cls := "mdl-navigation__link aplus-color-text--black",
            href := s"#${groupTitleId(index)}",
            i(cls := "material-icons", role := "presentation", "arrow_downward")
          )
        case _ =>
          ()
      }

    div(
      cls := "notification notification--error",
      div(
        cls := "mdl-cell mdl-cell--12-col single--align-items-center-important",
        linkToGroup,
        s"${error.format}",
        s" (Champ du formulaire : ${error.key})",
        if (error.args.isEmpty) ""
        else " (Arguments : " + error.args.mkString(",") + ")"
      )
    )
  }

}
