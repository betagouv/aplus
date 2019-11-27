package csv

import java.util.UUID

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.{CSVUtil, Time, UUIDHelper}
import models.{Organisation, User, UserGroup}
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.io.Source


@RunWith(classOf[JUnitRunner])
class CSVSpec extends Specification {

  val csvFile: String =
    """Nom;Qualité;Email;Instructeur;Responsable;Territoires;Organisation du groupe;Groupe;Bal générique / fonctionnelle;Précisions / Commentaires
      |Sabine MAIDE;Agent d’accueil FS;sabine.maide@france-service.com;;;Val-d’Oise;Maison France Services;MFS Saint Laurent;sfs.saint-laurent@laposte.com;
      |Marie-France SAIRVISSE;Agent d’accueil FS;Marie-france.sairvisse@laposte.com;;;Val-d’Oise;Maison France Services;MFS Saint Laurent;sfs.saint-laurent@laposte.com;
      |Jean PLOI;Référent Pole Emploi;jean.ploi@pole-emploi.fr;Instructeur;;Ardennes;Pôle emploi;Pole Emploi Charleville Mézières;chareville-mezieres@pole.emploi.com;
      |Jean DUCAFE;Réponsable CAF;jean.ducafe@caf.fr;Instructeur;Responsable;Ardennes;Caisse d’allocations familiale;CAF Ardennes;ardennes@caf.fr;
      |Anne TRESOR;Responsable DDFIP;Anne.tresor@ddfip.fr;;Responsable;Mayotte;Direction départementale des Finances publiques;DDFIP Mayotte (amendes);amendes@ddfip.fr;
      |Martin Paux;Responsable DDFIP;martin.paux@ddfip.fr;Instructeur;;Mayotte;Direction départementale des Finances publiques;DDFIP Mayotte (Impots locaux);amendes@ddfip.fr;
      |;;;;;;;;;""".stripMargin

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
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, GroupImport.HEADERS, UserImport.HEADERS, groupId, userId, creatorId, dateTime))
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
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, GroupImport.HEADERS, UserImport.HEADERS, userId, groupId, creatorId, dateTime))
      list must have size 7
      val result = list.head
      result.value.map(_._1) must beSome(UserGroup(id = userId,
        name = "MFS Saint Laurent",
        description = None,
        inseeCode = List.empty[String],
        creationDate = dateTime,
        createByUserId = creatorId,
        area = UUIDHelper.namedFrom("argenteuil"),
        organisation = Organisation.fromShortName("MFS").map(_.shortName),
        email = Some("sfs.saint-laurent@laposte.com")))
      list.flatMap(_.value).distinct must have size 6
    }

    "produce a valid users" >> {
      val reader = CSVReader.open(Source.fromString(csvFile))
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, GroupImport.HEADERS, UserImport.HEADERS, groupId, userId, creatorId,dateTime))
      list must have size 7
      list.head.value.map(_._2) must beSome(User(id = userId,
        key = "key",
        name = "Sabine MAIDE",
        qualite = "Agent d’accueil FS",
        email = "sabine.maide@france-service.com",
        helper = true,
        instructor = false,
        groupAdmin = false,
        admin = false,
        areas = List.empty[UUID],
        creationDate = dateTime,
        hasAcceptedCharte = false,
        communeCode = "0",
        disabled = false,
        groupIds = List.empty[UUID],
        delegations = Map.empty[String, String],
        cguAcceptationDate = None,
        newsletterAcceptationDate = None
      ))
      list(1).value.map(_._2) must beSome(User(id = userId,
        key = "key",
        name = "Marie-France SAIRVISSE",
        qualite = "Agent d’accueil FS",
        email = "Marie-france.sairvisse@laposte.com",
        helper = true,
        instructor = false,
        groupAdmin = false,
        admin = false,
        areas = List.empty[UUID],
        creationDate = dateTime,
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
