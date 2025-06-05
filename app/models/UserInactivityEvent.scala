package models

import cats.kernel.Eq
import cats.syntax.all._
import java.time.Instant
import java.util.UUID

case class UserInactivityEvent(
    id: UUID,
    userId: UUID,
    eventType: UserInactivityEvent.EventType,
    eventDate: Instant,
    lastActivityReferenceDate: Instant
)

object UserInactivityEvent {
  sealed trait EventType

  object EventType {

    @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
    implicit val eq: Eq[EventType] = (x: EventType, y: EventType) => x == y

    case object InactivityReminder1 extends EventType
    case object InactivityReminder2 extends EventType
    case object Deactivation extends EventType

  }

}
