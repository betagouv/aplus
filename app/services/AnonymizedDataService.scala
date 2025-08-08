package services

import anorm._
import anorm.SqlParser.scalar
import anorm.postgresql.jsValueToStatement
import cats.syntax.all._
import helper.{Pseudonymizer, Time, UUIDHelper}
import java.sql.Connection
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import models.{dataModels, Application, EventType, SignupRequest}
import models.dataModels.{AnswerRow, ApplicationRow, UserRow}
import modules.AppConfig
import play.api.db.Database
import play.api.libs.json.Json
import play.db.NamedDatabase
import scala.annotation.tailrec

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
    passwordService: PasswordService,
    signupService: SignupService,
    userGroupService: UserGroupService,
    userService: UserService,
) {

  import dataModels.SmsFormats._

  // The table `login_token` has data but is not exported
  def transferData(): Unit = {
    anonymizedDatabase.withTransaction { anonConn =>
      truncateData()(anonConn)
      insertApplicationsAndAnswers()(anonConn)
      insertFileMetadata()(anonConn)
      insertFranceServices()(anonConn)
      insertMandats()(anonConn)
      insertPasswords()(anonConn)
      insertSignupRequests()(anonConn)
      insertUsers()(anonConn)
      insertUserGroups()(anonConn)
    }
    // The event table is big and rows are not mutated
    insertEvents()
  }

  private def truncateData()(implicit connection: Connection): Unit = {
    val _ = SQL("""DELETE FROM answer""").execute()
    val _ = SQL("""DELETE FROM application""").execute()
    val _ = SQL("""DELETE FROM file_metadata""").execute()
    val _ = SQL("""DELETE FROM france_service""").execute()
    val _ = SQL("""DELETE FROM mandat""").execute()
    val _ = SQL("""DELETE FROM password""").execute()
    val _ = SQL("""DELETE FROM signup_request""").execute()
    val _ = SQL("""DELETE FROM "user"""").execute()
    val _ = SQL("""DELETE FROM user_group""").execute()
  }

  private def insertApplicationsAndAnswers()(implicit connection: Connection): Unit = {

    def insertApplications(applications: List[Application]): (Int, Int) = {
      val applicationsRows = applications.map(ApplicationRow.fromApplication)
      val applicationsCount = applicationsRows.size
      val applicationQuery =
        """INSERT INTO application (
           id,
           creation_date,
           creator_user_name,
           creator_user_id,
           creator_group_id,
           creator_group_name,
           subject,
           description,
           user_infos,
           invited_users,
           area,
           irrelevant,
           internal_id,
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
           {creatorGroupId}::uuid,
           {creatorGroupName},
           '',
           '',
           {userInfos}::jsonb,
           {invitedUsers}::jsonb,
           {area}::uuid,
           {irrelevant},
           {internalId},
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
      applicationsRows.foreach { row =>
        val params = Seq[NamedParameter](
          "id" -> row.id,
          "creationDate" -> row.creationDate,
          "creatorUserName" -> row.creatorUserName,
          "creatorUserId" -> row.creatorUserId,
          "creatorGroupId" -> row.creatorGroupId,
          "creatorGroupName" -> row.creatorGroupName,
          "userInfos" -> row.userInfos,
          "invitedUsers" -> row.invitedUsers,
          "area" -> row.area,
          "irrelevant" -> row.irrelevant,
          "internalId" -> row.internalId,
          "closed" -> row.closed,
          "usefulness" -> row.usefulness,
          "closedDate" -> row.closedDate,
          "expertInvited" -> row.expertInvited,
          "hasSelectedSubject" -> row.hasSelectedSubject,
          "category" -> row.category,
          "mandatType" -> row.mandatType,
          "mandatDate" -> row.mandatDate,
          "seenByUserIds" -> row.seenByUsers,
          "invitedGroupIds" -> row.invitedGroupIds,
          "personalDataWiped" -> row.personalDataWiped,
        )
        SQL(applicationQuery).on(params: _*).execute()
      }

      val answersRows = applications.flatMap(_.answers.zipWithIndex.map { case (answer, index) =>
        AnswerRow.fromAnswer(answer, index + 1)
      })
      val answersCount = answersRows.size
      val answersQuery =
        """
        INSERT INTO answer (
          id,
          application_id,
          answer_order,
          creation_date,
          answer_type,
          message,
          user_infos,
          creator_user_id,
          creator_user_name,
          invited_users,
          invited_group_ids,
          visible_by_helpers,
          declare_application_is_irrelevant
        ) VALUES (
          {id}::uuid,
          {applicationId}::uuid,
          {answerOrder},
          {creationDate},
          {answerType},
          '',
          {userInfos},
          {creatorUserId}::uuid,
          {creatorUserName},
          {invitedUsers},
          array[{invitedGroupIds}]::uuid[],
          {visibleByHelpers},
          {declareApplicationIsIrrelevant}
        )"""
      answersRows.foreach { row =>
        val params = Seq[NamedParameter](
          "id" -> row.id,
          "applicationId" -> row.applicationId,
          "answerOrder" -> row.answerOrder,
          "creationDate" -> row.creationDate,
          "answerType" -> row.answerType,
          "userInfos" -> row.userInfos,
          "creatorUserId" -> row.creatorUserId,
          "creatorUserName" -> row.creatorUserName,
          "invitedUsers" -> row.invitedUsers,
          "invitedGroupIds" -> row.invitedGroupIds,
          "visibleByHelpers" -> row.visibleByHelpers,
          "declareApplicationIsIrrelevant" -> row.declareApplicationIsIrrelevant
        )
        SQL(answersQuery).on(params: _*).execute()
      }
      (applicationsCount, answersCount)
    }

    val batchSize = 10000
    applicationService.lastInternalIdOrThrow match {
      case None                 => // Nothing to do, there are no application
      case Some(lastInternalId) =>
        val (applicationsCount, answersCount) =
          (0 to lastInternalId).by(batchSize).foldLeft((0, 0)) {
            case ((previousApplicationsCount, previousAnswersCount), firstIdToInsert) =>
              val applications = applicationService
                .byInternalIdBetweenOrThrow(firstIdToInsert, firstIdToInsert + batchSize)
                .map(_.anonymize)
              val (insertedApplications, insertedAnswers) = insertApplications(applications)
              (
                previousApplicationsCount + insertedApplications,
                previousAnswersCount + insertedAnswers
              )
          }
        logMessage(s"Table application : " + applicationsCount + " lignes")
        logMessage(s"Table answer : " + answersCount + " lignes")
    }
  }

  private def insertEvents(): Unit = {
    val batchSize = 100

    def insertBatch(before: Instant): Option[(Int, Instant)] = anonymizedDatabase.withConnection {
      implicit conn =>
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
           ) ON CONFLICT (id) DO NOTHING"""
        val numberOfUpdates = rows.foldLeft(0) { (acc, row) =>
          val params = Seq[NamedParameter](
            "id" -> row.id,
            "level" -> row.level,
            "code" -> row.code,
            "fromUserName" -> row.fromUserName,
            "fromUserId" -> row.fromUserId,
            "creationDate" -> Time.truncateAtHour(Time.timeZoneParis)(row.creationDate, 8),
            "description" -> row.description,
            "area" -> row.area,
            "toApplicationId" -> row.toApplicationId,
            "toUserId" -> row.toUserId,
            "ipAddress" -> "0.0.0.0",
          )
          val updates = SQL(query).on(params: _*).executeUpdate()
          acc + updates
        }
        val lastDate = rows.map(_.creationDate).minOption
        lastDate.map(date => (numberOfUpdates, date))
    }

    @tailrec
    def insertRecursive(before: Instant, after: Option[Instant], totalRows: Int): Int =
      after match {
        case Some(afterDate) if afterDate.isAfter(before) => totalRows
        case _                                            =>
          insertBatch(before) match {
            case None                         => totalRows
            case Some((rowsNumber, lastDate)) =>
              insertRecursive(lastDate, after, totalRows + rowsNumber)
          }
      }

    val earliestEventDate = anonymizedDatabase.withConnection { implicit conn =>
      SQL"""SELECT MIN(creation_date) FROM event""".as(scalar[Instant].singleOpt)
    }
    val latestEventDate = anonymizedDatabase.withConnection { implicit conn =>
      SQL"""SELECT MAX(creation_date) FROM event""".as(scalar[Instant].singleOpt)
    }

    val rowsNumber = (earliestEventDate, latestEventDate) match {
      case (Some(earliest), Some(latest)) =>
        val olderEvents = insertRecursive(earliest.plus(24, ChronoUnit.HOURS), None, 0)
        val newerEvents =
          insertRecursive(Instant.now(), Some(latest.minus(24, ChronoUnit.HOURS)), 0)
        olderEvents + newerEvents
      case _ => insertRecursive(Instant.now(), None, 0)
    }
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
      val uploadDate = Time.truncateAtHour(Time.timeZoneParis)(row.uploadDate, 12)
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
           version,
           user_id,
           creation_date,
           application_id,
           group_id,
           usager_prenom,
           usager_nom,
           usager_birth_date,
           usager_phone_local,
           sms_thread,
           sms_thread_closed,
           personal_data_wiped
         ) VALUES (
           {id}::uuid,
           {version},
           {userId}::uuid,
           {creationDate},
           {applicationId}::uuid,
           {groupId}::uuid,
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
        "version" -> row.version,
        "userId" -> row.userId,
        "creationDate" -> row.creationDate.toLocalDate
          .atStartOfDay(Time.timeZoneParis)
          .withHour(12),
        "applicationId" -> row.applicationId,
        "groupId" -> row.groupId,
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

  private def insertPasswords()(implicit connection: Connection): Unit = {
    val rows = passwordService.allOrThrow
    val query =
      """INSERT INTO password (
           user_id,
           password_hash,
           creation_date,
           last_update
         ) VALUES (
           {userId}::uuid,
           '',
           {creationDate},
           {lastUpdate}
         )"""
    rows.foreach { row =>
      val params = Seq[NamedParameter](
        "userId" -> row.userId,
        "creationDate" -> row.creationDate,
        "lastUpdate" -> row.lastUpdate,
      )
      SQL(query).on(params: _*).execute()
    }
    logMessage(s"Table password : " + rows.size + " lignes")
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
           first_login_date,
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
           {firstLoginDate},
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
        "firstLoginDate" -> row.firstLoginDate,
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
