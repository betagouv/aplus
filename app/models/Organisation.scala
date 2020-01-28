package models

import helper.StringHelper.CanonizeString


case class Organisation(shortName: String,
                        name: String) {
  def key: String = shortName.canonize
}

object Organisation {

  def fromShortName(shortName: String) = {
    val standardShortName = shortName.canonize
    all.find(_.shortName.canonize == standardShortName)
  }

  def fromName(name: String) = {
    val standardName = name.canonize
    all.find(_.name.canonize == standardName)
  }
  
  val all = List(
    Organisation("A+", "Administration+"),  // Nationale
    Organisation("ANAH", "Agence nationale de l'habitat"),
    Organisation("ANTS", "Agence nationale des titres sécurisés"),
    Organisation("BDF", "Banque de France"),
    Organisation("CAF", "Caisse d’allocations familiale"),  //Département
    Organisation("CARSAT", "Caisse d'assurance retraite et de la santé au travail"), //
    Organisation("CCAS", "Centre communal d'action sociale"), //Ville
    Organisation("CDAD", "Conseils départementaux d'accès au droit"), //Département
    Organisation("CNAV", "Caisse nationale d'assurance vieillesse"),  //Département
    Organisation("CPAM", "Caisse primaire d'assurance maladie"), //Département
    Organisation("CNAM", "Caisse nationale d'assurance maladie"),
    Organisation("CRAM", "Caisse régionale d'assurance maladie"), //Région
    Organisation("DDFIP", "Direction départementale des Finances publiques"),//Département
    Organisation("Département", "Conseil départemental"),
    Organisation("DRFIP", "Direction régionale des Finances publiques"),//Région
    Organisation("Hôpital", "Hôpital"), //Ville
    Organisation("OFPRA", "Office français de protection des réfugiés et apatrides"), //Nationale
    Organisation("La Poste", "La Poste"),
    Organisation("Mairie", "Mairie"),  //Ville
    Organisation("MDPH", "Maison départementale des personnes handicapées"),
    Organisation("MFS", "Maison France Services"),
    Organisation("Mission locale", "Mission locale"), //Ville
    Organisation("MSA", "Mutualité sociale agricole"),
    Organisation("MSAP", "Maison de services au public"), // Ville
    Organisation("Pôle emploi", "Pôle emploi"),
    Organisation("Préf", "Préfecture"),  //Département
    Organisation("Sous-Préf", "Sous-préfecture")
  )

  val organisationGrouping: List[Set[Organisation]] = List(
    Set("DDFIP", "DRFIP").flatMap(Organisation.fromShortName),
    Set("CPAM", "CRAM", "CNAM").flatMap(Organisation.fromShortName),
    Set("CARSAT", "CNAV").flatMap(Organisation.fromShortName)) ++
    List("A+", "ANAH", "ANTS", "BDF", "CAF", "CCAS", "CDAD", "Caisse régionale d'assurance maladie", "Département", "Hôpital",
      "OFPRA", "La Poste", "Mairie", "MDPH", "MFS", "Mission locale", "MSA", "MSAP", "Pôle emploi", "Préf", "Sous-Préf",
      "Sous-préfecture").flatMap(Organisation.fromShortName).map(organisation => Set(organisation))


  case class Subject(subject: String, organisations: Seq[Organisation])
  case class Category(name: String, description: String, defaultOrganisations: Seq[Organisation], subjects: Seq[Subject])
}
