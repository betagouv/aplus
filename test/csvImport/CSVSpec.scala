package csvImport

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.{CSVUtil, UUIDHelper}
import models.UserGroup
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.io.Source

@RunWith(classOf[JUnitRunner])
class CSVSpec extends Specification {

  val oneUser: String =
    """Nom de l'utilisateur;Qualité de l'utilisateur;Email de l'utilisateur;Aidant;Instructeur;Responsable;Groupe(s);Territoire(s);Organisation du groupe;Description du groupe;Bal
      |Lucien Pereira;Monsieur;lucien.pereira@beta.gouv.fr;Aidant;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe!;super.groupe@beta.gouv.fr
      |Roxanne Duchamp;Madame;roxanne.duchamp@beta.gouv.fr;Aidant;;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe!;super.groupe@beta.gouv.fr
      |John Ben;Monsieur;john.ben@beta.gouv.fr;;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe 2!;super.groupe2@beta.gouv.fr
      |Li June;Madame;li.june@beta.gouv.fr;Aidant;Instructeur;;SuperGroupe;Alpes-Maritimes (06);;Le Super Groupe 2!;super.groupe2@beta.gouv.fr""".stripMargin

  "The 'ap;l\"us' string should" >> {
    "be escaped as '\"ap;l\"\"us\"'" >> {
      CSVUtil.escape("ap;l\"us") mustEqual "\"ap;l\"\"us\""
    }
  }

  "The csv string should" >> {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = csvImport.SEPARATOR.charAt(0)
    }
    "produce valid groups" >> {
      val reader = CSVReader.open(Source.fromString(oneUser))
      val list = reader.allWithHeaders().map(line => csvImport.fromCSVLine[GroupImport](line, GroupImport.groupMapping, GroupImport.HEADERS) -> csvImport.fromCSVLine[UserImport](line, UserImport.userMapping, UserImport.HEADERS))
      list must have size 4
      val result = list.head
      result._1 must beRight(GroupImport(name = "SuperGroupe",
        departement = "Alpes-Maritimes (06)",
        organisation = None,
        email = Some("super.groupe@beta.gouv.fr")))
      list.map(_._1).distinct must have size 2
    }
    "produce valid groups with UserGroup mapping" >> {
      val reader = CSVReader.open(Source.fromString(oneUser))
      val list = reader.allWithHeaders().map(line => csvImport.fromCSVLine[UserGroup](line, GroupImport.userGroupMapping, GroupImport.HEADERS) -> csvImport.fromCSVLine[UserImport](line, UserImport.userMapping, UserImport.HEADERS))
      list must have size 4
      val result = list.head
      result._1 must beRight(UserGroup(id = null,
        name = "SuperGroupe",
        description = None,
        inseeCode = List.empty[String],
        creationDate = null,
        createByUserId = null,
        area = UUIDHelper.namedFrom("nice"),
        organisation = None,
        email = Some("super.groupe@beta.gouv.fr")))
      list.map(_._1).distinct must have size 2
    }
    "produce a valid users" >> {
      val reader = CSVReader.open(Source.fromString(oneUser))
      val list = reader.allWithHeaders().map(line => csvImport.fromCSVLine[GroupImport](line, GroupImport.groupMapping, GroupImport.HEADERS) -> csvImport.fromCSVLine[UserImport](line, UserImport.userMapping, UserImport.HEADERS))
      list must have size 4
      list.head._2 must beRight(UserImport(name = "Lucien Pereira",
        qualite = "Monsieur",
        email = "lucien.pereira@beta.gouv.fr",
        helper = true,
        instructor = true,
        groupManager = false))
      list(1)._2 must beRight(UserImport(name = "Roxanne Duchamp",
        qualite = "Madame",
        email = "roxanne.duchamp@beta.gouv.fr",
        helper = true,
        instructor = false,
        groupManager = false))
    }
  }
}
