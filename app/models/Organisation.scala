package models

import cats.Eq
import cats.syntax.all._
import helper.StringHelper.CanonizeString
import play.api.data.FormError

case class Organisation(id: Organisation.Id, shortName: String, name: String)

object Organisation {

  case class Id(id: String) extends AnyVal {
    override def toString = id.stripSpecialChars
  }

  object Id {

    import play.api.data.format.Formatter

    implicit val Eq: Eq[Id] = (x: Id, y: Id) => x.id === y.id

    implicit object OrganisationIdFormatter extends Formatter[Organisation.Id] {
      import play.api.data.format.Formats._
      override val format: Option[(String, Seq[Any])] = Some(("format.organisation.id", Nil))

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Id] =
        parsing(Organisation.Id(_), "error.organisation.id", Nil)(key, data)

      override def unbind(key: String, value: Organisation.Id): Map[String, String] =
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
    all.find(_.shortName.stripSpecialChars === standardShortName)
  }

  private lazy val byIdMap = all.map(organisation => (organisation.id, organisation)).toMap
  def byId(id: Id): Option[Organisation] = byIdMap.get(id)

  val association: Organisation = Organisation("Association", "Association")
  val bdf: Organisation = Organisation("BDF", "Banque de France")
  val cafId: Id = Organisation.Id("CAF")
  val caf: Organisation = Organisation(cafId, "CAF", "Caisse d’allocations familiales")
  val carsatId: Id = Organisation.Id("CARSAT")

  val carsat: Organisation =
    Organisation(carsatId, "CARSAT", "Caisse d'assurance retraite et de la santé au travail")

  val ccas: Organisation = Organisation("CCAS", "Centre communal d'action sociale")
  val cpamId: Id = Organisation.Id("CPAM")
  val cpam: Organisation = Organisation(cpamId, "CPAM", "Caisse primaire d'assurance maladie")
  val cnavId: Id = Organisation.Id("CNAV")
  val cnav: Organisation = Organisation(cnavId, "CNAV", "Caisse nationale d'assurance vieillesse")
  val cnamId: Id = Organisation.Id("CNAM")
  val cramId: Id = Organisation.Id("CRAM")
  val cram: Organisation = Organisation(cramId, "CRAM", "Caisse régionale d'assurance maladie")
  val ddfipId: Id = Organisation.Id("DDFIP")

  val ddfip: Organisation = Organisation(
    ddfipId,
    "DDFIP",
    "Direction départementale des Finances publiques"
  )

  val departement: Organisation = Organisation("Département", "Conseil départemental")

  val dilaId: Id = Organisation.Id("DILA")

  val dila: Organisation =
    Organisation(dilaId, "DILA", "Direction de l’information légale et administrative")

  val drfipId: Id = Organisation.Id("DRFIP")

  val drfip: Organisation =
    Organisation(drfipId, "DRFIP", "Direction régionale des Finances publiques")

  val franceServicesId: Id = Organisation.Id("MFS")
  val franceServices: Organisation = Organisation(franceServicesId, "FS", "France Services")
  val laPosteId: Id = Organisation.Id("La Poste")
  val laPoste: Organisation = Organisation(laPosteId, "La Poste", "La Poste")
  val mairie: Organisation = Organisation("Mairie", "Mairie")
  val mdph: Organisation = Organisation("MDPH", "Maison départementale des personnes handicapées")
  val missionLocale: Organisation = Organisation("Mission locale", "Mission locale")
  val msaId: Id = Organisation.Id("MSA")
  val msa: Organisation = Organisation(msaId, "MSA", "Mutualité sociale agricole")
  val msap: Organisation = Organisation("MSAP", "Maison de services au public")
  val hopital: Organisation = Organisation("Hôpital", "Hôpital")
  val poleEmploiId: Id = Organisation.Id("Pôle emploi")
  val poleEmploi: Organisation = Organisation(poleEmploiId, "France Travail", "France Travail")
  val prefId: Id = Organisation.Id("Préf")
  val pref: Organisation = Organisation(prefId, "Préf", "Préfecture")
  val sousPrefId: Id = Organisation.Id("Sous-Préf")
  val sousPref: Organisation = Organisation(sousPrefId, "Sous-Préf", "Sous-préfecture")

  /** Note: checklist when adding an `Organisation`
    *   - alphabetical order
    *   - when one name contains another, there is a 'hack' in `deductedFromName` (check for failing
    *     cases)
    *   - add to the table `organisation` which is used by Metabase (see creation here
    *     conf/evolutions/default/40.sql and insert in 50.sql)
    */
  val all: List[Organisation] = List(
    Organisation("ANAH", "Agence nationale de l'habitat"),
    Organisation("ANTS", "Agence nationale des titres sécurisés"),
    association,
    bdf,
    caf, // Département
    carsat,
    ccas, // Ville
    Organisation("CDAD", "Conseils départementaux d'accès au droit"), // Département
    Organisation("Chèque énergie", "Chèque énergie"),
    cnav, // Département
    cpam, // Département
    Organisation(cnamId, "CNAM", "Caisse nationale d'assurance maladie"),
    cram, // Région
    ddfip, // Département
    departement,
    dila,
    drfip, // Région
    Organisation("France Rénov'", "France Rénov'"),
    hopital, // Ville
    Organisation("OFPRA", "Office français de protection des réfugiés et apatrides"), // Nationale
    laPoste,
    mairie, // Ville
    mdph,
    franceServices,
    missionLocale, // Ville
    msa,
    msap, // Ville
    poleEmploi,
    pref, // Département
    sousPref,
    Organisation(
      "URSSAF",
      "Unions de Recouvrement des cotisations de Sécurité Sociale et d’Allocations Familiales"
    )
  )

  val organismesAidants: List[Organisation] = List(association, dila, franceServices, msap, hopital)

  val organismesOperateurs: List[Organisation] =
    all.filter(!organismesAidants.contains[Organisation](_))

  case class Subject(subject: String, organisations: Seq[Organisation])

  case class Category(
      name: String,
      description: String,
      defaultOrganisations: Seq[Organisation],
      subjects: Seq[Subject]
  )

}
