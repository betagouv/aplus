package models

import java.time.Instant
import java.util.UUID

object UserSession {

  sealed abstract trait LoginType

  object LoginType {
    case object MagicLink extends LoginType
    case object InsecureDemoKey extends LoginType
  }

}

case class UserSession(
    id: String,
    userId: UUID,
    creationDate: Instant,
    creationIpAddress: String,
    lastActivity: Instant,
    loginType: UserSession.LoginType,
    expiresAt: Instant,
    revokedAt: Option[Instant],
) {

  def isValid(now: Instant): Boolean =
    now.isBefore(expiresAt) && revokedAt.map(revokedAt => now.isBefore(revokedAt)).getOrElse(true)

}
