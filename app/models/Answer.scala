package models

import java.time.ZonedDateTime
import java.util.UUID

import anorm.Column.nonNull
import anorm.{MetaDataItem, TypeDoesNotMatch}
import cats.implicits.catsSyntaxEitherId
import helper.Time
import play.api.libs.json.{Json, Reads, Writes}
import serializers.Anorm.className

import serializers.JsonFormats._

case class Answer(
    id: UUID,
    applicationId: UUID,
    creationDate: ZonedDateTime,
    message: String,
    creatorUserID: UUID,
    creatorUserName: String,
    invitedUsers: Map[UUID, String],
    visibleByHelpers: Boolean,
    declareApplicationHasIrrelevant: Boolean,
    userInfos: Option[Map[String, String]],
    files: Option[Map[String, Long]] = Some(Map())
) extends AgeModel {

  lazy val filesAvailabilityLeftInDays: Option[Int] = if (ageInDays > 8) {
    None
  } else {
    Some(7 - ageInDays)
  }

}

object Answer {

  implicit val Reads: Reads[Answer] = Json
    .reads[Answer]
    .map(answer =>
      answer.copy(creationDate = answer.creationDate.withZoneSameInstant(Time.timeZoneParis))
    )

  implicit val Writes: Writes[Answer] = Json.writes[Answer]

  implicit val answerListParser: anorm.Column[List[Answer]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Json.parse(json.getValue).as[List[Answer]].asRight[Nothing]
        case json: String => Json.parse(json).as[List[Answer]].asRight[Nothing]
        case _ =>
          TypeDoesNotMatch(
            s"Cannot convert $value: ${className(value)} to List[Answer] for column $qualified"
          ).asLeft[List[Answer]]
      }
    }

}
