package csv

import cats.syntax.all._
import helper.{CSVUtil, Time, UUIDHelper}
import models.{Area, Organisation, UserGroup}
import org.junit.runner.RunWith
import org.specs2.matcher.{TypedEqual => _}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import serializers.UserAndGroupCsvSerializer
import serializers.UserAndGroupCsvSerializer.UserGroupBlock

@RunWith(classOf[JUnitRunner])
class CSVSpec extends Specification {

  val failFile: String =
    """Nom de l'utilisateur;Qualité de l'utilisateur;Email de l'utilisateur;Aidant;Instructeur;Responsable;Groupe(s);Territoire(s);Organisation du groupe;Description du groupe;Bal
      |;Monsieur;;Aidant;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe!;super.groupe@beta.gouv.fr""".stripMargin

  val prefFormat: String =
    """Opérateur partenaire,Nom Référent,Prénom Référent,Email,numéro de téléphone,"adresse mail générique (si nécessaire)",Compte partagé
      |CAF,Nom1,Prénom1,prenom1.nom1@cafaisne.cnafmail.fr,01.02.03.04.05,MFS.cafaisne@cafaisne.cnafmail.fr,
      |CPAM,Nom2,Prénom2,prenom2.nom2@assurance-maladie.fr,01.02.03.04.05,_x0001_,
      |CPAM,Nom3,Prénom3,prenom3.nom3@assurance-maladie.fr,01.02.03.04.05,,
      |CARSAT,Nom4,Prénom4,prenom4.nom4@carsat-nordpicardie.fr,01.02.03.04.05,partenariatsretraite@carsat-nordpicardie.fr,
      |DGFIP,Nom5,Prénom5,prenom5.nom5@dgfip.finances.gouv.fr,01.02.03.04.05,sip.laon@dgfip.finances.gouv.fr,
      |DGFIP,Nom6,Prénom6,prenom6.nom6@dgfip.finances.gouv.fr,01.02.03.04.06,ddfip02.gestionfiscale@dgfip.finances.gouv.fr,
      |DGFIP,Nom7,Prénom7,prenom7.nom7@dgfip.finances.gouv.fr,01.02.03.04.07,sip.soissons@dgfip.finances.gouv.fr,
      |La Poste,Nom8,Prénom8,prenom8.nom8@laposte.fr,,,
      |Min. Intérieur,Nom9,Prénom9,prenom9.nom9@aisne.gouv.fr,01.02.03.04.05,,
      |Min. Justice,Nom10,Prénom10,prenom10.nom10@justice.fr,01.02.03.04.05,,
      |MSA,Nom11,Prénom11,prenom11.nom11@picardie.msa.fr,01.02.03.04.05,,
      |Pôle Emploi,Nom12,Prénom12,prenom12.nom12@pole-emploi.fr,,,
      |,,,,,,
      |""".stripMargin

  // TODO : In the future, manage "Nom de la structure labellisable"
  val organisationTest: String =
    """organisation,Groupe,Adresse,Code Postal,Commune,Nom Agent,Prénom Agent,Contact mail Agent,Bal,"Coordonnées du référent préfectoral en charge du dossier France Services",Compte partagé
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,Nom1,Prénom1,test1@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,,
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,Nom2,Prénom2,test2@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,,
      |MSAP,d’Aubigny sur Nère,6 rue du 8 mai 1945,18700,Aubigny sur Nère,Nom3,Prénom3,test3@aubigny-sur-nere.fr,msap@aubigny-sur-nere.fr,,
      |MSAP,de Sancoins,38 rue de la croix blanche,18600,Sancoins,Nom4,Prénom4,test4@aubigny-sur-nere.fr,msap@sancoins.fr,,
      |MSAP,de Sancoins,38 rue de la croix blanche,18600,Sancoins,Nom5,Prénom5,test5@aubigny-sur-nere.fr,msap@sancoins.fr,,
      |""".stripMargin

  "The 'ap;l\"us' string should" >> {
    "be escaped as '\"ap;l\"\"us\"'" >> {
      CSVUtil.escape("ap;l\"us") mustEqual "\"ap;l\"\"us\""
    }
  }

