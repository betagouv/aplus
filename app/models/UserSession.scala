package models

import java.time.Instant
import java.util.UUID

object UserSession {

  sealed abstract trait LoginType

  object LoginType {
    case object AgentConnect extends LoginType
  }

}

case class UserSession(
    id: String,
    userId: UUID,
    creationDate: Instant,
    lastActivity: Instant,
    loginType: UserSession.LoginType,
    expiresAt: Instant,
    isRevoked: Option[Boolean],
)
