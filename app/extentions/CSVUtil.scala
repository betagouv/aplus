package extentions

object CSVUtil {

  def escape(content: String): String = {
    "\"" + content.replace("\"", "\"\"") + "\""
  }
}
