package models

import java.util.UUID

import extentions.UUIDHelper

case class Area(id: UUID, name: String)

object Area {
  def fromId(id: UUID) = all.find(_.id == id)

  def apply(id: String, name: String): Area = Area(UUIDHelper.namedFrom(id), name)

  val all = List(
    Area("nice", "Alpes-Maritimes (06)"),
    Area("ardennes", "Ardennes (07)"),
    Area("angers", "Maine-et-loire (49)"),
    Area("cahors", "Lot (46)"),
    Area("bethune", "Pas-de-Calais (62)"),
    Area("calvados", "Calvados (14)"),
    Area("coeur-du-perche", "Orne (61)"),
    Area("doubs", "Doubs (25)"),
    Area("essonne", "Essonne (91)"),
    Area("hautes-pyrénées", "Hautes-Pyrénées (65)"),
    Area("hauts-de-seine", "Hauts-de-Seine (92)"),
    Area("ille-et-vilaine", "Ille-et-Vilaine (35)"),
    Area("jura", "Jura (39)"),
    Area("loir-et-cher", "Loir-et-Cher (41)"),
    Area("lyon", "Rhônes (69)"),
    Area("paris", "Paris (75)"),
    Area("perigueux", "Dordogne (24)"),
    Area("seine-saint-denis", "Seine-Saint-Denis (93)"),
    Area("argenteuil", "Val-d'Oise (95)"),
    Area("val-de-marne", "Val-de-Marne (94)"),
    Area("vaucluse", "Vaucluse (84)"),
    Area("var", "Var (83)"),
    Area("vendee", "Vendée (85)"),
    Area("exemple", "Demo")
  )

  val notApplicable = Area("notApplicable", "NotApplicable")
}
