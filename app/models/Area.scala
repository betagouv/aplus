package models

import cats.Eq
import cats.syntax.all._
import helper.StringHelper.CanonizeString
import helper.UUIDHelper
import java.util.UUID

case class Area(id: UUID, name: String, inseeCode: String) {
  override def toString: String = s"$name ($inseeCode)"
}

object Area {

  implicit val Eq: Eq[Area] = (x: Area, y: Area) => x.id === y.id

  def fromId(id: UUID): Option[Area] =
    if (id === allArea.id) {
      Some(allArea)
    } else {
      all.find(area => area.id === id)
    }

  def searchFromName(name: String): Option[Area] = {
    val strippedName = name.stripSpecialChars
    Area.all.find(area => area.name.stripSpecialChars === strippedName)
  }

  def apply(id: String, name: String, inseeCode: String): Area =
    Area(UUIDHelper.namedFrom(id), name, inseeCode)

  val ain: Area = Area("Ain", "Ain", "01")
  val calvados: Area = Area("calvados", "Calvados", "14")
  val demo: Area = Area("exemple", "Demo", "-1")

  val allExcludingDemo = List(
    ain,
    Area("Aisne", "Aisne", "02"),
    Area("Allier", "Allier", "03"),
    Area("Alpes-de-Haute-Provence", "Alpes-de-Haute-Provence", "04"),
    Area("nice", "Alpes-Maritimes", "06"),
    Area("Ardeche", "Ardèche", "07"),
    Area("ardennes", "Ardennes", "08"),
    Area("Ariege", "Ariège", "09"),
    Area("Aube", "Aube", "10"),
    Area("Aude", "Aude", "11"),
    Area("Aveyron", "Aveyron", "12"),
    Area("Bas-Rhin", "Bas-Rhin", "67"),
    Area("bouches-du-rhone", "Bouches-du-Rhône", "13"),
    calvados,
    Area("Cantal", "Cantal", "15"),
    Area("Charente", "Charente", "16"),
    Area("Charente-Maritime", "Charente-Maritime", "17"),
    Area("Cher", "Cher", "18"),
    Area("Correze", "Corrèze", "19"),
    Area("Corse-du-Sud", "Corse-du-Sud", "2A"),
    Area("Cote-d'Or", "Côte-d’Or", "21"),
    Area("Cotes-d'Armor", "Côtes-d’Armor", "22"),
    Area("Creuse", "Creuse", "23"),
    Area("Deux-Sevres", "Deux-Sèvres", "79"),
    Area("dordogne", "Dordogne", "24"),
    Area("doubs", "Doubs", "25"),
    Area("Drome", "Drôme", "26"),
    Area("essonne", "Essonne", "91"),
    Area("Eure", "Eure", "27"),
    Area("Eure-et-Loir", "Eure-et-Loir", "28"),
    Area("Finistere", "Finistère", "29"),
    Area("Gard", "Gard", "30"),
    Area("Gers", "Gers", "32"),
    Area("Gironde", "Gironde", "33"),
    Area("Guadeloupe", "Guadeloupe", "971"),
    Area("Guyane", "Guyane", "973"),
    Area("Haut-Rhin", "Haut-Rhin", "68"),
    Area("Haute-Corse", "Haute-Corse", "2B"),
    Area("Haute-Garonne", "Haute-Garonne", "31"),
    Area("Haute-Loire", "Haute-Loire", "43"),
    Area("Haute-Marne", "Haute-Marne", "52"),
    Area("Haute-Savoie", "Haute-Savoie", "74"),
    Area("Haute-Saone", "Haute-Saône", "70"),
    Area("Haute-Vienne", "Haute-Vienne", "87"),
    Area("Hautes-Alpes", "Hautes-Alpes", "05"),
    Area("hautes-pyrénées", "Hautes-Pyrénées", "65"),
    Area("hauts-de-seine", "Hauts-de-Seine", "92"),
    Area("Herault", "Hérault", "34"),
    Area("ille-et-vilaine", "Ille-et-Vilaine", "35"),
    Area("Indre", "Indre", "36"),
    Area("Indre-et-Loire", "Indre-et-Loire", "37"),
    Area("Isere", "Isère", "38"),
    Area("jura", "Jura", "39"),
    Area("La Reunion", "La Réunion", "974"),
    Area("Landes", "Landes", "40"),
    Area("loir-et-cher", "Loir-et-Cher", "41"),
    Area("Loire", "Loire", "42"),
    Area("Loire-Atlantique", "Loire-Atlantique", "44"),
    Area("Loiret", "Loiret", "45"),
    Area("cahors", "Lot", "46"),
    Area("Lot-et-Garonne", "Lot-et-Garonne", "47"),
    Area("Lozere", "Lozère", "48"),
    Area("maine-et-loire", "Maine-et-loire", "49"),
    Area("Manche", "Manche", "50"),
    Area("Marne", "Marne", "51"),
    Area("Martinique", "Martinique", "972"),
    Area("Mayenne", "Mayenne", "53"),
    Area("Mayotte", "Mayotte", "976"),
    Area("Meurthe-et-Moselle", "Meurthe-et-Moselle", "54"),
    Area("Meuse", "Meuse", "55"),
    Area("Morbihan", "Morbihan", "56"),
    Area("Moselle", "Moselle", "57"),
    Area("Nievre", "Nièvre", "58"),
    Area("Nord", "Nord", "59"),
    Area("Oise", "Oise", "60"),
    Area("coeur-du-perche", "Orne", "61"),
    Area("paris", "Paris", "75"),
    Area("bethune", "Pas-de-Calais", "62"),
    Area("Puy-de-Dome", "Puy-de-Dôme", "63"),
    Area("Pyrenees-Atlantiques", "Pyrénées-Atlantiques", "64"),
    Area("Pyrenees-Orientales", "Pyrénées-Orientales", "66"),
    Area("lyon", "Rhône", "69"),
    Area("Saone-et-Loire", "Saône-et-Loire", "71"),
    Area("Sarthe", "Sarthe", "72"),
    Area("Savoie", "Savoie", "73"),
    Area("Seine-Maritime", "Seine-Maritime", "76"),
    Area("seine-saint-denis", "Seine-Saint-Denis", "93"),
    Area("Seine-et-Marne", "Seine-et-Marne", "77"),
    Area("Somme", "Somme", "80"),
    Area("Tarn", "Tarn", "81"),
    Area("Tarn-et-Garonne", "Tarn-et-Garonne", "82"),
    Area("Territoire de Belfort", "Territoire de Belfort", "90"),
    Area("argenteuil", "Val-d’Oise", "95"),
    Area("val-de-marne", "Val-de-Marne", "94"),
    Area("var", "Var", "83"),
    Area("vaucluse", "Vaucluse", "84"),
    Area("vendee", "Vendée", "85"),
    Area("Vienne", "Vienne", "86"),
    Area("Vosges", "Vosges", "88"),
    Area("Yonne", "Yonne", "89"),
    Area("Yvelines", "Yvelines", "78"),
  )

  val all = allExcludingDemo ::: List(demo)

  val allArea = Area(UUIDHelper.namedFrom("all"), "tous les territoires", "0")

  val notApplicable = Area("notApplicable", "NotApplicable", "-1")

  private lazy val inseeCodeToAreaMap: Map[String, Area] =
    all.map(area => (area.inseeCode, area)).toMap

  def fromInseeCode(inseeCode: String): Option[Area] = inseeCodeToAreaMap.get(inseeCode)

}
