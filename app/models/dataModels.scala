package models

import anorm.SqlMappingError
import cats.syntax.all._
import helper.{PlayFormHelper, Time}
import java.time.{Instant, ZonedDateTime}
import java.util.UUID
import models.Answer
import models.Answer.AnswerType
import models.Application.{MandatType, SeenByUser}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import serializers.Anorm.columnToJson
import serializers.JsonFormats.mapUUIDFormat

/** Only to serialize/deserialize in PG. */
object dataModels {

  object Answer {

    object AnswerType {

      implicit val answerTypeReads: Reads[AnswerType] =
        implicitly[Reads[String]].map(models.Answer.AnswerType.fromString)

      implicit val answerTypeWrites: Writes[AnswerType] =
        implicitly[Writes[String]].contramap[AnswerType](_.name)

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
      .and(
        (JsPath \ "invited_group_ids")
          .readNullable[List[UUID]]
          .or((JsPath \ "invitedGroupIds").readNullable[List[UUID]])
          .map(_.getOrElse(List.empty[UUID]))
      )(models.Answer.apply _)

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

      implicit val seenByUserWrites: Writes[SeenByUser] = (__ \ "user_id")
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

  case class FileMetadataRow(
      id: UUID,
      uploadDate: Instant,
      filename: String,
      filesize: Int,
      status: String,
      applicationId: Option[UUID],
      answerId: Option[UUID]
  ) {
    import FileMetadata._

    def modelStatus = status match {
      case "scanning"    => Status.Scanning.some
      case "quarantined" => Status.Quarantined.some
      case "available"   => Status.Available.some
      case "expired"     => Status.Expired.some
      case "error"       => Status.Error.some
      case _             => none
    }

    def toFileMetadata: Option[FileMetadata] = {
      val document = (applicationId, answerId) match {
        case (Some(applicationId), None)           => Attached.Application(applicationId).some
        case (Some(applicationId), Some(answerId)) => Attached.Answer(applicationId, answerId).some
        case _                                     => none
      }
      document.zip(modelStatus).map { case (attached, status) =>
        FileMetadata(
          id = id,
          uploadDate = uploadDate,
          filename = filename,
          filesize = filesize,
          status = status,
          attached = attached
        )
      }
    }

  }

  object FileMetadataRow {
    import FileMetadata._

    def statusFromFileMetadata(status: Status): String =
      status match {
        case Status.Scanning    => "scanning"
        case Status.Quarantined => "quarantined"
        case Status.Available   => "available"
        case Status.Expired     => "expired"
        case Status.Error       => "error"
      }

    def fromFileMetadata(metadata: FileMetadata): FileMetadataRow = {
      val (applicationId, answerId) = metadata.attached match {
        case Attached.Application(applicationId)      => (applicationId.some, none)
        case Attached.Answer(applicationId, answerId) => (applicationId.some, answerId.some)
      }
      FileMetadataRow(
        id = metadata.id,
        uploadDate = metadata.uploadDate,
        filename = metadata.filename,
        filesize = metadata.filesize,
        status = statusFromFileMetadata(metadata.status),
        applicationId = applicationId,
        answerId = answerId
      )
    }

  }

  object SmsFormats {
    import models.Sms
    implicit val smsIdReads: Reads[Sms.ApiId] = implicitly[Reads[String]].map(Sms.ApiId.apply)

    implicit val smsIdWrites: Writes[Sms.ApiId] =
      implicitly[Writes[String]].contramap((id: Sms.ApiId) => id.underlying)

    implicit val smsPhoneNumberReads: Reads[Sms.PhoneNumber] =
      implicitly[Reads[String]].map(Sms.PhoneNumber.apply)

    implicit val smsPhoneNumberWrites: Writes[Sms.PhoneNumber] =
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

  object UserRow {

    def fromUser(user: User, groupsWhichCannotHaveInstructors: Set[UUID]): UserRow = {
      val isInstructor = user.instructor &&
        groupsWhichCannotHaveInstructors.intersect(user.groupIds.toSet).isEmpty

      UserRow(
        id = user.id,
        key = user.key,
        firstName = user.firstName,
        lastName = user.lastName,
        name = user.name,
        qualite = user.qualite,
        email = user.email,
        helper = user.helper,
        instructor = isInstructor,
        admin = user.admin,
        areas = user.areas.distinct,
        creationDate = user.creationDate.toInstant,
        communeCode = user.communeCode,
        groupAdmin = user.groupAdmin,
        disabled = user.disabled,
        expert = user.expert,
        groupIds = user.groupIds.distinct,
        cguAcceptationDate = user.cguAcceptationDate.map(_.toInstant),
        newsletterAcceptationDate = user.newsletterAcceptationDate.map(_.toInstant),
        firstLoginDate = user.firstLoginDate,
        phoneNumber = user.phoneNumber,
        observableOrganisationIds = user.observableOrganisationIds.distinct.map(_.id),
        managingOrganisationIds = user.managingOrganisationIds.distinct.map(_.id),
        managingAreaIds = user.managingAreaIds.distinct,
        sharedAccount = user.sharedAccount,
        internalSupportComment = user.internalSupportComment,
      )
    }

  }

  case class UserRow(
      id: UUID,
      key: String,
      firstName: Option[String],
      lastName: Option[String],
      name: String,
      qualite: String,
      email: String,
      helper: Boolean,
      instructor: Boolean,
      admin: Boolean,
      areas: List[UUID],
      creationDate: Instant,
      communeCode: String,
      groupAdmin: Boolean,
      disabled: Boolean,
      expert: Boolean,
      groupIds: List[UUID],
      cguAcceptationDate: Option[Instant],
      newsletterAcceptationDate: Option[Instant],
      firstLoginDate: Option[Instant],
      phoneNumber: Option[String],
      observableOrganisationIds: List[String],
      managingOrganisationIds: List[String],
      managingAreaIds: List[UUID],
      sharedAccount: Boolean,
      internalSupportComment: Option[String]
  ) {

    def toUser: User = User(
      id = id,
      key = key,
      firstName = firstName,
      lastName = lastName,
      name = name,
      qualite = qualite,
      email = email,
      helper = helper,
      instructor = instructor,
      admin = admin,
      areas = areas,
      creationDate = creationDate.atZone(Time.timeZoneParis),
      communeCode = communeCode,
      groupAdmin = groupAdmin,
      disabled = disabled,
      expert = expert,
      groupIds = groupIds,
      cguAcceptationDate = cguAcceptationDate.map(_.atZone(Time.timeZoneParis)),
      newsletterAcceptationDate = newsletterAcceptationDate.map(_.atZone(Time.timeZoneParis)),
      firstLoginDate = firstLoginDate,
      phoneNumber = phoneNumber,
      observableOrganisationIds = observableOrganisationIds.map(Organisation.Id.apply),
      managingOrganisationIds = managingOrganisationIds.map(Organisation.Id.apply),
      managingAreaIds = managingAreaIds,
      sharedAccount = sharedAccount,
      internalSupportComment = internalSupportComment
    )

  }

}
