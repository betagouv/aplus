package services

import models.Organisation
import models.Organisation.{Category, Subject}
import net.jcazevedo.moultingyaml._

import scala.io.Source

@javax.inject.Singleton
class OrganisationService {

  object CustomYaml extends DefaultYamlProtocol {

    implicit object organisationFormat extends YamlFormat[Organisation] {

      def write(x: Organisation) = {
        require(x ne null)
        YamlString(x.shortName)
      }

      def read(value: YamlValue) = value match {
        case YamlString(x) => Organisation.fromShortName(x).get
        case x =>
          deserializationError("Expected String as YamlString, but got " + x)
      }
    }
    implicit val subjectFormat = yamlFormat2(Subject.apply)
    implicit val categoryFormat = yamlFormat4(Category.apply)
  }

  import CustomYaml._

  val categories = {
    val yaml = Source.fromFile("data/categories.yaml").getLines.mkString("\n")
    yaml.parseYaml.convertTo[List[Category]]
  }
}
