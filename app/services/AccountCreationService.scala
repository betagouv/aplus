package services

import anorm._
import aplus.macros.Macros
import cats.effect.IO
import cats.syntax.all._
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.{Inject, Singleton}
import models.{
  AccountCreation,
  AccountCreationRequest,
  AccountCreationSignature,
  AccountCreationStats,
  Error,
  EventType,
  Organisation
}
import modules.AppConfig
import play.api.db.Database
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

@Singleton
class AccountCreationService @Inject() (
    config: AppConfig,
    db: Database,
    notificationService: NotificationService,
)(implicit ec: ExecutionContext) {

  private val (accountCreationFormParser, formFields) =
    Macros.parserWithFields[AccountCreationRequest](
      "account_creation_request.id",
      "account_creation_request.request_date",
      "account_creation_request.email",
      "account_creation_request.is_named_account",
      "account_creation_request.first_name",
      "account_creation_request.last_name",
      "account_creation_request.phone_number",
      "account_creation_request.area_ids",
      "account_creation_request.qualite",
      "account_creation_request.organisation_id",
      "account_creation_request.misc_organisation",
      "account_creation_request.is_manager",
      "account_creation_request.is_instructor",
      "account_creation_request.message",
      "filling_ip_address",
      "account_creation_request.rejection_user_id",
      "account_creation_request.rejection_date",
      "account_creation_request.rejection_reason"
    )

  private val (accountCreationFormSignatureParser, signatureFields) =
    Macros.parserWithFields[AccountCreationSignature](
      "account_creation_request_signature.id",
      "account_creation_request_signature.form_id",
      "account_creation_request_signature.first_name",
      "account_creation_request_signature.last_name",
      "account_creation_request_signature.phone_number"
    )

  def byId(id: UUID): Future[Either[Error, Option[AccountCreation]]] = Future {
    Try {
      db.withConnection { implicit connection =>
        SQL(s"""
          SELECT
            ${formFields.mkString(", ")},
            host(account_creation_request.filling_ip_address) as filling_ip_address,
            ${signatureFields.mkString(", ")}
          FROM account_creation_request
          LEFT JOIN account_creation_request_signature
            ON account_creation_request.id = account_creation_request_signature.form_id
          WHERE account_creation_request.id = {id}::uuid
        """)
          .on("id" -> id)
          .as((accountCreationFormParser ~ accountCreationFormSignatureParser.?).*)
          .groupBy { case form ~ _ => form }
          .map { case (form, signatures) =>
            AccountCreation(form, signatures.flatMap { case _ ~ signatures => signatures })
          }
          .headOption
      }
    }.toEither.left.map { e =>
      Error.SqlException(
        EventType.SignupsError,
        s"Impossible de trouver le formulaire d'inscription $id",
        e,
        none
      )
    }
  }

  def listByAreasAndOrganisations(
      areaIds: List[UUID],
      organisationIds: List[Organisation.Id]
  ): Future[Either[Error, List[AccountCreation]]] = Future {
    Try {
      if (areaIds.isEmpty && organisationIds.isEmpty) {
        Nil
      } else {
        db.withConnection { implicit connection =>
          SQL(s"""
          SELECT
            ${formFields.mkString(", ")},
            host(account_creation_request.filling_ip_address) as filling_ip_address,
            ${signatureFields.mkString(", ")}
          FROM account_creation_request
          LEFT JOIN account_creation_request_signature ON account_creation_request.id = account_creation_request_signature.form_id
          WHERE account_creation_request.area_ids && array[{areaIds}]::uuid[]
          AND account_creation_request.organisation_id = ANY(array[{organisationIds}])
        """)
            .on("areaIds" -> areaIds)
            .on("organisationIds" -> organisationIds.map(_.id.toString))
            .as((accountCreationFormParser ~ accountCreationFormSignatureParser.?).*)
            .groupBy { case form ~ _ => form }
            .map { case (form, signatures) =>
              AccountCreation(form, signatures.flatMap { case _ ~ signatures => signatures })
            }
            .toList

        }
      }
    }.toEither.left.map { e =>
      Error.SqlException(
        EventType.SignupsError,
        s"Impossible de lister les formulaires d'inscription pour " +
          s"les départements ${areaIds.mkString(", ")} et " +
          s"les organisations ${organisationIds.map(_.id).mkString(", ")}",
        e,
        none
      )
    }
  }

  def add(accountCreation: AccountCreation): Future[Either[Error, Unit]] = Future {
    Try {
      db.withTransaction { implicit connection =>
        SQL"""
          INSERT INTO account_creation_request (
            id,
            request_date,
            email,
            is_named_account,
            first_name,
            last_name,
            phone_number,
            area_ids,
            qualite,
            organisation_id,
            misc_organisation,
            is_manager,
            is_instructor,
            message,
            filling_ip_address,
            rejection_user_id,
            rejection_date,
            rejection_reason
          )
          VALUES (
            ${accountCreation.form.id}::uuid,
            ${accountCreation.form.requestDate},
            ${accountCreation.form.email},
            ${accountCreation.form.isNamedAccount},
            ${accountCreation.form.firstName},
            ${accountCreation.form.lastName},
            ${accountCreation.form.phoneNumber},
            array[${accountCreation.form.areaIds}]::uuid[],
            ${accountCreation.form.qualite},
            ${accountCreation.form.organisationId.map(_.id)},
            ${accountCreation.form.miscOrganisation},
            ${accountCreation.form.isManager},
            ${accountCreation.form.isInstructor},
            ${accountCreation.form.message},
            ${accountCreation.form.fillingIpAddress}::inet,
            ${accountCreation.form.rejectionUserId}::uuid,
            ${accountCreation.form.rejectionDate},
            ${accountCreation.form.rejectionReason}
          )
        """.executeUpdate()

        incrementFormsCreationNumber(accountCreation.form.fillingIpAddress)

        accountCreation.signatures.foreach { signature =>
          SQL"""
            INSERT INTO account_creation_request_signature (
              id,
              form_id,
              first_name,
              last_name,
              phone_number
            )
            VALUES (
              ${signature.id}::uuid,
              ${accountCreation.form.id}::uuid,
              ${signature.firstName},
              ${signature.lastName},
              ${signature.phoneNumber}
            )
          """.executeUpdate()
        }

        ()
      }
    }.toEither.left.map { e =>
      Error.SqlException(
        EventType.SignupsError,
        s"Impossible d'enregistrer un nouveau formulaire d'inscription",
        e,
        none
      )
    }
  }

  def reject(
      formId: UUID,
      rejectionUserId: UUID,
      rejectionDate: Instant,
      rejectionReason: String
  ): Future[Either[Error, Unit]] = Future {
    Try {
      db.withConnection { implicit connection =>
        SQL"""
          UPDATE account_creation_request
          SET
            rejection_user_id = $rejectionUserId,
            rejection_date = $rejectionDate,
            rejection_reason = $rejectionReason
          WHERE id = $formId
        """.executeUpdate()
      }
      ()
    }.toEither.left.map { e =>
      Error.SqlException(
        EventType.SignupsError,
        s"Impossible de rejecter le formulaire d'inscription $formId",
        e,
        none
      )
    }
  }

  private def fetchStatsBlocking(implicit connection: Connection): Option[AccountCreationStats] = {
    import SqlParser.{double, int}
    val result =
      SQL"""
          WITH daily_counts AS (
              SELECT date_trunc('day', request_date) AS day, COUNT(*) AS count
              FROM account_creation_request
              GROUP BY day
          ),
          daily_counts_last_year AS (
              SELECT date_trunc('day', request_date) AS day, COUNT(*) AS count
              FROM account_creation_request
              WHERE request_date >= CURRENT_DATE - INTERVAL '1 year'
              GROUP BY day
          ),
          statistics AS (
              SELECT
                  MIN(count) AS min_count,
                  MAX(count) AS max_count,
                  percentile_cont(0.99) WITHIN GROUP (ORDER BY count) AS percentile_99,
                  percentile_cont(0.50) WITHIN GROUP (ORDER BY count) AS median,
                  percentile_cont(0.25) WITHIN GROUP (ORDER BY count) AS quartile_1,
                  percentile_cont(0.75) WITHIN GROUP (ORDER BY count) AS quartile_3,
                  AVG(count) AS mean,
                  STDDEV(count) AS stddev
              FROM daily_counts
          ),
          statistics_last_year AS (
              SELECT
                  MIN(count) AS min_count,
                  MAX(count) AS max_count,
                  percentile_cont(0.99) WITHIN GROUP (ORDER BY count) AS percentile_99,
                  percentile_cont(0.50) WITHIN GROUP (ORDER BY count) AS median,
                  percentile_cont(0.25) WITHIN GROUP (ORDER BY count) AS quartile_1,
                  percentile_cont(0.75) WITHIN GROUP (ORDER BY count) AS quartile_3,
                  AVG(count) AS mean,
                  STDDEV(count) AS stddev
              FROM daily_counts_last_year
          ),
          today_count AS (
              SELECT COUNT(*) AS today_count
              FROM account_creation_request
              WHERE request_date >= date_trunc('day', CURRENT_TIMESTAMP)
              AND request_date < date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '1 day'
          )
          SELECT
              s.min_count AS all_min_count,
              s.max_count AS all_max_count,
              s.percentile_99 AS all_percentile_99,
              s.median AS all_median,
              s.quartile_1 AS all_quartile_1,
              s.quartile_3 AS all_quartile_3,
              s.mean AS all_mean,
              s.stddev AS all_stddev,
              sly.min_count AS last_year_min_count,
              sly.max_count AS last_year_max_count,
              sly.percentile_99 AS last_year_percentile_99,
              sly.median AS last_year_median,
              sly.quartile_1 AS last_year_quartile_1,
              sly.quartile_3 AS last_year_quartile_3,
              sly.mean AS last_year_mean,
              sly.stddev AS last_year_stddev,
              tc.today_count
          FROM statistics s, statistics_last_year sly, today_count tc
        """
        .as(
          (
            int("all_min_count").? ~
              int("all_max_count").? ~
              double("all_percentile_99").? ~
              double("all_median").? ~
              double("all_quartile_1").? ~
              double("all_quartile_3").? ~
              double("all_mean").? ~
              double("all_stddev").? ~
              int("last_year_min_count").? ~
              int("last_year_max_count").? ~
              double("last_year_percentile_99").? ~
              double("last_year_median").? ~
              double("last_year_quartile_1").? ~
              double("last_year_quartile_3").? ~
              double("last_year_mean").? ~
              double("last_year_stddev").? ~
              int("today_count")
          ).*
        )
        .headOption

    val stats = result match {
      case Some(
            Some(allMinCount) ~
            Some(allMaxCount) ~
            Some(allPercentile99) ~
            Some(allMedian) ~
            Some(allQuartile1) ~
            Some(allQuartile3) ~
            Some(allMean) ~
            Some(allStddev) ~
            Some(lastYearMinCount) ~
            Some(lastYearMaxCount) ~
            Some(lastYearPercentile99) ~
            Some(lastYearMedian) ~
            Some(lastYearQuartile1) ~
            Some(lastYearQuartile3) ~
            Some(lastYearMean) ~
            Some(lastYearStddev) ~
            todayCount
          ) =>
        AccountCreationStats(
          todayCount = todayCount,
          yearStats = AccountCreationStats.PeriodStats(
            minCount = lastYearMinCount,
            maxCount = lastYearMaxCount,
            median = lastYearMedian,
            quartile1 = lastYearQuartile1,
            quartile3 = lastYearQuartile3,
            percentile99 = lastYearPercentile99,
            mean = lastYearMean,
            stddev = lastYearStddev
          ),
          allStats = AccountCreationStats.PeriodStats(
            minCount = allMinCount,
            maxCount = allMaxCount,
            median = allMedian,
            quartile1 = allQuartile1,
            quartile3 = allQuartile3,
            percentile99 = allPercentile99,
            mean = allMean,
            stddev = allStddev
          )
        ).some
      case _ =>
        // This case has not enough data
        // (2 days of data are required to calculate the stddev)
        none
    }

    stats
  }

  def stats(): IO[Either[Error, Option[AccountCreationStats]]] =
    IO.blocking {
      db.withConnection { implicit connection =>
        fetchStatsBlocking(connection)
      }
    }.attempt
      .map(
        _.left.map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible de calculer les statistiques des formulaires d'inscription",
            e,
            none
          )
        )
      )

  //
  // Rate limits for account creation
  //

  private val accountFormsAbuseThreshold: AtomicInteger = new AtomicInteger(
    config.accountCreationAbuseThreshold
  )

  private val accountFormsByIp: ConcurrentHashMap[String, Int] =
    new ConcurrentHashMap[String, Int]()

  private val accountFormsAbuseByIp: ConcurrentHashMap[String, Int] =
    new ConcurrentHashMap[String, Int]()

  private def incrementFormsCreationNumber(ipAddress: String) = {
    accountFormsByIp.computeIfPresent(ipAddress, (_, count) => count + 1)
    accountFormsByIp.putIfAbsent(ipAddress, 1)
  }

  private def resetFormsIps(newValues: Map[String, Int]) = {
    accountFormsByIp.clear()
    accountFormsByIp.putAll(newValues.asJava)
  }

  def checkIsAbusing(ipAddress: String): Boolean = {
    val threshold = accountFormsAbuseThreshold.get()
    val ipCount = accountFormsByIp.getOrDefault(ipAddress, 0)
    val isAbusing = ipCount >= threshold
    if (isAbusing) {
      accountFormsAbuseByIp.computeIfPresent(ipAddress, (_, count) => count + 1)
      val previous = accountFormsAbuseByIp.putIfAbsent(ipAddress, 1)
      if (previous === 0) {
        config.accountCreationAdminEmails.foreach(adminEmail =>
          notificationService.formCreationAbuseForAdmins(
            adminEmail,
            threshold,
            accountFormsByIp.asScala.toMap,
            accountFormsAbuseByIp.asScala.toMap
          )
        )
      }
    }
    isAbusing
  }

  def currentAbuseCounts(): (Int, Map[String, Int], Map[String, Int]) =
    (
      accountFormsAbuseThreshold.get(),
      accountFormsByIp.asScala.toMap,
      accountFormsAbuseByIp.asScala.toMap,
    )

  def setupRateLimits(): IO[Either[Error, Unit]] =
    IO.blocking {
      db.withConnection { implicit connection =>
        val creationStats = fetchStatsBlocking(connection)
        creationStats.foreach(stats =>
          accountFormsAbuseThreshold.set(
            stats.yearStats.quartile3.toInt + config.accountCreationAbuseThreshold
          )
        )

        val todayFormsIps =
          SQL"""
            SELECT host(filling_ip_address) as ip_address, COUNT(*) AS count
            FROM account_creation_request
            WHERE request_date >= CURRENT_DATE
            GROUP BY filling_ip_address
          """
            .as((SqlParser.str("ip_address") ~ SqlParser.int("count")).*)
            .map { case ipAddress ~ count => ipAddress -> count }
            .toMap
        resetFormsIps(todayFormsIps)
      }
    }.attempt
      .map(
        _.left.map(e =>
          Error.SqlException(
            EventType.SignupsError,
            s"Impossible d'initialiser les rate-limit de la demande de création de comte",
            e,
            none
          )
        )
      )

}
