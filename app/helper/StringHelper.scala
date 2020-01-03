package helper

import org.apache.commons.lang3.StringUtils

object StringHelper {
  implicit class CanonizeString(string: String) {
    def canonize: String =
      StringUtils.stripAccents(string.toLowerCase().replaceAll("[-'’ +]", ""))
  }
}
