package models.form

import java.time.ZonedDateTime
import java.util.UUID

import models.{Organisation, UserGroup}
import play.api.data.Forms.{email, ignored, list, mapping, of, optional, text, uuid}
import play.api.data.Mapping

case class UserGroupForm(
    id: UUID,
    name: String,
    description: Option[String],
    inseeCode: List[String],
    creationDate: ZonedDateTime,
    areaIds: List[UUID],
    organisation: Option[Organisation.Id] = None,
    email: Option[String] = None
)

object UserGroupForm {

  private def toUserGroup(userGroupForm: UserGroupForm): UserGroup = {
    import userGroupForm._
    UserGroup(
      id = id,
      name = name,
      description = description,
      inseeCode = inseeCode,
      creationDate = creationDate,
      areaIds = areaIds,
      organisation = organisation,
      email = userGroupForm.email
    )
  }

  private def toUserGroupForm(userGroup: UserGroup): UserGroupForm = {
    import userGroup._
    UserGroupForm(
      id = id,
      name = name,
      description = description,
      inseeCode = inseeCode,
      creationDate = creationDate,
      areaIds = areaIds,
      organisation = organisation,
      email = userGroup.email
    )
  }

  def groupImportMapping(date: ZonedDateTime): Mapping[UserGroup] =
    mapping(
      "id" -> optional(uuid).transform[UUID](
        {
          case None     => UUID.randomUUID()
          case Some(id) => id
        },
        Option.apply
      ),
      "name" -> text(maxLength = 60),
      "description" -> optional(text),
      "insee-code" -> list(text),
      "creationDate" -> ignored(date),
      "area-ids" -> list(uuid)
        .verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
      "organisation" -> optional(of[Organisation.Id]).verifying(
        "Vous devez sélectionner une organisation dans la liste",
        _.exists(Organisation.isValidId)
      ),
      "email" -> optional(email)
    )(UserGroupForm.apply)(UserGroupForm.unapply).transform(toUserGroup, toUserGroupForm)

}
