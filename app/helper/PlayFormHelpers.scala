package helper

import helper.StringHelper.commonStringInputNormalization
import play.api.data.{Form, FormError, Mapping}
import play.api.data.Forms.{optional, text}
import play.api.data.validation.{Constraint, Valid}
import play.api.i18n.MessagesProvider
import play.api.libs.json.{JsPath, JsonValidationError}

object PlayFormHelpers {

  val normalizedText: Mapping[String] =
    text.transform[String](commonStringInputNormalization, commonStringInputNormalization)

  val normalizedOptionalText: Mapping[Option[String]] =
    optional(text).transform[Option[String]](
      _.map(commonStringInputNormalization).filter(_.nonEmpty),
      _.map(commonStringInputNormalization).filter(_.nonEmpty)
    )

  def inOption[T](constraint: Constraint[T]): Constraint[Option[T]] =
    Constraint[Option[T]](constraint.name, constraint.args) {
      case None    => Valid
      case Some(t) => constraint(t)
    }

  def prettifyFormError(formError: FormError): String = {
    val prettyKey = formError.key.split("\\.").lastOption.getOrElse("")
    val prettyMessages = formError.messages.flatMap(_.split("\\.").lastOption).mkString(", ")
    s"($prettyKey : $prettyMessages)"
  }

  /** According to Play's source, `error.format` does not contain form values (this is what we want
    * here)
    */
  def formErrorsLog(formWithErrors: Form[_])(implicit messages: MessagesProvider): String =
    formWithErrors.errors
      .map(error => "['" + error.key + "' => " + error.format + "]")
      .mkString(" ")

  /** Uses `scala.collection.Seq` in its type to match the type returned by Play Json
    *
    * Note: `errors` does not contain personal data, only the `JsPath` and a message about the
    * validation that failed
    */
  def prettifyJsonFormInvalidErrors(
      errors: scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])]
  ): String =
    "Champs du formulaire invalides : " +
      errors
        .map { case (path, validationErrors) =>
          validationErrors.map(_.message).mkString(", ") +
            path.toJsonString
        }
        .mkString(" ; ")

}
