package helper

import de.mkammerer.argon2.Argon2Factory
import scala.util.Try

object PasswordHasher {

  /** See official usage doc: https://github.com/phxql/argon2-jvm#usage
    *
    * For recommended parameters:
    *   - https://github.com/phxql/argon2-jvm#recommended-parameters
    *   - https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#argon2id
    *   - https://github.com/P-H-C/phc-winner-argon2/blob/master/argon2-specs.pdf
    *
    * Also spring defaults as reference:
    * https://github.com/spring-projects/spring-security/blob/006b9b960797d279b31cf8c8d16f1549c5632b2c/crypto/src/main/java/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.java#L47
    */
  def hashAndWipe(password: Array[Char]): Try[String] = Try {
    val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    try {
      // OWASP recommended parameters
      // On desktop, those are optimal for x=50-100ms (argon2-specs.pdf section 9)
      argon2.hash(1, 47104, 1, password)
    } finally {
      argon2.wipeArray(password)
    }
  }

  def verifyAndWipe(password: Array[Char], hash: String): Try[Boolean] = Try {
    val argon2 = Argon2Factory.create()
    try {
      argon2.verify(hash, password)
    } finally {
      argon2.wipeArray(password)
    }
  }

}