  "The format of from the prefecture should" >> {
    "be recognized" >> {
      val result: Either[String, (List[String], List[UserGroupBlock])] =
        UserAndGroupCsvSerializer.csvLinesToUserGroupData(
          separator = ',',
          defaultAreas = List(Area.fromId(UUIDHelper.namedFrom("ardennes"))).flatten
        )(prefFormat)
      result must beRight

      val (errors, data) = result.toOption.get
      errors must have size 1
      data must have size 9

      val expectedUserGroup = UserGroup(
        id = UUIDHelper.namedFrom("id"),
        name = "DGFIP - Ardennes",
        description = None,
        inseeCode = Nil,
        creationDate = Time.nowParis(),
        areaIds = List(Area.fromId(UUIDHelper.namedFrom("ardennes"))).flatten.map(_.id),
        organisationId = None,
        email = Some("sip.laon@dgfip.finances.gouv.fr"),
        isInFranceServicesNetwork = true,
        publicNote = None,
        internalSupportComment = None
      )

      val dgfip = data.find(formData => formData.group.name.eqv(expectedUserGroup.name))
      dgfip must beSome

      dgfip.get.group.name must equalTo(expectedUserGroup.name)
      dgfip.get.group.description must equalTo(expectedUserGroup.description)
      dgfip.get.group.areaIds must equalTo(expectedUserGroup.areaIds)
      dgfip.get.group.organisationId must equalTo(expectedUserGroup.organisationId)
      dgfip.get.group.email must equalTo(expectedUserGroup.email)
      dgfip.get.users must have size 3

      val expectedEmail = "prenom5.nom5@dgfip.finances.gouv.fr"
      val expectedPhoneNumber = "01.02.03.04.05"
      dgfip.get.users.head.userData.firstName.orEmpty must equalTo("Prénom5")
      dgfip.get.users.head.userData.lastName.orEmpty must equalTo("Nom5")
      dgfip.get.users.head.userData.email must equalTo(expectedEmail)
      dgfip.get.users.head.userData.phoneNumber must beSome(expectedPhoneNumber)
    }

    "be recognized with proper organisation" >> {
      val result: Either[String, (List[String], List[UserGroupBlock])] =
        UserAndGroupCsvSerializer.csvLinesToUserGroupData(
          separator = ',',
          defaultAreas = List(Area.fromId(UUIDHelper.namedFrom("ardennes"))).flatten
        )(organisationTest)
      result must beRight
      val (errors, data) = result.toOption.get
      errors must have size 0
      val group = data.find(_.group.name.eqv("d’Aubigny sur Nère - Ardennes"))
      group must beSome
      group.get.group.organisationId must beSome(Organisation.Id("MSAP"))
      data must have size 2
    }
  }

  "The failFile string should" >> {
    "produce 1 errors" >> {
      val result: Either[String, (List[String], List[UserGroupBlock])] =
        UserAndGroupCsvSerializer.csvLinesToUserGroupData(
          separator = ';',
          defaultAreas = List(Area.fromId(UUIDHelper.namedFrom("ardennes"))).flatten
        )(failFile)
      result must beRight
      val (errors, _) = result.toOption.get
      errors must have size 1
    }
  }

  val csvFile: String =
    """Nom;Qualité;Email;Instructeur;Responsable;Territoires;Organisation du groupe;Groupe;Bal générique / fonctionnelle;Précisions / Commentaires;Compte partagé
      |Nom1 Prénom1;Agent d’accueil FS;prenom1.nom1@france-service.com;;;Val-d’Oise;Maison France Services;MFS Saint Laurent;sfs.saint-laurent@laposte.com;;
      |Nom2 Prénom2;Agent d’accueil FS;prenom2.nom2@laposte.com;;;Val-d’Oise;Maison France Services;MFS Saint Laurent;sfs.saint-laurent@laposte.com;;
      |Nom3 Prénom3;Référent Pole Emploi;prenom3.nom3@pole-emploi.fr;Instructeur;;Ardennes;Pôle emploi;Pole Emploi Charleville Mézières;chareville-mezieres@pole.emploi.com;;
      |Nom4 Prénom4;Réponsable CAF;prenom4.nom4@pole-emploi.fr;Instructeur;Responsable;Ardennes;Caisse d’allocations familiale;CAF Ardennes;ardennes@caf.fr;;
      |Nom5 Prénom5;Responsable DDFIP;prenom5.nom5@ddfip.fr;;Responsable;Mayotte;Direction départementale des Finances publiques;DDFIP Mayotte (amendes);amendes@ddfip.fr;;
      |Nom6 Prénom6;Responsable DDFIP;prenom6.nom6@ddfip.fr;Instructeur;;Mayotte;Direction départementale des Finances publiques;DDFIP Mayotte (Impots locaux);amendes@ddfip.fr;;
      |;;;;;;;;;;""".stripMargin

  "The csvFile string should" >> {
    "produce valid groups" >> {
      val result: Either[String, (List[String], List[UserGroupBlock])] =
        UserAndGroupCsvSerializer.csvLinesToUserGroupData(
          separator = ';',
          defaultAreas = List(Area.fromId(UUIDHelper.namedFrom("ardennes"))).flatten
        )(csvFile)
      result must beRight
      val (errors, data) = result.toOption.get
      errors must have size 0
      data must have size 5
    }

    "produce a valid users" >> {
      val result: Either[String, (List[String], List[UserGroupBlock])] =
        UserAndGroupCsvSerializer.csvLinesToUserGroupData(
          separator = ';',
          defaultAreas = List(Area.fromId(UUIDHelper.namedFrom("ardennes"))).flatten
        )(csvFile)
      result must beRight
      val (errors, data) = result.toOption.get
      errors must have size 0
      data.flatMap(_.users.map(_.userData)) must have size 6
    }
  }
}
