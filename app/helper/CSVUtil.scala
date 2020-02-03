package helper

object CSVUtil {

  def escape(content: String): String =
    "\"" + content.filterNot(_ == '\n').replace("\"", "\"\"") + "\""
}
