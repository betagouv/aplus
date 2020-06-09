package models

import java.time.ZonedDateTime

sealed trait Sms {
  def apiId: Sms.ApiId
  def creationDate: ZonedDateTime
  def body: String
}

object Sms {

  case class ApiId(underlying: String)

  case class PhoneNumber(internationalPhoneNumber: String) {

    require(
      PhoneNumber.internationalNumberRegex.matches(internationalPhoneNumber), {
        // The phone number could not be parsed:
        // we log enough info to debug, but we avoid logging personal infos
        val nrOfChars: Int = internationalPhoneNumber.size
        val first2Chars: String = internationalPhoneNumber.take(2).toString
        val isDigitsOnly: Boolean = """\d+""".r.matches(internationalPhoneNumber)
        val errorMessage: String =
          "Impossible de lire le numéro de téléphone. " +
            s"Caractéristiques du numéro: $nrOfChars caractères, commençant par $first2Chars " +
            (if (isDigitsOnly) "et ne comportant que des chiffres"
             else "et composé d'autre chose que des chiffres")
        errorMessage
      }
    )

    /** Example: ("+31612345678": PhoneNumber) => ("31612345678": String)  */
    def numberWithoutPlus: String = internationalPhoneNumber.stripPrefix("+")

    /** Example: ("+31612345678": PhoneNumber) => ("0612345678": String) */
    def toLocalPhoneFrance: String =
      "0" + internationalPhoneNumber.drop(internationalPhoneNumber.size - 9)

  }

  object PhoneNumber {

    private val internationalNumberRegex = """\+[0-9]+""".r

    /** Example: ("0612345678": String) => ("+31612345678": PhoneNumber)  */
    def fromLocalPhoneFrance(localPhoneFrance: String): PhoneNumber =
      PhoneNumber("+33" + localPhoneFrance.drop(1))

  }

  case class Outgoing(
      apiId: ApiId,
      creationDate: ZonedDateTime,
      recipient: PhoneNumber,
      body: String
  ) extends Sms

  case class Incoming(
      apiId: ApiId,
      creationDate: ZonedDateTime,
      originator: PhoneNumber,
      body: String
  ) extends Sms

}
