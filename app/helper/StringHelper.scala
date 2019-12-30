package helper

import org.apache.commons.lang3.StringUtils

object StringHelper {
  def canonize(area: String): String =
    StringUtils.stripAccents(area.toLowerCase().replaceAll("[-'’ +]", ""))
}
