package models

import models.Organisation.Subject

case class Organisation(shortName: String,
                        name: String)

object Organisation {
  def fromShortName(shortName: String) = all.find(_.shortName == shortName)

  val all = List(
    Organisation("A+", "Administration+"),  // Nationale
    Organisation("ANAH", "Agence nationale de l'habitat"),
    Organisation("CAF", "Caisse d’allocations familiale"),  //Département
    Organisation("ANTS", "Agence nationale des titres sécurisés"),
    Organisation("CAF", "Caisse d’allocations familiale"),  //Département
    Organisation("BDF", "Banque de France"),
    Organisation("CAF", "Caisse d’allocations familiale"),  //Département
    Organisation("CARSAT", "Caisse d'assurance retraite et de la santé au travail"), //
    Organisation("CCAS", "Centre communal d'action sociale"), //Ville
    Organisation("CNAV", "Caisse nationale d'assurance vieillesse"),  //Département
    Organisation("CPAM", "Caisse primaire d'assurance maladie"), //Département
    Organisation("CRAM", "Caisse régionale d'assurance maladie"), //Région
    Organisation("DDFIP", "Direction départementale des Finances publiques"),//Département
    Organisation("Département", "Conseil départemental"),
    Organisation("DRFIP", "Direction régionale des Finances publiques"),//Région
    Organisation("Hôpital", "Hôpital"), //Ville
    Organisation("OFPRA", "Office français de protection des réfugiés et apatrides"), //Nationale
    Organisation("Mairie", "Mairie"),  //Ville
    Organisation("MDPH", "Maison départementale des personnes handicapées"),
    Organisation("Mission locale", "Mission locale"), //Ville
    Organisation("MSA", "Mutualité sociale agricole"),
    Organisation("Pôle emploi", "Pôle emploi"),
    Organisation("Préf", "Préfecture"),  //Département
    Organisation("Sous-Préf", "Sous-préfecture")
  )

  case class Subject(subject: String, organisations: Seq[Organisation]) {
    override def toString: String = subject
  }
  case class Category(name: String, description: String, defaultOrganisations: Seq[Organisation], subjects: Seq[Subject]) {
    override def toString: String = name
  }

  object Category {
    val all = List(
      Category("Social et famille", "APL, RSA, ...", List("CAF","MSA").flatMap(fromShortName),
        List(
          Subject("Trop perçu à la suite d'un contrôle CAF", List("CAF").flatMap(fromShortName)),
          Subject("Long délais de versement de prestation sociale", List("CAF","MSA").flatMap(fromShortName))
        )
      ),
      Category("Santé et Handicap", "CMU, AME, ACS, Affiliation, Reconnaissance Handicap...", List("CPAM","CRAM","MSA","MDPH").flatMap(fromShortName),
        List(
          Subject("Première inscription à l'assurance maladie", List("CPAM","CRAM","MSA").flatMap(fromShortName)),
          Subject("Renouvellement de CMU-C / ACS", List("CPAM","CRAM").flatMap(fromShortName)), // MSA ?
          Subject("Aide médicale de l'état pour les personnes sans papier", List("CPAM","CRAM").flatMap(fromShortName)), // MSA ?
          Subject("Reconnaissance Handicap", List("MDPH").flatMap(fromShortName)), // MSA ?
        )
      ),
      Category("Emploi","Recherche d'emploi, allocation chomage, ...", List("Pôle emploi","Mission locale").flatMap(fromShortName),
        List(

        )
      ),
      Category("Logement","Logement d'urgence, Droit au Logement...", List("CAF","MSA","Préf","Sous-Préf","Mairie").flatMap(fromShortName),
        List(

        )
      ),
      Category("Retraite","Droits retraite, versement, ...", List("CNAV","CARSAT","MSA").flatMap(fromShortName),
        List(

        )
      ),
      Category("Impôts / Argent", "Déclaration impôt, précarité financière, ...", List("DDFIP","DRFIP","BDF").flatMap(fromShortName),
        List(

        )
      ),
      Category("Papier et Titres","Titre de séjour, carte d'identité, passport, ...", List("Préf","Sous-Préf","Mairie").flatMap(fromShortName),
        List(

        )
      ),
      Category("Autre", "Vous ne savez pas quoi choisir", List("A+").flatMap(fromShortName),
        List(

        )
      )
    )
  }
}