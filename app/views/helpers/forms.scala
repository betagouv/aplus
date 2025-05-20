package views.helpers

import cats.syntax.all._
import play.api.data.{Field, Form}
import play.api.i18n.MessagesProvider
import play.api.mvc.{Flash, RequestHeader}
import scalatags.Text.all._
import views.helpers.common.contactLink

// Note: Play's implementation of Twirl import is here:
// https://github.com/playframework/playframework/tree/2.8.7/core/play/src/main/scala/views/helper
object forms {

  // From Play's impl
  // https://github.com/playframework/playframework/blob/2.8.7/web/play-filters-helpers/src/main/scala/views/html/helper/CSRF.scala
  def CSRFInput(implicit request: RequestHeader): Tag = {
    val token = views.html.helper.CSRF.getToken
    // Note: to protect against XSS attack, Play escapes the value before using a raw
    //       html fragment.
    //       We do not need to do that, as scalatags is supposed to escape attribute values
    //       (escaping like Play does would create a double escaping)
    input(
      `type` := "hidden",
      name := token.name,
      value := token.value
    )
  }

  val flashErrorKey = "error"

  def flashError(implicit flash: Flash): Frag =
    flash.get(flashErrorKey) match {
      case None => ()
      case Some(error) =>
        flashErrorOuter(
          frag(
            div(b(error)),
            div(
              "Si l’erreur persiste vous pouvez ",
              contactLink("contacter l’équipe A+"),
              ".",
            )
          )
        )
    }

  val flashErrorRawHtmlKey = "error-raw-html"

  def flashErrorRawHtml(implicit flash: Flash): Frag =
    flash.get(flashErrorRawHtmlKey) match {
      case None        => ()
      case Some(error) => flashErrorOuter(raw(error))
    }

  def flashErrorOuter(inner: Frag): Tag =
    div(
      cls := "mdl-cell mdl-cell--12-col",
      id := "answer-error",
      div(
        cls := "notification notification--error",
        inner
      )
    )

  val flashSuccessKey = "success"

  def flashSuccess(implicit flash: Flash): Frag =
    flash.get(flashSuccessKey) match {
      case None => ()
      case Some(message) =>
        flashSuccessOuter(
          frag(
            div(
              cls := "notification__message",
              message
            ),
            button(
              cls := "notification__close-btn mdl-button mdl-js-button mdl-button--icon",
              i(cls := "material-icons", "close")
            )
          )
        )
    }

  val flashSuccessRawHtmlKey = "success-raw-html"

  def flashSuccessRawHtml(implicit flash: Flash): Frag =
    flash.get(flashSuccessRawHtmlKey) match {
      case None          => ()
      case Some(message) => flashSuccessOuter(raw(message))
    }

  def flashSuccessOuter(inner: Frag): Tag =
    div(
      cls := "mdl-cell mdl-cell--12-col",
      div(
        cls := "notification notification--success",
        inner
      )
    )

  def displayFormGlobalErrors(form: Form[_])(implicit messages: MessagesProvider): Frag =
    form.hasErrors.some
      .filter(identity)
      .map(_ =>
        div(
          cls := "mdl-cell mdl-cell--12-col",
          div(
            cls := "mdl-grid notification notification--error",
            p(cls := "mdl-cell mdl-cell--12-col", "Le formulaire comporte des erreurs."),
            form.hasGlobalErrors.some
              .filter(identity)
              .map(_ =>
                ul(
                  cls := "mdl-cell mdl-cell--12-col",
                  frag(form.globalErrors.map(error => li(error.format)))
                )
              )
          )
        )
      )

  def selectInput(
      field: Field,
      fieldLabel: String,
      isMandatory: Boolean,
      options: List[Tag],
      fieldId: Option[String] = None,
      outerDivClass: String = "mdl-cell mdl-cell--12-col",
      innerDivClass: String = "single--margin-top-8px single--margin-bottom-16px",
  )(implicit messages: MessagesProvider): Tag = {
    val selectId: String = fieldId.getOrElse[String](field.id)
    div(
      cls := outerDivClass,
      div(
        cls := innerDivClass,
        div(
          label(
            field.hasErrors.some.filter(identity).map(_ => cls := "mdl-color-text--red-A700"),
            `for` := selectId,
            fieldLabel
          ),
          if (isMandatory) frag(" ", span(cls := "mdl-color-text--red-A700", "*")) else (),
          br,
          select(
            cls := "single--margin-top-8px",
            id := selectId,
            name := field.name,
            frag(options)
          )
        ),
        field.hasErrors.some
          .filter(identity)
          .map(_ =>
            span(
              cls := "mdl-color-text--red-A700 single--font-size-12px",
              field.errors.map(_.format).mkString(", ")
            )
          )
      )
    )
  }

  def textInput(field: Field, fieldId: String, fieldLabel: String, isMandatory: Boolean)(implicit
      messages: MessagesProvider
  ): Tag =
    div(
      cls := "single--margin-top-16px single--margin-bottom-8px",
      div(
        label(
          field.hasErrors.some.filter(identity).map(_ => cls := "mdl-color-text--red-A700"),
          `for` := fieldId,
          fieldLabel
        ),
        if (isMandatory) frag(" ", span(cls := "mdl-color-text--red-A700", "*")) else (),
        br,
        minimalTextInput(
          field,
          input(
            cls := "mdl-textfield__input mdl-color--white",
            `type` := "text",
            // Not putting required here, because it is bad UX to show a red field on first load
            name := field.name,
            id := fieldId,
            field.value.map(value := _)
          ),
          fieldId,
          fieldLabel = None,
          classes = List("mdl-textfield--fix single--margin-top--8px")
        )
      )
    )

  /** Note: Twirl impl is in app/views/helpers/input.scala.html (replaces default
    * defaultFieldConstructor.scala.html)
    *
    * Default impl is at
    * https://github.com/playframework/playframework/blob/2.8.x/core/play/src/main/scala/views/helper/defaultFieldConstructor.scala.html
    */
  def minimalTextInput(
      field: Field,
      innerInput: Modifier,
      fieldId: String,
      fieldLabel: Option[String] = None,
      classes: List[String] = Nil,
      floated: Boolean = true
  )(implicit messages: MessagesProvider): Tag =
    div(
      cls := (List[Option[String]](
        "mdl-textfield".some,
        "mdl-js-textfield".some,
        if (floated) "mdl-textfield--floating-label".some else none,
        if (field.hasErrors) "is-invalid".some else none
      ).flatten ::: classes).mkString(" "),
      innerInput,
      fieldLabel.map(l => label(cls := "mdl-textfield__label", `for` := fieldId, l)),
      field.hasErrors.some
        .filter(identity)
        .map(_ => span(cls := "mdl-textfield__error", field.errors.map(_.format).mkString(", ")))
    )

}
