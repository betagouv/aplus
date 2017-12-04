package extentions

import java.security.MessageDigest

object Hash {
  def sha256(string: String) = MessageDigest.getInstance("SHA-256").digest(string.getBytes).map("%02x".format(_)).mkString
  def md5(string: String) = MessageDigest.getInstance("MD5").digest(string.getBytes).map("%02x".format(_)).mkString
}
