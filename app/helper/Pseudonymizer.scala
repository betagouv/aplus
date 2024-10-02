package helper

import cats.syntax.all._
import com.github.tototoshi.csv.CSVReader
import java.util.UUID
import scala.util.Random

object Pseudonymizer {

  val names: List[(String, Boolean)] = {
    val reader = CSVReader.open("data/pseudonymization/common_first_names.csv")
    val list = reader.iterator
      .drop(1)
      .collect { case Seq(gender, name, _) =>
        (name, gender === "2")
      }
      .toList
    reader.close()
    list
  }

  val adjectives: List[(String, String)] = {
    val reader = CSVReader.open("data/pseudonymization/common_adjectives.csv")
    val list = reader.iterator
      .drop(1)
      .collect { case Seq(m, f) =>
        (m, f)
      }
      .toList
    reader.close()
    list
  }

}

/** Designed to give the same results on multiple runs for the same UUID parameter. Absolutely not
  * collision resistant (collision resistance is not wanted here).
  */
class Pseudonymizer(uuid: UUID) {
  import Pseudonymizer.{adjectives, names}

  private val random = new Random(uuid.hashCode)

  val (firstName: String, lastName: String) = {
    val (first, gender) = names(random.between(0, names.size))
    val (adjectiveM, adjectiveF) = adjectives(random.between(0, adjectives.size))
    val last = StringHelper.capitalizeName(if (gender) adjectiveF else adjectiveM)
    (first, last)
  }

  def fullName: String = firstName + " " + lastName

  def emailName: String = StringHelper.unaccent(fullName.toLowerCase).replaceAll("[^a-z]+", ".") +
    "." + uuid.toString.take(4)

  def emailKeepingDomain(originEmail: String): String = {
    val domain = originEmail
      .split('@')
      .drop(1)
      .headOption
      .filter(_.nonEmpty)
      .map(_.toLowerCase + ".example.com")
      .getOrElse[String]("example.com")
    emailName + "@" + domain
  }

}
