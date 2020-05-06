package services

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.Future

import javax.inject.Inject
import anorm.Column.nonNull
import models.{Answer, Application, Authorization, Error, User}
import models.Authorization.UserRights
import play.api.db.Database
import play.api.libs.json.Json
import anorm._
import helper.Time
import serializers.DataModel
import scala.util.Try

// TODO:
import services.ApiSms
import models.mandat._

/** This is a "low-level" component, akin to Java's "repositories".
  * This component does not represent the acual business level model.
  * "high-level" code is in the corresponding controller.
  */
@javax.inject.Singleton
class MandatService @Inject() (
    db: Database,
    dependencies: ServicesDependencies
) {
  import dependencies.databaseExecutionContext

  import serializers.Anorm._

  implicit val mandatIdAnormParser: anorm.Column[Mandat.Id] =
    implicitly[anorm.Column[UUID]].map(Mandat.Id.apply)

  private val mandatRowParser: RowParser[Mandat] = Macro
    .parser[Mandat](
      "id",
      "user_id",
      "initiation_date",
      "application_id",
      "enduser_prenom",
      "enduser_nom",
      "enduser_birth_date",
      "enduser_phone_local",
      "sms_thread",
      "sms_thread_closed"
    )

  def byId(id: Mandat.Id): Future[Either[Error, Option[Mandat]]] = Future {
    db.withTransaction { implicit connection =>
      Right(
        Try(
          SQL"""SELECT * FROM mandat WHERE id = ${id.underlying}::uuid"""
            .as(mandatRowParser.singleOpt)
        ).get // TODO
      )
    }
  }

  /** Initiate a `Mandat` using SMS */
  // TODO: validations here? or?
  // TODO: try 5 UUID
  def createSmsMandat(initiation: SmsMandatInitiation, user: User): Future[Either[Error, Mandat]] =
    Future {
      val id = UUID.randomUUID
      val now = Time.nowParis()

      Right(Try {
        db.withTransaction {
          implicit connection =>
            SQL"""
          INSERT INTO mandat (
            id,
            user_id,
            initiation_date,
            enduser_prenom,
            enduser_nom,
            enduser_birth_date,
            enduser_phone_local
          ) VALUES (
            ${id}::uuid,
            ${user.id}::uuid,
            $now,
            ${initiation.enduserPrenom},
            ${initiation.enduserNom},
            ${initiation.enduserBirthDate},
            ${initiation.enduserPhoneLocal}
          )
        """.executeInsert(SqlParser.scalar[UUID].singleOpt)

            SQL"""SELECT * FROM mandat WHERE id = $id::uuid"""
              .as(mandatRowParser.singleOpt)
              .get
        }
      }.get)

    }

  def linkApplication(id: Mandat.Id, applicationId: UUID): Future[Either[Error, Int]] = Future {
    Right(
      // TODO: Try
      db.withConnection { implicit connection =>
        SQL"""UPDATE mandat
            SET application_id = ${applicationId}::uuid
            WHERE id = ${id.underlying}::uuid
         """
          .executeUpdate()
      }
    )
  }

  def addSmsToMandat(id: Mandat.Id, remoteSms: CompleteSms): Future[Either[Error, Int]] = Future {
    Right(
      // TODO: Try
      db.withConnection { implicit connection =>
        SQL"""UPDATE mandat
            SET sms_thread = sms_thread || ${remoteSms.json}::jsonb
            WHERE id = ${id.underlying}::uuid
         """
          .executeUpdate()
      }
    )
  }

  // TODO: What if enduser send an incorrect response the first time? close sms_thread after some time?
  def addSmsResponse(remoteSms: CompleteSms): Future[Either[Error, Mandat.Id]] = Future {
    Right(
      Try {
        // TODO: .get
        val localPhone: String = ApiSms.internationalToLocalPhone(remoteSms.sms.originator).get

        db.withTransaction {
          implicit connection =>
            // Check if a thread is open
            val allOpenMandats = SQL"""SELECT * FROM mandat
                                WHERE enduser_phone_local = $localPhone
                                AND sms_thread_closed = false
                             """
              .as(mandatRowParser.*)
            val mandatOpt = allOpenMandats.headOption
            // if (allOpenMandats.size > 1) TODO
            mandatOpt match {
              case None => ??? // TODO
              case Some(mandat) =>
                SQL"""UPDATE mandat
                    SET sms_thread = sms_thread || ${remoteSms.json}::jsonb,
                        sms_thread_closed = true
                    WHERE enduser_phone_local = ${localPhone}
                    AND sms_thread_closed = false
                 """
                  .executeUpdate()
                mandat.id
            }
        }
      }.get // TODO
    )
  }

}
