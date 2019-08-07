package models

import java.util.UUID

import extentions.UUIDHelper

case class Area(id: UUID, name: String)

object Area {
  def fromId(id: UUID) = all.find(_.id == id)

  def apply(id: String, name: String): Area = Area(UUIDHelper.namedFrom(id), name)

  val all = List(
    Area("nice", "Alpes-Maritimes"),
    Area("ardennes", "Ardennes"),
    Area("angers", "Angers"),
    Area("cahors", "Bassin de Cahors"),
    Area("besancon", "Besançon"),
    Area("bethune", "Bethune"),
    Area("calvados", "Calvados"),
    Area("coeur-du-perche", "Cœur du Perche"),
    Area("doubs", "Doubs"),
    Area("hautes-pyrénées", "Hautes-Pyrénées"),
    Area("hauts-de-seine", "Hauts-de-Seine"),
    Area("ille-et-vilaine", "Ille-et-Vilaine"),
    Area("jura", "Jura"),
    Area("loir-et-cher", "Loir-et-Cher"),
    Area("lons-le-saunoer", "Lons-le-Saunier"),
    Area("lyon", "Lyon"),
    Area("perigueux", "Périgueux"),
    Area("argenteuil", "Val-d'Oise"),
    Area("val-de-marne", "Val-de-Marne"),
    Area("vaucluse", "Vaucluse"),
    Area("var", "Var"),
    Area("vendee", "Vendée"),
    Area("exemple", "Demo")
  )

  val notApplicable = Area("notApplicable", "NotApplicable")
}
