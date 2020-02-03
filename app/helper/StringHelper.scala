package helper

import org.apache.commons.lang3.StringUtils

object StringHelper {

  implicit class CanonizeString(string: String) {

    def stripSpecialChars: String =
      StringUtils.stripAccents(string.toLowerCase().replaceAll("[-'’ +]", ""))
  }

  def camelToUnderscoresUpperCase(name: String) =
    "_?[A-Z][a-z\\d]+".r
      .findAllMatchIn(name)
      .map(_.group(0).toLowerCase)
      .mkString("_")
      .toUpperCase()
}
