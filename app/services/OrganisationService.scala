package services

import cats.syntax.all._
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import models.Organisation
import models.Organisation.{Category, Subject}
import net.jcazevedo.moultingyaml.{
  deserializationError,
  DefaultYamlProtocol,
  PimpedString,
  YamlFormat,
  YamlString,
  YamlValue
}
import scala.io.Source

object OrganisationService {

  private object CustomYaml extends DefaultYamlProtocol {

    implicit object organisationFormat extends YamlFormat[Organisation] {

      def write(x: Organisation): YamlString = {
        require(x ne null)
        YamlString(x.shortName)
      }

      def read(value: YamlValue): Organisation =
        value match {
          case YamlString(x) => Organisation.fromShortName(x).get
          case x =>
            deserializationError("Expected String as YamlString, but got " + x)
        }

    }

    implicit val subjectFormat: YamlFormat[Subject] = yamlFormat2(Subject.apply)
    implicit val categoryFormat: YamlFormat[Category] = yamlFormat4(Category.apply)
  }

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

@javax.inject.Singleton
class OrganisationService {

  import OrganisationService._
  import CustomYaml._

  val categories: List[Category] = {
    val yaml = Source.fromFile("data/categories.yaml").getLines().mkString("\n")
    yaml.parseYaml.convertTo[List[Category]]
  }

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

}
