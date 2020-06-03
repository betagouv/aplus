package helper

import play.api.data.FormError

object PlayFormHelper {

  def prettifyFormError(formError: FormError): String = {
    val prettyKey = formError.key.split("\\.").lastOption.getOrElse("")
    val prettyMessages = formError.messages.flatMap(_.split("\\.").lastOption).mkString(", ")
    s"($prettyKey : $prettyMessages)"
  }

}
