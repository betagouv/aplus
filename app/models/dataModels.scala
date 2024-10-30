package models

import anorm.{Column, SqlMappingError, ToStatement}
import anorm.postgresql.{jsValueColumn, jsValueToStatement}
import cats.syntax.all._
import helper.{PlayFormHelpers, Time}
import helper.MiscHelpers.toTuple
import java.time.{Instant, ZonedDateTime}
import java.util.UUID
import models.Application.{MandatType, SeenByUser}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._
import serializers.JsonFormats.mapUUIDFormat

/** Only to serialize/deserialize in PG. */
object dataModels {

  case class PasswordRow(
      userId: UUID,
      passwordHash: String,
      creationDate: Instant,
      lastUpdate: Instant,
  )

  case class PasswordRecoveryTokenRow(
      token: String,
      userId: UUID,
      creationDate: Instant,
      expirationDate: Instant,
      ipAddress: String,
      used: Boolean,
  )

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
        .and((__ \ "last_seen_date").write[Instant])(toTuple[SeenByUser])

    }

  }

  object SeenByUsers {

    import models.dataModels.Application.SeenByUser._

    implicit val seenByUsersParser: Column[SeenByUsers] =
      Column
        .of[JsValue]
        .mapResult(
          _.validate[List[SeenByUser]].asEither
            .map(SeenByUsers.apply)
            .left
            .map(errors =>
              SqlMappingError(
                s"Cannot parse JSON as List[SeenByUser]: ${PlayFormHelpers.prettifyJsonFormInvalidErrors(errors)}"
              )
            )
        )

    implicit val seenByUsersToStatement: ToStatement[SeenByUsers] =
      ToStatement.of[JsValue].contramap(users => Json.toJson(users.users))

  }

  case class SeenByUsers(users: List[SeenByUser])

  object InvitedUsers {

    implicit val invitedUsersReads: Reads[InvitedUsers] =
      implicitly[Reads[Map[UUID, String]]].map(InvitedUsers.apply)

    implicit val invitedUsersWrites: Writes[InvitedUsers] =
      implicitly[Writes[Map[String, String]]]
        .contramap(_.invitedUsers.map { case (key, value) => key.toString -> value })

    implicit val invitedUsersParser: Column[InvitedUsers] =
      Column
        .of[JsValue]
        .mapResult(
          _.validate[InvitedUsers].asEither.left.map(errors =>
            SqlMappingError(
              s"Cannot parse JSON as InvitedUsers: ${PlayFormHelpers.prettifyJsonFormInvalidErrors(errors)}"
            )
          )
        )

    implicit val invitedUsersToStatement: ToStatement[InvitedUsers] =
      ToStatement.of[JsValue].contramap(Json.toJson(_))

  }

  /** Convenient wrapper for serialization. */
  case class InvitedUsers(invitedUsers: Map[UUID, String])

  object UserInfos {

    implicit val userInfosReads: Reads[UserInfos] =
      implicitly[Reads[Map[String, String]]].map(UserInfos.apply)

    implicit val userInfosWrites: Writes[UserInfos] =
      implicitly[Writes[Map[String, String]]].contramap(_.userInfos)

    implicit val userInfosParser: Column[UserInfos] =
      Column
        .of[JsValue]
        .mapResult(
          _.validate[UserInfos].asEither.left.map(errors =>
            SqlMappingError(
              s"Cannot parse JSON as UserInfos: ${PlayFormHelpers.prettifyJsonFormInvalidErrors(errors)}"
            )
          )
        )

    implicit val userInfosToStatement: ToStatement[UserInfos] =
      ToStatement.of[JsValue].contramap(Json.toJson(_))

  }

  /** Convenient wrapper for serialization. */
  case class UserInfos(userInfos: Map[String, String])

  case class FileMetadataRow(
      id: UUID,
      uploadDate: Instant,
      filename: String,
      filesize: Int,
      status: String,
      applicationId: Option[UUID],
      answerId: Option[UUID],
      encryptionKeyId: Option[String],
  ) {
    import FileMetadata._

    def modelStatus: Option[Status] = status match {
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
          attached = attached,
          encryptionKeyId = encryptionKeyId,
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
        answerId = answerId,
        encryptionKeyId = metadata.encryptionKeyId,
      )
    }

  }

  object SmsFormats {

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
        .and((JsPath \ "body").format[String])(Sms.Outgoing.apply, toTuple)

    private val smsIncomingFormat: Format[Sms.Incoming] =
      (JsPath \ "apiId")
        .format[Sms.ApiId]
        .and((JsPath \ "creationDate").format[ZonedDateTime])
        .and((JsPath \ "originator").format[Sms.PhoneNumber])
        .and((JsPath \ "body").format[String])(Sms.Incoming.apply, toTuple)

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

  object ApplicationRow {

    def fromApplication(application: Application): ApplicationRow =
      ApplicationRow(
        id = application.id,
        creationDate = application.creationDate.toInstant,
        creatorUserName = application.creatorUserName,
        creatorUserId = application.creatorUserId,
        creatorGroupId = application.creatorGroupId,
        creatorGroupName = application.creatorGroupName,
        subject = application.subject,
        description = application.description,
        userInfos = UserInfos(application.userInfos),
        invitedUsers = InvitedUsers(application.invitedUsers),
        area = application.area,
        irrelevant = application.irrelevant,
        internalId = application.internalId,
        closed = application.closed,
        seenByUsers = SeenByUsers(application.seenByUsers),
        usefulness = application.usefulness,
        closedDate = application.closedDate.map(_.toInstant),
        expertInvited = application.expertInvited,
        hasSelectedSubject = application.hasSelectedSubject,
        category = application.category,
        mandatType =
          application.mandatType.map(dataModels.Application.MandatType.dataModelSerialization),
        mandatDate = application.mandatDate,
        invitedGroupIds = application.invitedGroupIdsAtCreation,
        personalDataWiped = application.personalDataWiped
      )

  }

  case class ApplicationRow(
      id: UUID,
      creationDate: Instant,
      creatorUserName: String,
      creatorUserId: UUID,
      creatorGroupId: Option[UUID],
      creatorGroupName: Option[String],
      subject: String,
      description: String,
      userInfos: UserInfos,
      invitedUsers: InvitedUsers,
      area: UUID,
      irrelevant: Boolean,
      internalId: Int,
      closed: Boolean,
      seenByUsers: SeenByUsers,
      usefulness: Option[String],
      closedDate: Option[Instant],
      expertInvited: Boolean,
      hasSelectedSubject: Boolean,
      category: Option[String],
      mandatType: Option[String],
      mandatDate: Option[String],
      invitedGroupIds: List[UUID],
      personalDataWiped: Boolean,
  ) {

    def toApplication(relatedAnswers: List[AnswerRow]): Application = {
      val answers =
        relatedAnswers.filter(_.applicationId === id).sortBy(_.answerOrder).map(_.toAnswer)
      models.Application(
        id = id,
        creationDate = creationDate.atZone(Time.timeZoneParis),
        creatorUserName = creatorUserName,
        creatorUserId = creatorUserId,
        creatorGroupId = creatorGroupId,
        creatorGroupName = creatorGroupName,
        subject = subject,
        description = description,
        userInfos = userInfos.userInfos,
        invitedUsers = invitedUsers.invitedUsers,
        area = area,
        irrelevant = irrelevant,
        answers = answers,
        internalId = internalId,
        closed = closed,
        seenByUsers = seenByUsers.users,
        usefulness = usefulness,
        closedDate = closedDate.map(_.atZone(Time.timeZoneParis)),
        expertInvited = expertInvited,
        hasSelectedSubject = hasSelectedSubject,
        category = category,
        mandatType = mandatType.flatMap(Application.MandatType.dataModelDeserialization),
        mandatDate = mandatDate,
        invitedGroupIdsAtCreation = invitedGroupIds,
        personalDataWiped = personalDataWiped,
      )
    }

  }

  object AnswerRow {

    def fromAnswer(answer: Answer, answerOrder: Int): AnswerRow =
      AnswerRow(
        id = answer.id,
        applicationId = answer.applicationId,
        answerOrder = answerOrder,
        creationDate = answer.creationDate.toInstant,
        answerType = answer.answerType.name,
        message = answer.message,
        userInfos = UserInfos(answer.userInfos.getOrElse(Map.empty)),
        creatorUserId = answer.creatorUserID,
        creatorUserName = answer.creatorUserName,
        invitedUsers = InvitedUsers(answer.invitedUsers),
        invitedGroupIds = answer.invitedGroupIds,
        visibleByHelpers = answer.visibleByHelpers,
        declareApplicationIsIrrelevant = answer.declareApplicationHasIrrelevant
      )

  }

  case class AnswerRow(
      id: UUID,
      applicationId: UUID,
      answerOrder: Int,
      creationDate: Instant,
      answerType: String,
      message: String,
      userInfos: UserInfos,
      creatorUserId: UUID,
      creatorUserName: String,
      invitedUsers: InvitedUsers,
      invitedGroupIds: List[UUID],
      visibleByHelpers: Boolean,
      declareApplicationIsIrrelevant: Boolean,
  ) {

    def toAnswer: Answer = models.Answer(
      id = id,
      applicationId = applicationId,
      creationDate = creationDate.atZone(Time.timeZoneParis),
      answerType = models.Answer.AnswerType.fromString(answerType),
      message = message,
      creatorUserID = creatorUserId,
      creatorUserName = creatorUserName,
      invitedUsers = invitedUsers.invitedUsers,
      visibleByHelpers = visibleByHelpers,
      declareApplicationHasIrrelevant = declareApplicationIsIrrelevant,
      userInfos = Some(userInfos.userInfos),
      invitedGroupIds = invitedGroupIds,
    )

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
        passwordActivated = user.passwordActivated,
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
      internalSupportComment: Option[String],
      passwordActivated: Boolean,
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
      internalSupportComment = internalSupportComment,
      passwordActivated = passwordActivated,
    )

  }

}
