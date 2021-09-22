package helper

object EmailHelper {

  /** In the creation of the email, the raw strings in `play.api.libs.mailer.Email` that are
    * supposed to be email addresses are sent without escaping to the constructor of the class
    * `javax.mail.internet.InternetAddress` which will throw an exception for ill-formatted strings.
    * Address spec: https://tools.ietf.org/html/rfc822#section-6
    *
    * We want to avoid invalid `phrase`: https://tools.ietf.org/html/rfc822#section-3.3
    *
    * Useful bits of the BNF:
    * ```
    *     phrase      =  1*word                       ; Sequence of words
    *     word        =  atom / quoted-string
    *     atom        =  1*<any CHAR except specials, SPACE and CTLs>
    *     quoted-string = <"> *(qtext/quoted-pair) <">; Regular qtext or
    *     qtext       =  <any CHAR excepting <">,     ; => may be folded
    *                     "\" & CR, and including
    *                     linear-white-space>
    * ```
    *
    * Note: this implementation is not at all RFC complient. It should pass the constructor of
    * `InternetAddress`.
    */
  def quoteEmailPhrase(phrase: String): String = {
    // Remove controls
    val noControls = """\p{Cc}""".r.replaceAllIn(phrase, "")
    val specialsOrSpace =
      Set[Char]('(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '.', '[', ']', ' ')
    val mustBeQuoted = noControls.exists(specialsOrSpace.contains)
    if (mustBeQuoted) {
      val noQuotesNorBackslashes = """[\\"]""".r.replaceAllIn(noControls, "")
      "\"" + noQuotesNorBackslashes + "\""
    } else {
      noControls
    }
  }

}
