package serializers

import java.time.{Instant, ZonedDateTime}
import java.util.UUID

import anorm.SqlMappingError
import helper.PlayFormHelper
import models.Answer
import models.Answer.AnswerType
import models.Application.{MandatType, SeenByUser}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import serializers.Anorm.columnToJson
import serializers.JsonFormats.mapUUIDFormat

/** Only to serialize/deserialize in PG. */
object DataModel {

  object Answer {

    object AnswerType {

      implicit val answerTypeReads =
        implicitly[Reads[String]].map(models.Answer.AnswerType.fromString)

      implicit val answerTypeWrites = implicitly[Writes[String]].contramap[AnswerType](_.name)
    }

    import AnswerType.{answerTypeReads, answerTypeWrites}

    // .or are due to an old bug
    implicit val answerReads: Reads[Answer] = (JsPath \ "id")
      .read[UUID]
      .and((JsPath \ "application_id").read[UUID].or((JsPath \ "applicationId").read[UUID]))
      .and(
        (JsPath \ "creation_date")
          .read[ZonedDateTime]
          .or((JsPath \ "creationDate").read[ZonedDateTime])
      )
      .and(
        (JsPath \ "answer_type")
          .readNullable[AnswerType]
          .or((JsPath \ "answerType").readNullable[AnswerType])
          .map {
            case Some(answerType) => answerType
            case None             => models.Answer.AnswerType.Custom
          }
      )
      .and((JsPath \ "message").read[String])
      .and((JsPath \ "creator_user_id").read[UUID].or((JsPath \ "creatorUserID").read[UUID]))
      .and(
        (JsPath \ "creator_user_name").read[String].or((JsPath \ "creatorUserName").read[String])
      )
      .and(
        (JsPath \ "invited_users")
          .read[Map[UUID, String]]
          .or((JsPath \ "invitedUsers").read[List[(UUID, String)]].map(_.toMap))
      )
      .and(
        (JsPath \ "visible_by_helpers")
          .read[Boolean]
          .or((JsPath \ "visibleByHelpers").read[Boolean])
      )
      .and(
        (JsPath \ "declare_application_has_irrelevant")
          .read[Boolean]
          .or((JsPath \ "declareApplicationHasIrrelevant").read[Boolean])
      )
      .and(
        (JsPath \ "user_infos")
          .readNullable[Map[String, String]]
          .or((JsPath \ "userInfos").readNullable[Map[String, String]])
      )
      .and((JsPath \ "files").readNullable[Map[String, Long]])
      .and(
        (JsPath \ "invited_group_ids")
          .readNullable[List[UUID]]
          .or((JsPath \ "invitedGroupIds").readNullable[List[UUID]])
          .map(_.getOrElse(List.empty[UUID]))
      )(models.Answer.apply _)

    //implicit val answerWrite: Writes[Answer] = Json.writes[Answer]
    implicit val answerWrite: Writes[Answer] =
      (JsPath \ "id")
        .write[UUID]
        .and((JsPath \ "application_id").write[UUID])
        .and((JsPath \ "creation_date").write[ZonedDateTime])
        .and((JsPath \ "answer_type").write[AnswerType])
        .and((JsPath \ "message").write[String])
        .and((JsPath \ "creator_user_id").write[UUID])
        .and((JsPath \ "creator_user_name").write[String])
        .and((JsPath \ "invited_users").write[Map[UUID, String]])
        .and((JsPath \ "visible_by_helpers").write[Boolean])
        .and((JsPath \ "declare_application_has_irrelevant").write[Boolean])
        .and((JsPath \ "user_infos").writeNullable[Map[String, String]])
        .and((JsPath \ "files").writeNullable[Map[String, Long]])
        .and((JsPath \ "invited_group_ids").write[List[UUID]])(unlift(models.Answer.unapply))

  }

  object Application {

    object MandatType {
      import models.Application.MandatType._

      def dataModelSerialization(entity: MandatType): String =
        entity match {
          case Sms   => "sms"
          case Phone => "phone"
          case Paper => "paper"
        }

      def dataModelDeserialization(raw: String): Option[MandatType] =
        raw match {
          case "sms"   => Some(Sms)
          case "phone" => Some(Phone)
          case "paper" => Some(Paper)
          case _       => None
        }

    }

    object SeenByUser {

      implicit val seenByUserReads: Reads[SeenByUser] = (__ \ "user_id")
        .read[UUID]
        .and((__ \ "last_seen_date").read[Instant])(models.Application.SeenByUser.apply _)

      implicit val seenByUserWrites = (__ \ "user_id")
        .write[UUID]
        .and((__ \ "last_seen_date").write[Instant])(unlift(models.Application.SeenByUser.unapply))

      implicit val seenByUserListParser: anorm.Column[List[SeenByUser]] =
        implicitly[anorm.Column[JsValue]].mapResult(
          _.validate[List[SeenByUser]].asEither.left.map(errors =>
            SqlMappingError(
              s"Cannot parse JSON as List[SeenByUser]: ${PlayFormHelper.prettifyJsonFormInvalidErrors(errors)}"
            )
          )
        )

    }

  }

  object SmsFormats {
    import models.Sms
    implicit val smsIdReads = implicitly[Reads[String]].map(Sms.ApiId.apply)

    implicit val smsIdWrites =
      implicitly[Writes[String]].contramap((id: Sms.ApiId) => id.underlying)

    implicit val smsPhoneNumberReads = implicitly[Reads[String]].map(Sms.PhoneNumber.apply)

    implicit val smsPhoneNumberWrites =
      implicitly[Writes[String]].contramap((id: Sms.PhoneNumber) => id.internationalPhoneNumber)

    // Not implicits, so they are not picked as serializers/deserializers of `Sms`
    private val smsOutgoingFormat: Format[Sms.Outgoing] =
      (JsPath \ "apiId")
        .format[Sms.ApiId]
        .and((JsPath \ "creationDate").format[ZonedDateTime])
        .and((JsPath \ "recipient").format[Sms.PhoneNumber])
        .and((JsPath \ "body").format[String])(Sms.Outgoing.apply, unlift(Sms.Outgoing.unapply))

    private val smsIncomingFormat: Format[Sms.Incoming] =
      (JsPath \ "apiId")
        .format[Sms.ApiId]
        .and((JsPath \ "creationDate").format[ZonedDateTime])
        .and((JsPath \ "originator").format[Sms.PhoneNumber])
        .and((JsPath \ "body").format[String])(Sms.Incoming.apply, unlift(Sms.Incoming.unapply))

    implicit val smsApiReads: Reads[Sms] =
      (JsPath \ "tag").read[String].flatMap {
        case "outgoing" => smsOutgoingFormat.map(sms => (sms: Sms))
        case "incoming" => smsIncomingFormat.map(sms => (sms: Sms))
        case tag        => Reads.failed(s"Type de SMS inconnu: $tag")
      }

    implicit val smsApiWrites: Writes[Sms] =
      Writes {
        case sms: Sms.Outgoing =>
          smsOutgoingFormat.writes(sms) match {
            case obj: JsObject => obj + ("tag" -> JsString("outgoing"))
            case other         => other
          }
        case sms: Sms.Incoming =>
          smsIncomingFormat.writes(sms) match {
            case obj: JsObject => obj + ("tag" -> JsString("incoming"))
            case other         => other
          }
      }

  }

}
