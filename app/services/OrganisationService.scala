package services

import cats.syntax.all._
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import javax.inject.Singleton
import models.Organisation
import models.Organisation.Category
import scala.io.Source

object OrganisationService {

  implicit private object fsCsvFormat extends DefaultCSVFormat {
    override val delimiter = ';'
  }

  case class InseeCode(code: String) extends AnyVal

  case class FranceServiceInstance(
      nomFranceService: String,
      adresse: String,
      complementAdresse: Option[String],
      postalCode: String,
      commune: String,
      departementCode: InseeCode,
      contactMail: Option[String],
      phone: Option[String]
  )

  case class FranceServiceInfos(instances: List[FranceServiceInstance])

}

@Singleton
class OrganisationService {

  import OrganisationService._

  val franceServiceInfos: FranceServiceInfos = {
    // To see what we extend, see:
    // https://github.com/tototoshi/scala-csv/blob/1.3.6/src/main/scala/com/github/tototoshi/csv/Formats.scala#L18
    val reader = CSVReader.open(
      Source.fromFile("data/France_Services_CDC_Liste_des_France_Services_02032020.csv")
    )
    def readNthField(line: List[String], nth: Int): Option[String] =
      line.lift(nth).flatMap { field =>
        val trimmed: String = field.trim()
        if (trimmed === "-" || trimmed.isEmpty) None
        else Some(trimmed)
      }

    val lines = reader
      .all()
      .drop(1) // Remove the header
      .map { line =>
        val rawDepartementCode = readNthField(line, 1).getOrElse("") // Should never be empty
        val departementCode = InseeCode(
          if (rawDepartementCode.length === 0) "00"
          else if (rawDepartementCode.length === 1) s"0$rawDepartementCode"
          else rawDepartementCode
        )
        FranceServiceInstance(
          nomFranceService = readNthField(line, 2).getOrElse(""), // Never empty
          adresse = readNthField(line, 3).getOrElse(""), // Never empty
          complementAdresse = readNthField(line, 4),
          postalCode = readNthField(line, 6).getOrElse(""), // Never empty
          commune = readNthField(line, 7).getOrElse(""), // Never empty
          departementCode = departementCode,
          contactMail = readNthField(line, 10),
          phone = readNthField(line, 11)
        )
      }
    reader.close()
    FranceServiceInfos(lines)
  }

  val categories: List[Category] = {
    import Organisation._
    List(
      Category(
        name = "Social / Famille",
        description = "Allocations familiales, aides et prestations sociales, etc.",
        defaultOrganisations = Seq(caf, msa),
        subjects = Seq(
          Subject(
            subject = "Trop perçu à la suite d'un contrôle",
            organisations = Seq(caf, msa)
          ),
          Subject(
            subject = "Délai d'étude ou de versement d'une aide anormalement long",
            organisations = Seq(caf, msa)
          ),
          Subject(
            subject = "Accompagnement pour la naissance ou le décès d'un proche",
            organisations = Seq(caf, msa, cpam, cram)
          )
        )
      ),
      Category(
        name = "Santé / Handicap",
        description = "Sécurité sociale, CSS, AME, etc.",
        defaultOrganisations = Seq(cpam, cram, msa, mdph),
        subjects = Seq(
          Subject(
            subject = "Première inscription à la Sécurité Sociale",
            organisations = Seq(cpam, cram, msa)
          ),
          Subject(
            subject = "Remboursement de soins médicaux",
            organisations = Seq(cpam, cram, msa)
          ),
          Subject(
            subject = "Renouvellement CSS (anciennement CMU-C et ACS)",
            organisations = Seq(cpam, cram, msa)
          ),
          Subject(
            subject = "Aide médicale de l'État (AME) pour les personnes en situation irrégulière",
            organisations = Seq(cpam, cram)
          ),
          Subject(
            subject = "Reconnaissance Handicap",
            organisations = Seq(mdph)
          )
        )
      ),
      Category(
        name = "Emploi / Formation",
        description = "Démarches Pôle emploi, allocations chômage, etc.",
        defaultOrganisations = Seq(poleEmploi, missionLocale),
        subjects = Seq(
          Subject(
            subject = "Inscription à Pôle Emploi",
            organisations = Seq(poleEmploi)
          ),
          Subject(
            subject = "Suivi d'un dossier Pôle Emploi complexe",
            organisations = Seq(poleEmploi)
          ),
          Subject(
            subject = "Radiation de Pôle Emploi",
            organisations = Seq(poleEmploi)
          ),
          Subject(
            subject = "Demande et suivi de formation",
            organisations = Seq(poleEmploi, missionLocale)
          )
        )
      ),
      Category(
        name = "Logement",
        description = "Allocations logement, hébergement d'urgence, etc.",
        defaultOrganisations = Seq(caf, msa, pref, sousPref, mairie),
        subjects = Seq(
          Subject(
            subject = "Demande d'une allocation logement (APL, ALS, ALF)",
            organisations = Seq(caf, msa)
          ),
          Subject(
            subject = "Demande d'un hébergement d'urgence",
            organisations = Seq(mairie, ccas, departement)
          ),
          Subject(
            subject = "Expulsion du logement",
            organisations = Seq(mairie, pref, sousPref)
          )
        )
      ),
      Category(
        name = "Retraite",
        description = "Droits et demande de retraite, ASPA, pension de réversion, etc.",
        defaultOrganisations = Seq(cnav, carsat, msa),
        subjects = Seq(
          Subject(
            subject = "Préparation de la retraite et première demande",
            organisations = Seq(cnav, carsat, msa)
          ),
          Subject(
            subject = "Demande de l'allocation de solidarité aux personnes âgées (ASPA)",
            organisations = Seq(cnav, carsat, msa)
          ),
          Subject(
            subject = "Versement de la pension de retraite",
            organisations = Seq(cnav, carsat, msa)
          ),
          Subject(
            subject = "Pension de réversion",
            organisations = Seq(cnav, carsat, msa)
          )
        )
      ),
      Category(
        name = "Impôts / Argent",
        description = "Fiscalité, surendettement, précarité financière, etc.",
        defaultOrganisations = Seq(ddfip, drfip, bdf),
        subjects = Seq(
          Subject(
            subject = "Demande d'un justificatif des impôts ou d'un avis fiscal",
            organisations = Seq(ddfip, drfip)
          ),
          Subject(
            subject = "Dossier de surendettement",
            organisations = Seq(bdf)
          )
        )
      ),
      Category(
        name = "Papiers / Titres",
        description = "Carte d'identité, passeport, titre de séjour, etc.",
        defaultOrganisations = Seq(pref, sousPref, mairie),
        subjects = Seq(
          Subject(
            subject = "Vol, perte, renouvellement de papiers",
            organisations = Seq(mairie)
          ),
          Subject(
            subject = "Première demande de carte d'identité ou passeport",
            organisations = Seq(mairie)
          ),
          Subject(
            subject = "Renouvellement du titre de séjour",
            organisations = Seq(pref, sousPref)
          )
        )
      ),
      Category(
        name = "Autre",
        description = "Ma demande concerne un autre sujet",
        defaultOrganisations = Seq.empty,
        subjects = Seq.empty
      )
    )
  }

}
