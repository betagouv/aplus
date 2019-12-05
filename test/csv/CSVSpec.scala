package csv

import java.util.UUID

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.{CSVUtil, Time, UUIDHelper}
import models.{Area, Organisation, User, UserGroup}
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.data.FormError

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

  val prefFormat: String =
    """Opérateur partenaire,Nom Référent,Prénom Référent,Adresse e-mail,numéro de téléphone,"adresse mail générique (si nécessaire)"
      |CAF,BOUTS,Stéphanie,stephanie.bouts@cafaisne.cnafmail.fr,03.23.75.60.27,MFS.cafaisne@cafaisne.cnafmail.fr
      |CPAM,GLADIEUX,Fabrice,fabrice.gladieux@assurance-maladie.fr,03 23 26 23 40,_x0001_
      |CPAM,BEAURAIN,Nathalie,nathalie.beaurain@assurance-maladie.fr,03 23 65 43 85,
      |CARSAT,BOURQUIN,Cathy,cathy.bourquin@carsat-nordpicardie.fr,03 20 05 61 10,partenariatsretraite@carsat-nordpicardie.fr
      |DGFIP,THEVENIN,JEAN-LUC,jean-luc.thevenin@dgfip.finances.gouv.fr,03 23 26 28 07,sip.laon@dgfip.finances.gouv.fr
      |DGFIP,CLAISSE,FLORENCE,florence.claisse@dgfip.finances.gouv.fr,03.23.26.70.16,ddfip02.gestionfiscale@dgfip.finances.gouv.fr
      |DGFIP,HOBART,FREDERIC,frederic.hobart@dgfip.finances.gouv.fr,03 23 76 49 19,sip.soissons@dgfip.finances.gouv.fr
      |La Poste,QUENTIN,Laure,laure.quentin@laposte.fr,,
      |Min. Intérieur,REMIOT,Christine,christine.remiot@aisne.gouv.fr,03 23 21 82 49,
      |Min. Justice,SAUVEZ,Clothilde,cdad-aisne@justice.fr,03 60 81 30 10,
      |MSA,FONTE NOVA,Sandrine,fonte-nova.sandrine@picardie.msa.fr,0322826334,
      |Pôle Emploi,LACOMBLEZ,Christelle,christelle.lacomblez@pole-emploi.fr,,
      |,,,,,
      |""".stripMargin

  val organisationTest: String =
    """organisation,Nom de la structure labellisable,Adresse,Code Postal,Commune,Nom Agent,Prénom Agent,Contact mail Agent,Bal,"Coordonnées du référent préfectoral en charge du dossier France Services"
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,LAURENT,Florence,flo@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,COURCELLES,Carine,carine@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,AOUTIN,Catherine,catherine@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,
      |MSAP,de Sancoins,38 rue de la croix blanche,18600,Sancoins,LAGNEAU,Julie,julie@aubigny-sur-nere.fr,msap@sancoins.fr,
      |MSAP,de Sancoins,38 rue de la croix blanche,18600,Sancoins,BARTHELEMY,Déborah,deborah@aubigny-sur-nere.fr,msap@sancoins.fr,
      |""".stripMargin

  "The 'ap;l\"us' string should" >> {
    "be escaped as '\"ap;l\"\"us\"'" >> {
      CSVUtil.escape("ap;l\"us") mustEqual "\"ap;l\"\"us\""
    }
  }

  "The format of from the prefecture should" >> {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = ','
    }
    "be recognized" >> {
      val reader = CSVReader.open(Source.fromString(prefFormat))
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => groupId, () => userId, creatorId, dateTime))
      val result = list.head
      result.right.get._1 must equalTo(UserGroup(id = groupId,
        name = "CAF",
        description = None,
        inseeCode = List.empty[String],
        creationDate = dateTime,
        createByUserId = creatorId,
        area = Area.allArea.id,
        organisation = None,
        email = Some("MFS.cafaisne@cafaisne.cnafmail.fr")))

      result.right.get._2 must equalTo(User(id = userId,
        key = "key",
        name = "Stéphanie BOUTS",
        qualite = "",
        email = "stephanie.bouts@cafaisne.cnafmail.fr",
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
        newsletterAcceptationDate = None,
        phoneNumber = Some("03.23.75.60.27")
      ))
      list.filter(_.isRight) must have size 11
    }
    "be recognized with proper organisation" >> {
      val reader = CSVReader.open(Source.fromString(organisationTest))
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => userId, () => groupId, creatorId, dateTime))
      val result = list.head
      result.right.get._1 must equalTo(UserGroup(id = userId,
        name = "d’Aubigny sur Nère",
        description = None,
        inseeCode = List.empty[String],
        creationDate = dateTime,
        createByUserId = creatorId,
        area = Area.allArea.id,
        organisation = Some("MSAP"),
        email = Some("msap@aubigny-sur-nere.fr")))
      list.filter(_.isRight) must have size 5
    }
  }

  "The failFile string should" >> {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = csv.SEPARATOR.charAt(0)
    }
    "produce 1 errors" >> {
      val reader = CSVReader.open(Source.fromString(failFile))
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list: List[Either[List[FormError], (UserGroup, User)]] = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => groupId, () => userId, creatorId, dateTime))
      list.filter(_.isLeft) must have size 1
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
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => userId, () => groupId, creatorId, dateTime))
      list must have size 7
      val result = list.head
      result.right.get._1 must equalTo(UserGroup(id = userId,
        name = "MFS Saint Laurent",
        description = None,
        inseeCode = List.empty[String],
        creationDate = dateTime,
        createByUserId = creatorId,
        area = UUIDHelper.namedFrom("argenteuil"),
        organisation = Organisation.fromShortName("MFS").map(_.shortName),
        email = Some("sfs.saint-laurent@laposte.com")))
      list.filter(_.isRight) must have size 6
    }

    "produce a valid users" >> {
      val reader = CSVReader.open(Source.fromString(csvFile))
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => groupId, () => userId, creatorId, dateTime))
      list must have size 7
      list.head.right.get._2 must equalTo(User(id = userId,
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
      list(1).right.get._2 must equalTo(User(id = userId,
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
