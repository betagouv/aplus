package models

import java.util.UUID

import org.joda.time.DateTime

import scala.util.Random

case class LoginToken(token: String,
                      userId: UUID,
                      creationDate: DateTime,
                      expirationDate: DateTime) {
  lazy val isActive = expirationDate.isAfterNow
}

object LoginToken {
  def forUserId(userId: UUID, expirationInMinutes: Int) =
    new LoginToken(Random.alphanumeric.take(20).mkString,
      userId,
      DateTime.now(Time.dateTimeZone),
      DateTime.now(Time.dateTimeZone).plusMinutes(expirationInMinutes))
}