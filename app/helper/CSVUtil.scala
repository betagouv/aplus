package helper

import cats.implicits.catsSyntaxEq

object CSVUtil {

  def escape(content: String): String =
    "\"" + content.filterNot(_ === '\n').replace("\"", "\"\"") + "\""

}
