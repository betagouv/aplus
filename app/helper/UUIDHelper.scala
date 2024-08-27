package helper

import java.util.UUID

object UUIDHelper {
  def randomUUID = UUID.randomUUID
  def namedFrom(name: String): UUID = UUID.nameUUIDFromBytes(name.getBytes())

  def fromString(string: String): Option[UUID] =
    scala.util.control.Exception.allCatch.opt(UUID.fromString(string))

}
