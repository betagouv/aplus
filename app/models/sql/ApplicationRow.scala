package models.sql

import java.time.ZonedDateTime
import java.util.UUID

import anorm.{Macro, RowParser}
import cats.implicits.catsSyntaxTuple2Semigroupal
import helper.Time
import models.Application.Mandat
import models.{Answer, Application}
import serializers.DataModel

final case class ApplicationRow(
    id: UUID,
    creationDate: ZonedDateTime,
    creatorUserName: String,
    creatorUserId: UUID,
    subject: String,
    description: String,
    userInfos: Map[String, String],
    invitedUsers: Map[UUID, String],
    area: UUID,
    irrelevant: Boolean,
    answers: List[Answer] = List.empty[Answer],
    internalId: Int = -1,
    closed: Boolean = false,
    seenByUserIds: List[UUID] = List.empty[UUID],
    usefulness: Option[String] = Option.empty[String],
    closedDate: Option[ZonedDateTime] = Option.empty[ZonedDateTime],
    expertInvited: Boolean = false,
    hasSelectedSubject: Boolean = false,
    category: Option[String] = Option.empty[String],
    files: Map[String, Long] = Map.empty[String, Long],
    mandatType: Option[Application.Mandat.MandatType],
    mandatDate: Option[String]
)

object ApplicationRow {

  import serializers.Anorm._
  import DataModel.Application.Mandat.MandatType.MandatTypeParser

  val ApplicationRowParser: RowParser[ApplicationRow] = Macro
    .parser[ApplicationRow](
      "id",
      "creation_date",
      "creator_user_name",
      "creator_user_id",
      "subject",
      "description",
      "user_infos",
      "invited_users",
      "area",
      "irrelevant",
      "answers",
      "internal_id",
      "closed",
      "seen_by_user_ids",
      "usefulness",
      "closed_date",
      "expert_invited",
      "has_selected_subject",
      "category",
      "files",
      "mandat_type",
      "mandat_date"
    )
    .map(application =>
      application.copy(
        creationDate = application.creationDate.withZoneSameInstant(Time.timeZoneParis),
        answers = application.answers.map(answer =>
          answer.copy(creationDate = answer.creationDate.withZoneSameInstant(Time.timeZoneParis))
        )
      )
    )

  def toApplication(row: ApplicationRow): Application = {
    import row._
    Application(
      id,
      creationDate,
      creatorUserName,
      creatorUserId,
      subject,
      description = description,
      userInfos = userInfos,
      invitedUsers = invitedUsers,
      area = area,
      irrelevant = irrelevant,
      answers = answers,
      internalId = internalId,
      closed = closed,
      seenByUserIds = seenByUserIds,
      usefulness = usefulness,
      closedDate = closedDate,
      expertInvited = expertInvited,
      hasSelectedSubject = hasSelectedSubject,
      category = category,
      files = files,
      mandat = (row.mandatType, row.mandatDate).mapN(Mandat.apply)
    )
  }

}
