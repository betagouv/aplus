package models

import helper.StringHelper.CanonizeString

case class Organisation(id: Organisation.Id, shortName: String, name: String)

object Organisation {

  case class Id(id: String) extends AnyVal {
    override def toString = id.stripSpecialChars
  }

  object Id {

    import play.api.data.format.Formatter

    implicit object OrganisationIdFormatter extends Formatter[Organisation.Id] {
      import play.api.data.format.Formats._
      override val format = Some(("format.organisation.id", Nil))

      override def bind(key: String, data: Map[String, String]) =
        parsing(Organisation.Id(_), "error.organisation.id", Nil)(key, data)

      override def unbind(key: String, value: Organisation.Id) =
        Map(key -> value.id)

    }

    implicit val organisationIdAnormParser: anorm.Column[Organisation.Id] =
      implicitly[anorm.Column[String]].map(Organisation.Id.apply)

  }

  def apply(shortName: String, name: String): Organisation =
    Organisation(
      id = Organisation.Id(shortName),
      shortName = shortName,
      name = name
    )

  def isValidId(id: Organisation.Id): Boolean =
    byId(id).nonEmpty

  def fromShortName(shortName: String): Option[Organisation] = {
    val standardShortName = shortName.stripSpecialChars
    all.find(_.shortName.stripSpecialChars == standardShortName)
  }

  def byId(id: Id): Option[Organisation] =
    all.find(org => (org.id: Id) == (id: Id))

  val franceServicesId = Organisation.Id("MFS")
  val franceServices = Organisation(franceServicesId, "FS", "France Services")
  val msap = Organisation("MSAP", "Maison de services au public")
  val hopital = Organisation("Hôpital", "Hôpital")

  /** Note: checklist when adding an `Organisation`
    * - alphabetical order
    * - when one name contains another, there is a 'hack' in `deductedFromName`
    *   (check for failing cases)
    */
  val all = List(
    Organisation("ANAH", "Agence nationale de l'habitat"),
    Organisation("ANTS", "Agence nationale des titres sécurisés"),
    Organisation("BDF", "Banque de France"),
    Organisation("CAF", "Caisse d’allocations familiale"), //Département
    Organisation("CARSAT", "Caisse d'assurance retraite et de la santé au travail"), //
    Organisation("CCAS", "Centre communal d'action sociale"), //Ville
    Organisation("CDAD", "Conseils départementaux d'accès au droit"), //Département
    Organisation("CNAV", "Caisse nationale d'assurance vieillesse"), //Département
    Organisation("CPAM", "Caisse primaire d'assurance maladie"), //Département
    Organisation("CNAM", "Caisse nationale d'assurance maladie"),
    Organisation("CRAM", "Caisse régionale d'assurance maladie"), //Région
    Organisation("DDFIP", "Direction départementale des Finances publiques"), //Département
    Organisation("Département", "Conseil départemental"),
    Organisation("DRFIP", "Direction régionale des Finances publiques"), //Région
    hopital, //Ville
    Organisation("OFPRA", "Office français de protection des réfugiés et apatrides"), //Nationale
    Organisation("La Poste", "La Poste"),
    Organisation("Mairie", "Mairie"), //Ville
    Organisation("MDPH", "Maison départementale des personnes handicapées"),
    franceServices,
    Organisation("Mission locale", "Mission locale"), //Ville
    Organisation("MSA", "Mutualité sociale agricole"),
    msap, // Ville
    Organisation("Pôle emploi", "Pôle emploi"),
    Organisation("Préf", "Préfecture"), //Département
    Organisation("Sous-Préf", "Sous-préfecture"),
    Organisation(
      "URSSAF",
      "Unions de Recouvrement des cotisations de Sécurité Sociale et d’Allocations Familiales"
    )
  )

  val organismesAidants: List[Organisation] = List(franceServices, msap, hopital)

  val organismesOperateurs: List[Organisation] =
    all.filter(!organismesAidants.contains[Organisation](_))

  case class Subject(subject: String, organisations: Seq[Organisation])

  case class Category(
      name: String,
      description: String,
      defaultOrganisations: Seq[Organisation],
      subjects: Seq[Subject]
  )

  def deductedFromName(name: String): Option[Organisation] = {
    val lowerCaseName = name.toLowerCase().stripSpecialChars
    // Hack: `.reverse` the orgs so we can match first MSAP before MSA and
    // Sous-Préf before Préf
    all.reverse
      .find { organisation =>
        lowerCaseName.contains(organisation.shortName.toLowerCase().stripSpecialChars) ||
        lowerCaseName.contains(organisation.name.toLowerCase().stripSpecialChars)
      }
  }

}
