package helper

import java.security.MessageDigest

object Hash {

  def sha256(string: String): String =
    MessageDigest.getInstance("SHA-256").digest(string.getBytes).map("%02x".format(_)).mkString

}
