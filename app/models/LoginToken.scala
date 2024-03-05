package models

import helper.Time
import java.time.ZonedDateTime
import java.util.UUID
import scala.util.Random

case class LoginToken(
    token: String,
    origin: LoginToken.Origin,
    creationDate: ZonedDateTime,
    expirationDate: ZonedDateTime,
    ipAddress: String
) {
  lazy val isActive = expirationDate.isAfter(Time.nowParis())
}

object LoginToken {

  sealed trait Origin

  object Origin {
    case class User(userId: UUID) extends Origin
    case class Signup(signupId: UUID) extends Origin
  }

  def forSignupId(signupId: UUID, expirationInMinutes: Int, ipAddress: String) =
    LoginToken(
      Random.alphanumeric.take(20).mkString,
      Origin.Signup(signupId),
      Time.nowParis(),
      Time.nowParis().plusMinutes(expirationInMinutes.toLong),
      ipAddress
    )

  def forUserId(userId: UUID, expirationInMinutes: Int, ipAddress: String) =
    LoginToken(
      Random.alphanumeric.take(20).mkString,
      Origin.User(userId),
      Time.nowParis(),
      Time.nowParis().plusMinutes(expirationInMinutes.toLong),
      ipAddress
    )

}
