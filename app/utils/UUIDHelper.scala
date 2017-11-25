package utils

object UUIDHelper {
  def randomUUID = java.util.UUID.randomUUID
  def namedFrom(name: String) = java.util.UUID.nameUUIDFromBytes(name.getBytes())
  def fromString(string: String) = scala.util.control.Exception.allCatch.opt(java.util.UUID.fromString(string))
}