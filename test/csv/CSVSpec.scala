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

  val failFile: String =
    """Nom de l'utilisateur;Qualité de l'utilisateur;Email de l'utilisateur;Aidant;Instructeur;Responsable;Groupe(s);Territoire(s);Organisation du groupe;Description du groupe;Bal
      |;Monsieur;;Aidant;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe!;super.groupe@beta.gouv.fr""".stripMargin

  val prefFormat: String =
    """Opérateur partenaire,Nom Référent,Prénom Référent,Adresse e-mail,numéro de téléphone,"adresse mail générique (si nécessaire)"
      |CAF,Nom1,Prénom1,prenom1.nom1@cafaisne.cnafmail.fr,01.02.03.04.05,MFS.cafaisne@cafaisne.cnafmail.fr
      |CPAM,Nom2,Prénom2,prenom2.nom2@assurance-maladie.fr,01.02.03.04.05,_x0001_
      |CPAM,Nom3,Prénom3,prenom3.nom3@assurance-maladie.fr,01.02.03.04.05,
      |CARSAT,Nom4,Prénom4,prenom4.nom4@carsat-nordpicardie.fr,01.02.03.04.05,partenariatsretraite@carsat-nordpicardie.fr
      |DGFIP,Nom5,Prénom5,prenom5.nom5@dgfip.finances.gouv.fr,01.02.03.04.05,sip.laon@dgfip.finances.gouv.fr
      |DGFIP,Nom6,Prénom6,prenom6.nom6@dgfip.finances.gouv.fr,01.02.03.04.05,ddfip02.gestionfiscale@dgfip.finances.gouv.fr
      |DGFIP,Nom7,Prénom7,prenom7.nom7@dgfip.finances.gouv.fr,01.02.03.04.05,sip.soissons@dgfip.finances.gouv.fr
      |La Poste,Nom8,Prénom8,prenom8.nom8@laposte.fr,,
      |Min. Intérieur,Nom9,Prénom9,prenom9.nom9@aisne.gouv.fr,01.02.03.04.05,
      |Min. Justice,Nom10,Prénom10,prenom10.nom10@justice.fr,01.02.03.04.05,
      |MSA,Nom11,Prénom11,prenom11.nom11@picardie.msa.fr,01.02.03.04.05,
      |Pôle Emploi,Nom12,Prénom12,prenom12.nom12@pole-emploi.fr,,
      |,,,,,
      |""".stripMargin

  val organisationTest: String =
    """organisation,Nom de la structure labellisable,Adresse,Code Postal,Commune,Nom Agent,Prénom Agent,Contact mail Agent,Bal,"Coordonnées du référent préfectoral en charge du dossier France Services"
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,Nom1,Prénom1,test1@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,Nom2,Prénom2,test2@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,Nom3,Prénom3,test3@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,
      |MSAP,de Sancoins,38 rue de la croix blanche,18600,Sancoins,Nom4,Prénom4,test4@aubigny-sur-nere.fr,msap@sancoins.fr,
      |MSAP,de Sancoins,38 rue de la croix blanche,18600,Sancoins,Nom5,Prénom5,test5@aubigny-sur-nere.fr,msap@sancoins.fr,
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
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => groupId, () => userId, creatorId, dateTime,""))
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
        name = "Nom1 Prénom1",
        qualite = "",
        email = "prenom1.nom1@cafaisne.cnafmail.fr",
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
        phoneNumber = Some("01.02.03.04.05")
      ))
      list.filter(_.isRight) must have size 11
    }
    "be recognized with proper organisation" >> {
      val reader = CSVReader.open(Source.fromString(organisationTest))
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val creatorId = UUID.randomUUID()
      val dateTime = DateTime.now(Time.dateTimeZone)
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => userId, () => groupId, creatorId, dateTime,""))
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
      val list: List[Either[(List[FormError], String), (UserGroup, User)]] = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => groupId, () => userId, creatorId, dateTime, ""))
      list.filter(_.isLeft) must have size 1
    }
  }

  val csvFile: String =
    """Nom;Qualité;Email;Instructeur;Responsable;Territoires;Organisation du groupe;Groupe;Bal générique / fonctionnelle;Précisions / Commentaires
      |Nom1 Prénom1;Agent d’accueil FS;prenom1.nom1@france-service.com;;;Val-d’Oise;Maison France Services;MFS Saint Laurent;sfs.saint-laurent@laposte.com;
      |Nom2 Prénom2;Agent d’accueil FS;prenom2.nom2@laposte.com;;;Val-d’Oise;Maison France Services;MFS Saint Laurent;sfs.saint-laurent@laposte.com;
      |Nom3 Prénom3;Référent Pole Emploi;prenom3.nom3@pole-emploi.fr;Instructeur;;Ardennes;Pôle emploi;Pole Emploi Charleville Mézières;chareville-mezieres@pole.emploi.com;
      |Nom4 Prénom4;Réponsable CAF;prenom4.nom4@pole-emploi.fr;Instructeur;Responsable;Ardennes;Caisse d’allocations familiale;CAF Ardennes;ardennes@caf.fr;
      |Nom5 Prénom5;Responsable DDFIP;prenom5.nom5@ddfip.fr;;Responsable;Mayotte;Direction départementale des Finances publiques;DDFIP Mayotte (amendes);amendes@ddfip.fr;
      |Nom6 Prénom6;Responsable DDFIP;prenom6.nom6@ddfip.fr;Instructeur;;Mayotte;Direction départementale des Finances publiques;DDFIP Mayotte (Impots locaux);amendes@ddfip.fr;
      |;;;;;;;;;""".stripMargin

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
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => userId, () => groupId, creatorId, dateTime, ""))
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
      val list = reader.allWithHeaders().map(line => csv.fromCSVLine(line, csv.GROUP_HEADERS, csv.USER_HEADERS, () => groupId, () => userId, creatorId, dateTime, ""))
      list must have size 7
      list.head.right.get._2 must equalTo(User(id = userId,
        key = "key",
        name = "Nom1 Prénom1",
        qualite = "",
        email = "prenom1.nom1@france-service.com",
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
        name = "Nom2 Prénom2",
        qualite = "",
        email = "prenom2.nom2@laposte.com",
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
