package models

import java.time.ZonedDateTime
import java.util.UUID

import helper.Time

import scala.util.Random

case class LoginToken(
    token: String,
    userId: UUID,
    creationDate: ZonedDateTime,
    expirationDate: ZonedDateTime,
    ipAddress: String
) {
  lazy val isActive = expirationDate.isAfter(Time.nowParis())
}

object LoginToken {

  def forUserId(userId: UUID, expirationInMinutes: Int, ipAddress: String) =
    new LoginToken(
      Random.alphanumeric.take(20).mkString,
      userId,
      Time.nowParis(),
      Time.nowParis().plusMinutes(expirationInMinutes),
      ipAddress
    )
}
