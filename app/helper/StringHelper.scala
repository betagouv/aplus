package helper

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
    name
      .split("[-\\s]")
      .map(_.toLowerCase.capitalize)
      .mkString("-")

}
