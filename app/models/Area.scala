package models

import java.util.UUID

import extentions.UUIDHelper

case class Area(id: UUID, name: String)

object Area {
  val all = List(
    Area(UUIDHelper.namedFrom("argenteuil"), "Argenteuil"),
    Area(UUIDHelper.namedFrom("besancon"), "Besançon"),
    Area(UUIDHelper.namedFrom("exemple"), "Demo")
  )
}