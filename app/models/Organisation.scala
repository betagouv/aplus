package models



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
    Organisation("CDAD", "Conseils départementaux d'accès au droit"), //Département
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
    Organisation("MFS", "Maison France Services"),
    Organisation("Mission locale", "Mission locale"), //Ville
    Organisation("MSA", "Mutualité sociale agricole"),
    Organisation("MSAP", "Maison de services au public"), // Ville
    Organisation("Pôle emploi", "Pôle emploi"),
    Organisation("Préf", "Préfecture"),  //Département
    Organisation("Sous-Préf", "Sous-préfecture")
  )

  case class Subject(subject: String, organisations: Seq[Organisation])
  case class Category(name: String, description: String, defaultOrganisations: Seq[Organisation], subjects: Seq[Subject])
}
