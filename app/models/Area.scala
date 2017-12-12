package models

import java.util.UUID

import extentions.UUIDHelper

case class Area(id: UUID, name: String)

object Area {
  val all = List(
    Area(UUIDHelper.namedFrom("argenteuil"), "Argenteuil"),
    Area(UUIDHelper.namedFrom("besancon"), "Besançon"),
    Area(UUIDHelper.namedFrom("lons-le-saunoer"), "Lons-le-Saunier"),
    Area(UUIDHelper.namedFrom("perigueux"), "Périgueux"),
    Area(UUIDHelper.namedFrom("angers"), "Angers"),
    Area(UUIDHelper.namedFrom("nice"), "Nice"),
    Area(UUIDHelper.namedFrom("exemple"), "Demo")
  )
}