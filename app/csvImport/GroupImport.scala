package csvImport

import java.util.UUID

import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.Constraints.{maxLength, nonEmpty}

case class GroupImport(name: String,
                       departement: String,
                       organisation: Option[String],
                       email: Option[String],
                       existingId: Option[UUID] = None)

object GroupImport {
  val HEADER: String = List(TERRITORY_HEADER_PREFIX, GROUP_ORGANISATION_HEADER_PREFIX, GROUP_NAME_HEADER_PREFIX, GROUP_EMAIL_HEADER_PREFIX).mkString(SEPARATOR)

  val groupMapping: Mapping[GroupImport] = mapping(
    "name" -> nonEmptyText.verifying(maxLength(100)),
    "department" -> nonEmptyText,
    "organisation" -> optional(nonEmptyText),
    "email" -> optional(email.verifying(maxLength(200), nonEmpty)),
    "existingId" -> optional(uuid)
  )(GroupImport.apply)(GroupImport.unapply)

  def fromCSVLine(values: Map[String, String]): Either[CSVImportError, GroupImport] = {
    searchByPrefix(GROUP_NAME_HEADER_PREFIX, values).fold[Either[CSVImportError, GroupImport]]({
      Left[CSVImportError, GroupImport](GROUP_NAME_UNDEFINED)
    })({ name: String =>
      searchByPrefix(TERRITORY_HEADER_PREFIX, values).fold[Either[CSVImportError, GroupImport]]({
        Left[CSVImportError, GroupImport](DEPARTEMENT_UNDEFINED)
      })({ departement: String =>
        Right[CSVImportError, GroupImport](GroupImport.apply(name = name,
          departement = departement,
          organisation = searchByPrefix(GROUP_ORGANISATION_HEADER_PREFIX, values).flatMap(v => if (v.isEmpty) None else Some(v)),
          email = searchByPrefix(GROUP_EMAIL_HEADER_PREFIX, values).flatMap(v => if (v.isEmpty) None else Some(v))))
      })
    })
  }
}