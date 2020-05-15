package helper

import play.api.data.FormError
import play.api.libs.json.{JsPath, JsonValidationError}

object PlayFormHelper {

  def prettifyFormError(formError: FormError): String = {
    val prettyKey = formError.key.split("\\.").lastOption.getOrElse("")
    val prettyMessages = formError.messages.flatMap(_.split("\\.").lastOption).mkString(", ")
    s"($prettyKey : $prettyMessages)"
  }

  /** Uses `scala.collection.Seq` in its type to match the type returned by
    * Play Json
    *
    * Note: `errors` does not contain personal data, only the `JsPath` and
    *        a message about the validation that failed
    */
  def prettifyJsonFormInvalidErrors(
      errors: scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])]
  ): String =
    "Champs du formulaire invalides : " +
      errors
        .map {
          case (path, validationErrors) =>
            validationErrors.map(_.message).mkString(", ") +
              path.toJsonString
        }
        .mkString(" ; ")

}
