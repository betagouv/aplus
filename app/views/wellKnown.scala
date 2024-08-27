package views

import java.time.{LocalDate, ZoneOffset}
import java.time.format.DateTimeFormatter

object wellKnown {

  /** Reference: https://securitytxt.org/ */
  def securityTxt(): String = {
    val expiresInstant = LocalDate
      .now()
      .plusMonths(3)
      .plusYears(1)
      .withDayOfYear(1)
      .atTime(0, 0, 0)
      .atZone(ZoneOffset.UTC)
      .toInstant
    // ISO 8601 (like 2021-12-31T18:37:07.000Z)
    val expires = DateTimeFormatter.ISO_INSTANT.format(expiresInstant)

    s"""|Contact: mailto:support-incubateur@anct.gouv.fr
        |Canonical: https://aplus.beta.gouv.fr/.well-known/security.txt
        |Policy: https://incubateur.anct.gouv.fr/security-policy
        |Preferred-Languages: fr, en
        |Expires: $expires
        |""".stripMargin
  }

}
