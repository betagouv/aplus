package services

import anorm._
import cats.syntax.all._
import helper.{Pseudonymizer, Time, UUIDHelper}
import java.sql.Connection
import java.time.{Instant, LocalDate}
import java.util.UUID
import javax.inject.Inject
import models.{dataModels, EventType, SignupRequest}
import models.Application.SeenByUser
import models.dataModels.UserRow
import modules.AppConfig
import play.api.db.Database
import play.api.libs.json.Json
import play.db.NamedDatabase
import scala.util.Try

/** Note that the origin database is not passed as parameter here, in order to maximize the
  * probability of not deleting data by error.
  */
@javax.inject.Singleton
class AnonymizedDataService @Inject() (
    @NamedDatabase("anonymized-data") anonymizedDatabase: Database,
    applicationService: ApplicationService,
    config: AppConfig,
    dependencies: ServicesDependencies,
    eventService: EventService,
    fileService: FileService,
    mandatService: MandatService,
    signupService: SignupService,
    userGroupService: UserGroupService,
    userService: UserService,
) {

  import dataModels.Answer._
  import dataModels.Application.SeenByUser._
  import dataModels.SmsFormats._
  import serializers.Anorm._

  // The table `login_token` has data but is not exported
  def transferData(): Unit =
    anonymizedDatabase.withTransaction { anonConn =>
      truncateData()(anonConn)
      insertApplications()(anonConn)
      insertEvents()(anonConn)
      insertFileMetadata()(anonConn)
      insertFranceServices()(anonConn)
      insertMandats()(anonConn)
      insertSignupRequests()(anonConn)
      insertUsers()(anonConn)
      insertUserGroups()(anonConn)
    }

  private def truncateAtHour(instant: Instant, hour: Int): Instant =
    instant
      .atZone(Time.timeZoneParis)
      .toLocalDate
      .atStartOfDay(Time.timeZoneParis)
      .withHour(hour)
      .toInstant

  private def truncateData()(implicit connection: Connection): Unit = {
    val _ = SQL("""DELETE FROM application""").execute()
    val _ = SQL("""DELETE FROM event""").execute()
    val _ = SQL("""DELETE FROM file_metadata""").execute()
    val _ = SQL("""DELETE FROM france_service""").execute()
    val _ = SQL("""DELETE FROM mandat""").execute()
    val _ = SQL("""DELETE FROM signup_request""").execute()
    val _ = SQL("""DELETE FROM "user"""").execute()
    val _ = SQL("""DELETE FROM user_group""").execute()
  }

  private def insertApplications()(implicit connection: Connection): Unit = {
    val baseApplications = applicationService.allOrThrow
    val rows = baseApplications.map(_.anonymize)
    val query =
      """INSERT INTO application (
           id,
           creation_date,
           creator_user_name,
           creator_user_id,
           subject,
           description,
           user_infos,
           invited_users,
           area,
           irrelevant,
           internal_id,
           answers,
           closed,
           usefulness,
           closed_date,
           expert_invited,
           has_selected_subject,
           category,
           mandat_type,
           mandat_date,
           seen_by_user_ids,
           invited_group_ids,
           personal_data_wiped
         ) VALUES (
           {id}::uuid,
           {creationDate},
           {creatorUserName},
           {creatorUserId}::uuid,
           '',
           '',
           {userInfos}::jsonb,
           {invitedUsers}::jsonb,
           {area}::uuid,
           {irrelevant},
           {internalId},
           {answers}::jsonb,
           {closed},
           {usefulness},
           {closedDate},
           {expertInvited},
           {hasSelectedSubject},
           {category},
           {mandatType},
           {mandatDate},
           {seenByUserIds}::jsonb,
           array[{invitedGroupIds}]::uuid[],
           {personalDataWiped}
         )"""
    rows.foreach { row =>
      val params = Seq[NamedParameter](
        "id" -> row.id,
        "creationDate" -> row.creationDate,
        "creatorUserName" -> row.creatorUserName,
        "creatorUserId" -> row.creatorUserId,
        "userInfos" -> Json.toJson(row.userInfos),
        "invitedUsers" -> Json.toJson(
          row.invitedUsers.map { case (key, value) => key.toString -> value }
        ),
        "area" -> row.area,
        "irrelevant" -> row.irrelevant,
        "internalId" -> row.internalId,
        "answers" -> (Json.toJson(row.answers): ParameterValue),
        "closed" -> row.closed,
        "usefulness" -> row.usefulness,
        "closedDate" -> row.closedDate,
        "expertInvited" -> row.expertInvited,
        "hasSelectedSubject" -> row.hasSelectedSubject,
        "category" -> row.category,
        "mandatType" -> row.mandatType.map(
          dataModels.Application.MandatType.dataModelSerialization
        ),
        "mandatDate" -> row.mandatDate,
        "seenByUserIds" -> Json.toJson(row.seenByUsers),
        "invitedGroupIds" -> row.invitedGroupIdsAtCreation,
        "personalDataWiped" -> row.personalDataWiped,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table application : " + rows.size + " lignes")
  }

  private def insertEvents()(implicit connection: Connection): Unit = {
    val batchSize = 10
    def insertBatch(before: Instant): Option[(Int, Instant)] = {
      val rows = eventService.beforeOrThrow(before, batchSize)
      val query =
        """INSERT INTO event (
             id,
             level,
             code,
             from_user_name,
             from_user_id,
             creation_date,
             description,
             area,
             to_application_id,
             to_user_id,
             ip_address
           ) VALUES (
             {id}::uuid,
             {level},
             {code},
             {fromUserName},
             {fromUserId}::uuid,
             {creationDate},
             {description},
             {area}::uuid,
             {toApplicationId}::uuid,
             {toUserId}::uuid,
             {ipAddress}::inet
           )"""
      rows.foreach { row =>
        val params = Seq[NamedParameter](
          "id" -> row.id,
          "level" -> row.level,
          "code" -> row.code,
          "fromUserName" -> row.fromUserName,
          "fromUserId" -> row.fromUserId,
          "creationDate" -> truncateAtHour(row.creationDate, 8),
          "description" -> row.description,
          "area" -> row.area,
          "toApplicationId" -> row.toApplicationId,
          "toUserId" -> row.toUserId,
          "ipAddress" -> "0.0.0.0",
        )
        SQL(query).on(params: _*).execute()
      }
      val lastDate = rows.map(_.creationDate).minOption
      lastDate.map(date => (rows.size, date))
    }
    def insertRecursive(before: Instant): Int =
      insertBatch(before) match {
        case None                         => 0
        case Some((rowsNumber, lastDate)) => insertRecursive(lastDate) + rowsNumber
      }
    val rowsNumber = insertRecursive(Instant.now())
    logMessage(s"Table event : " + rowsNumber + " lignes")
  }

  private def insertFileMetadata()(implicit connection: Connection): Unit = {
    val rows = fileService.allOrThrow
    val query =
      """INSERT INTO file_metadata (
           id,
           upload_date,
           filename,
           filesize,
           status,
           application_id,
           answer_id
         ) VALUES (
           {id}::uuid,
           {uploadDate},
           {filename},
           {filesize},
           {status},
           {applicationId}::uuid,
           {answerId}::uuid
         )"""
    rows.foreach { row =>
      val uploadDate = truncateAtHour(row.uploadDate, 12)
      val filename =
        if (row.filename === "fichier-non-existant") "fichier-non-existant" else "fichier-anonyme"
      val params = Seq[NamedParameter](
        "id" -> row.id,
        "uploadDate" -> uploadDate,
        "filename" -> filename,
        "filesize" -> row.filesize,
        "status" -> row.status,
        "applicationId" -> row.applicationId,
        "answerId" -> row.answerId,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table file_metadata : " + rows.size + " lignes")
  }

  private def insertFranceServices()(implicit connection: Connection): Unit = {
    val rows = userGroupService.franceServiceRowsOrThrow
    val query =
      """INSERT INTO france_service (
           group_id,
           matricule
         ) VALUES (
           {groupId}::uuid,
           {matricule}
         )"""
    rows.foreach { row =>
      val params = Seq[NamedParameter](
        "groupId" -> row.groupId,
        "matricule" -> row.matricule,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table france_service : " + rows.size + " lignes")
  }

  private def insertMandats()(implicit connection: Connection): Unit = {
    val rows = mandatService.allOrThrow.map(_.withWipedPersonalData)
    val query =
      """INSERT INTO mandat (
           id,
           user_id,
           creation_date,
           application_id,
           usager_prenom,
           usager_nom,
           usager_birth_date,
           usager_phone_local,
           sms_thread,
           sms_thread_closed,
           personal_data_wiped
         ) VALUES (
           {id}::uuid,
           {userId}::uuid,
           {creationDate},
           {applicationId}::uuid,
           {usagerPrenom},
           {usagerNom},
           {usagerBirthDate},
           {usagerPhoneLocal},
           {smsThread}::jsonb,
           {smsThreadClosed},
           {personalDataWiped}
         )"""
    rows.foreach { row =>
      val params = Seq[NamedParameter](
        "id" -> row.id.underlying,
        "userId" -> row.userId,
        "creationDate" -> row.creationDate.toLocalDate
          .atStartOfDay(Time.timeZoneParis)
          .withHour(12),
        "applicationId" -> row.applicationId,
        "usagerPrenom" -> row.usagerPrenom,
        "usagerNom" -> row.usagerNom,
        "usagerBirthDate" -> row.usagerBirthDate,
        "usagerPhoneLocal" -> row.usagerPhoneLocal,
        "smsThread" -> Json.toJson(row.smsThread),
        "smsThreadClosed" -> row.smsThreadClosed,
        "personalDataWiped" -> row.personalDataWiped,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table mandat : " + rows.size + " lignes")
  }

  private def insertSignupRequests()(implicit connection: Connection): Unit = {
    val rows = signupService.allOrThrow.map { case (signup, userIdOpt) =>
      val email = userIdOpt match {
        case None       => new Pseudonymizer(UUID.randomUUID()).emailKeepingDomain(signup.email)
        case Some(uuid) => new Pseudonymizer(uuid).emailKeepingDomain(signup.email)
      }
      SignupRequest(
        id = signup.id,
        requestDate = signup.requestDate,
        email = email,
        invitingUserId = signup.invitingUserId,
      )
    }
    val query =
      """INSERT INTO signup_request (
           id,
           request_date,
           email,
           inviting_user_id
         ) VALUES (
           {id}::uuid,
           {requestDate},
           {email},
           {invitingUserId}::uuid
         )"""
    rows.foreach { row =>
      val params = Seq[NamedParameter](
        "id" -> row.id,
        "requestDate" -> row.requestDate,
        "email" -> row.email,
        "invitingUserId" -> row.invitingUserId,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table signup_request : " + rows.size + " lignes")
  }

  private def insertUsers()(implicit connection: Connection): Unit = {
    val baseUsers = userService.allOrThrow
    val rows = baseUsers.map(user =>
      UserRow.fromUser(user.pseudonymize, config.groupsWhichCannotHaveInstructors)
    )
    val query =
      """INSERT INTO "user" (
           id,
           key,
           first_name,
           last_name,
           name,
           qualite,
           email,
           helper,
           instructor,
           admin,
           areas,
           creation_date,
           commune_code,
           group_admin,
           group_ids,
           expert,
           cgu_acceptation_date,
           newsletter_acceptation_date,
           disabled,
           phone_number,
           observable_organisation_ids,
           shared_account
         ) VALUES (
           {id}::uuid,
           '',
           {firstName},
           {lastName},
           {name},
           {qualite},
           {email},
           {helper},
           {instructor},
           {admin},
           array[{areas}]::uuid[],
           {creationDate},
           {communeCode},
           {groupAdmin},
           array[{groupIds}]::uuid[],
           {expert},
           {cguAcceptationDate},
           {newsletterAcceptationDate},
           {disabled},
           {phoneNumber},
           array[{observableOrganisationIds}]::varchar[],
           {sharedAccount}
         )"""
    rows.foreach { row =>
      val params = Seq[NamedParameter](
        "id" -> row.id,
        "firstName" -> row.firstName,
        "lastName" -> row.lastName,
        "name" -> row.name,
        "qualite" -> row.qualite,
        "email" -> row.email,
        "helper" -> row.helper,
        "instructor" -> row.instructor,
        "admin" -> row.admin,
        "areas" -> row.areas,
        "creationDate" -> row.creationDate,
        "communeCode" -> row.communeCode,
        "groupAdmin" -> row.groupAdmin,
        "groupIds" -> row.groupIds,
        "expert" -> row.expert,
        "cguAcceptationDate" -> row.cguAcceptationDate,
        "newsletterAcceptationDate" -> row.newsletterAcceptationDate,
        "disabled" -> row.disabled,
        "phoneNumber" -> row.phoneNumber,
        "observableOrganisationIds" -> row.observableOrganisationIds,
        "sharedAccount" -> row.sharedAccount,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table user : " + rows.size + " lignes")
  }

  private def insertUserGroups()(implicit connection: Connection): Unit = {
    val rows = userGroupService.allOrThrow
    val query =
      """INSERT INTO user_group (
           id,
           name,
           description,
           insee_code,
           creation_date,
           create_by_user_id,
           area_ids,
           organisation,
           email,
           public_note
         ) VALUES (
           {id}::uuid,
           {name},
           {description},
           array[{inseeCode}]::character varying(5)[],
           {creationDate},
           {creationUserId}::uuid,
           array[{areaIds}]::uuid[],
           {organisationId},
           {email},
           {publicNote}
         )"""
    rows.foreach { row =>
      val params = Seq[NamedParameter](
        "id" -> row.id,
        "name" -> row.name,
        "description" -> row.description,
        "inseeCode" -> row.inseeCode,
        "creationDate" -> row.creationDate,
        "creationUserId" -> UUIDHelper.namedFrom("deprecated"),
        "areaIds" -> row.areaIds,
        "organisationId" -> row.organisationId.map(_.id),
        "email" -> row.email,
        "publicNote" -> row.publicNote,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table user_group : " + rows.size + " lignes")
  }

  private def logMessage(message: String): Unit =
    eventService.logNoRequest(EventType.AnonymizedDataExportMessage, message)

}
