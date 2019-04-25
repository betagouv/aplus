package models

import java.util.UUID

import extentions.UUIDHelper

case class Area(id: UUID, name: String)

object Area {
  val all = List(
    Area(UUIDHelper.namedFrom("nice"), "Alpes-Maritimes"),
    Area(UUIDHelper.namedFrom("argenteuil"), "Argenteuil"),
    Area(UUIDHelper.namedFrom("angers"), "Angers"),
    Area(UUIDHelper.namedFrom("cahors"), "Bassin de Cahors"),
    Area(UUIDHelper.namedFrom("besancon"), "Besançon"),
    Area(UUIDHelper.namedFrom("bethune"), "Bethune"),
    Area(UUIDHelper.namedFrom("coeur-du-perche"), "Cœur du Perche"),
    Area(UUIDHelper.namedFrom("doubs"), "Doubs"),
    Area(UUIDHelper.namedFrom("ecouen"), "Écouen"),
    Area(UUIDHelper.namedFrom("hauts-de-seine"),"Hauts-de-Seine"),
    Area(UUIDHelper.namedFrom("jura"), "Jura"),
    Area(UUIDHelper.namedFrom("loir-et-cher"), "Loir-et-Cher"),
    Area(UUIDHelper.namedFrom("lons-le-saunoer"), "Lons-le-Saunier"),
    Area(UUIDHelper.namedFrom("lyon"), "Lyon"),
    Area(UUIDHelper.namedFrom("perigueux"), "Périgueux"),
    Area(UUIDHelper.namedFrom("val-de-marne"), "Val-de-Marne"),
    Area(UUIDHelper.namedFrom("var"), "Var"),
    Area(UUIDHelper.namedFrom("exemple"), "Demo")
  )
}
