package csv

import java.util.UUID

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.{CSVUtil, UUIDHelper}
import models.{Area, User, UserGroup}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.io.Source

@RunWith(classOf[JUnitRunner])
class CSVSpec extends Specification {

  val csvFile: String =
    """Nom de l'utilisateur;Qualité de l'utilisateur;Email de l'utilisateur;Aidant;Instructeur;Responsable;Groupe(s);Territoire(s);Organisation du groupe;Description du groupe;Bal
      |Lucien Pereira;Monsieur;lucien.pereira@beta.gouv.fr;Aidant;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe!;super.groupe@beta.gouv.fr
      |Roxanne Duchamp;Madame;roxanne.duchamp@beta.gouv.fr;Aidant;;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe!;super.groupe@beta.gouv.fr
      |John Ben;Monsieur;john.ben@beta.gouv.fr;;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe 2!;super.groupe2@beta.gouv.fr
      |Li June;Madame;li.june@beta.gouv.fr;Aidant;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe 2!;super.groupe2@beta.gouv.fr
      |Géraline Kaplant;Big boss;geraldine.kaplant@beta.gouv.fr;Aidant;Instructeur;;Group -1045109618;Alpes-Maritimes (06);;Ceci est une desc;Group.1045109618@beta.gouv.fr""".stripMargin

  val excelFile: String =
      """Nom de l'utilisateur;Qualité de l'utilisateur;Email de l'utilisateur;Aidant;Instructeur;Responsable;Territoire(s);Groupe(s);Bal
        |Sabine MAIDE;Agent d’accueil FS;sabine.maide@laposte.com;Aidant;;;Val-d’Oise;MFS Saint Laurent;mfs.saint-laurent@laposte.com
        |Jean MAIDE;Agent d’accueil FS;jean.maide@laposte.com;Aidant;;;Val-d’Oise;MFS Saint Laurent;mfs.saint-laurent@laposte.com
        |Paul LAPOSTE;Référent La Poste Ardennes;pau.laposte@laposte.com;;Instructeur;;Ardennes;La Poste Ardennes;
        |Jeanne LAPOSTE;Référent La Poste Ardennes;jeanne.laposte@laposte.com;;Instructeur;;Ardennes,Jura;La Poste Ardennes;
        |Nathan LAPOSTE;Référent La Poste Nationnal;nathan.laposte@laposte.com;;Instructeur;;Tous;La Poste;
        |Anne LAPOSTE;Réponsable La Poste;anne.laposte@laposte.com;;;Responsable;Ardennes;La Poste Ardennes;""".stripMargin

  val failFile: String =
    """Nom de l'utilisateur;Qualité de l'utilisateur;Email de l'utilisateur;Aidant;Instructeur;Responsable;Groupe(s);Territoire(s);Organisation du groupe;Description du groupe;Bal
      |;Monsieur;;Aidant;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe!;super.groupe@beta.gouv.fr""".stripMargin

  "The 'ap;l\"us' string should" >> {
    "be escaped as '\"ap;l\"\"us\"'" >> {
      CSVUtil.escape("ap;l\"us") mustEqual "\"ap;l\"\"us\""
    }
  }

  "The failFile string should" >> {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = csv.SEPARATOR.charAt(0)
    }
    "produce 3 errors" >> {
      val reader = CSVReader.open(Source.fromString(failFile))
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, GroupImport.HEADERS, UserImport.HEADERS))
      list must have size 1
      val result = list.head
      result.errors must have size 3
      result.value.map(_._1) must beNone
    }
  }

  "The csvFile string should" >> {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = csv.SEPARATOR.charAt(0)
    }

    "produce valid groups" >> {
      val reader = CSVReader.open(Source.fromString(csvFile))
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, GroupImport.HEADERS, UserImport.HEADERS))
      list must have size 5
      val result = list.head
      result.value.map(_._1) must beSome(UserGroup(id = csv.deadbeef,
        name = "SuperGroupe",
        description = None,
        inseeCode = List.empty[String],
        creationDate = null,
        createByUserId = null,
        area = Area.allArea.id, // TODO correct
        organisation = None,
        email = Some("super.groupe@beta.gouv.fr")))
      list.flatMap(_.value).distinct must have size 5
    }

    "produce a valid users" >> {
      val reader = CSVReader.open(Source.fromString(csvFile))
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, GroupImport.HEADERS, UserImport.HEADERS))
      list must have size 5
      list.head.value.map(_._2) must beSome(User(id = csv.deadbeef,
        key = "key",
        name = "Lucien Pereira",
        qualite = "Monsieur",
        email = "lucien.pereira@beta.gouv.fr",
        helper = true,
        instructor = true,
        groupAdmin = false,
        admin = false,
        areas = List.empty[UUID],
        creationDate = null,
        hasAcceptedCharte = false,
        communeCode = "0",
        disabled = false,
        groupIds = List.empty[UUID],
        delegations = Map.empty[String, String],
        cguAcceptationDate = None,
        newsletterAcceptationDate = None
      ))
      list(1).value.map(_._2) must beSome(User(id = csv.deadbeef,
        key = "key",
        name = "Roxanne Duchamp",
        qualite = "Madame",
        email = "roxanne.duchamp@beta.gouv.fr",
        helper = true,
        instructor = false,
        groupAdmin = false,
        admin = false,
        areas = List.empty[UUID],
        creationDate = null,
        hasAcceptedCharte = false,
        communeCode = "0",
        disabled = false,
        groupIds = List.empty[UUID],
        delegations = Map.empty[String, String],
        cguAcceptationDate = None,
        newsletterAcceptationDate = None
      ))
    }
  }
}
