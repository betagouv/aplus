package helper

import cats.syntax.all._

object CSVUtil {

  def escape(content: String): String =
    "\"" + content.filterNot(_ === '\n').replace("\"", "\"\"") + "\""

}
