package helper

import cats.syntax.all._
import java.text.Normalizer
import org.apache.commons.lang3.StringUtils

object StringHelper {

  // Note: `.r` is supposed to compile the regex
  val notLetterNorNumberRegex = """[^\p{L}\p{N}]+""".r

  def stripEverythingButLettersAndNumbers(string: String): String =
    StringUtils.stripAccents(notLetterNorNumberRegex.replaceAllIn(string, "").toLowerCase)

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

  val oneOrMoreSpacesRegex = """\p{Z}+""".r

  def stripSpaces(string: String): String =
    oneOrMoreSpacesRegex.replaceAllIn(string, "")

  /** "  " => " " */
  def mergeSpacesToOne(string: String): String =
    oneOrMoreSpacesRegex.replaceAllIn(string, " ")

  /** Notably: will merge letter+accent together (C in NFKC) and convert some weird unicode
    * letters to compatible letters (K in NFKC).
    */
  def normalizeNFKC(string: String): String =
    Normalizer.normalize(string, Normalizer.Form.NFKC)

  /** This is a "common" normalization for untrusted inputs.
    * 1. Unicode NFKC
    * 2. Merge multiple spaces in one
    * 3. Trim left an right whitespaces
    */
  def commonStringInputNormalization(string: String): String =
    mergeSpacesToOne(normalizeNFKC(string)).trim

  def capitalizeName(name: String): String =
    """[\P{P}&&\P{Z}]+""".r.replaceAllIn(name, m => m.group(0).toLowerCase.capitalize)

  final object NonEmptyTrimmedString {
    def unapply(s: String): Option[String] = s.some.map(_.trim).filter(_.nonEmpty)
  }

  implicit class StringOps(s: String) {
    def normalized = StringHelper.commonStringInputNormalization(s)
    def capitalizeWords = StringHelper.capitalizeName(s)
  }

  implicit class StringListOps(list: List[String]) {

    def mkStringIfNonEmpty(start: String, sep: String, end: String) = {
      val s = if (list.nonEmpty) start else ""
      val e = if (list.nonEmpty) end else ""

      list.mkString(s, sep, e)
    }

  }

}
