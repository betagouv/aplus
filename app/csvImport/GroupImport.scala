package csvImport

import java.util.UUID

import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.{Form, Mapping}

case class GroupImport(name: String,
                       departement: String,
                       organisation: Option[String],
                       description: Option[String],
                       email: Option[String],
                       existingId: Option[UUID] = None)

object GroupImport {
  val HEADER: String = List(GROUP_NAME_LABEL, DEPARTEMENT_LABEL, ORGANISATION_LABEL, DESCRIPTION_LABEL, GROUP_EMAIL_LABEL).mkString(SEPARATOR)

  val groupMapping: Mapping[GroupImport] = mapping(
    "name" -> nonEmptyText.verifying(maxLength(100)),
    "department" -> nonEmptyText,
    "organisation" -> optional(nonEmptyText),
    "description" -> optional(nonEmptyText.verifying(maxLength(100))),
    "email" -> optional(email.verifying(maxLength(200), nonEmpty)),
    "existingId" -> optional(uuid)
  )(GroupImport.apply)(GroupImport.unapply)

  def fromCSVLine(values: Map[String, String]): Either[CSVImportError, GroupImport] = {
    values.get(GROUP_NAME_LABEL).fold[Either[CSVImportError, GroupImport]]({
      Left[CSVImportError, GroupImport](GROUP_NAME_UNDEFINED)
    })({ name: String =>
      values.get(DEPARTEMENT_LABEL).fold[Either[CSVImportError, GroupImport]]({
        Left[CSVImportError, GroupImport](DEPARTEMENT_UNDEFINED)
      })({ departement: String =>
        Right[CSVImportError, GroupImport](GroupImport.apply(name = name,
          departement = departement,
          organisation = values.get(ORGANISATION_LABEL).flatMap(v => if (v.isEmpty) None else Some(v)),
          description = values.get(DESCRIPTION_LABEL).flatMap(v => if (v.isEmpty) None else Some(v)),
          email = values.get(GROUP_EMAIL_LABEL).flatMap(v => if (v.isEmpty) None else Some(v))))
      })
    })
  }
}