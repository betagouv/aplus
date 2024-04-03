package services

import anorm._
import aplus.macros.Macros
import cats.syntax.all._
import helper.{PlayFormHelpers, Time}
import java.util.UUID
import javax.inject.Inject
import models.{Authorization, Error, EventType, Mandat, Sms, User}
import models.Authorization.UserRights
import models.dataModels.SmsFormats._
import models.jsonApiModels.mandat.{MandatGeneration, SmsMandatInitiation}
import play.api.db.Database
import play.api.libs.json.{JsValue, Json}
import scala.concurrent.Future
import scala.util.Try

/** This is a "low-level" component, akin to Java's "repositories".
  *
  * This component does not represent the actual business level model.
  *
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

  implicit val smsListParser: anorm.Column[List[Sms]] =
    implicitly[anorm.Column[JsValue]].mapResult(
      _.validate[List[Sms]].asEither.left.map(errors =>
        SqlMappingError(
          s"Cannot parse JSON as List[Sms]: ${PlayFormHelpers.prettifyJsonFormInvalidErrors(errors)}"
        )
      )
    )

  private val (mandatRowParser, tableFields) = Macros.parserWithFields[Mandat](
    "id",
    "version",
    "user_id",
    "creation_date",
    "application_id",
    "group_id",
    "usager_prenom",
    "usager_nom",
    "usager_birth_date",
    "usager_phone_local",
    "sms_thread",
    "sms_thread_closed",
    "personal_data_wiped",
  )

  private val fieldsInSelect: String = tableFields.mkString(", ")

  private def byIdNoAuthorizationCheck(id: Mandat.Id): Future[Either[Error, Mandat]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL(s"""SELECT $fieldsInSelect FROM mandat WHERE id = {id}::uuid""")
            .on("id" -> id.underlying)
            .as(mandatRowParser.singleOpt)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.MandatError,
            s"Impossible d'accéder au mandat ${id.underlying}",
            e,
            none
          )
        )
        .flatMap {
          case None =>
            Left(
              Error.EntityNotFound(
                EventType.MandatNotFound,
                s"Tentative d'accès à un mandat non existant : ${id.underlying}",
                none
              )
            )
          case Some(mandat) => Right(mandat)
        }
    )

  def byIdAnonymous(id: Mandat.Id): Future[Either[Error, Mandat]] =
    byIdNoAuthorizationCheck(id).map(_.map(_.anonymous))

  def byId(id: Mandat.Id, rights: UserRights): Future[Either[Error, Mandat]] =
    byIdNoAuthorizationCheck(id).map(
      _.flatMap(mandatWithPrivateInfos =>
        if (Authorization.canSeeMandat(mandatWithPrivateInfos)(rights)) {
          if (Authorization.canSeePrivateDataOfMandat(mandatWithPrivateInfos)(rights)) {
            Right(mandatWithPrivateInfos)
          } else {
            Right(mandatWithPrivateInfos.anonymous)
          }
        } else {
          Left(
            Error.Authorization(
              EventType.MandatUnauthorized,
              s"Tentative d'accès à un mandat non autorisé : ${id.underlying}",
              none
            )
          )
        }
      )
    )

  /** Initiate a `Mandat` using SMS */
  def createSmsMandat(
      initiation: SmsMandatInitiation,
      user: User
  ): Future[Either[Error, Mandat]] =
    createNewMandat(
      user.id,
      1,
      initiation.usagerPrenom,
      initiation.usagerNom,
      initiation.usagerBirthDate,
      initiation.usagerPhoneLocal.some,
      none,
    )

  def createMandatV2(
      request: MandatGeneration,
      user: User
  ): Future[Either[Error, Mandat]] =
    createNewMandat(
      user.id,
      2,
      request.usagerPrenom,
      request.usagerNom,
      request.usagerBirthdate,
      none,
      request.creatorGroupId,
    )

  private def createNewMandat(
      userId: UUID,
      version: Int,
      usagerPrenom: String,
      usagerNom: String,
      usagerBirthdate: String,
      usagerPhoneLocal: Option[String],
      creatorGroupId: Option[UUID],
  ): Future[Either[Error, Mandat]] =
    Future {
      Try {
        val id = UUID.randomUUID()
        val now = Time.nowParis()
        db.withTransaction { implicit connection =>
          SQL"""
          INSERT INTO mandat (
            id,
            version,
            user_id,
            creation_date,
            usager_prenom,
            usager_nom,
            usager_birth_date,
            usager_phone_local,
            group_id
          ) VALUES (
            $id::uuid,
            $version,
            ${userId}::uuid,
            $now,
            ${usagerPrenom},
            ${usagerNom},
            ${usagerBirthdate},
            ${usagerPhoneLocal},
            ${creatorGroupId}::uuid
          )
        """.executeInsert(SqlParser.scalar[UUID].singleOpt)

          SQL(s"""SELECT $fieldsInSelect FROM mandat WHERE id = {id}::uuid""")
            .on("id" -> id)
            .as(mandatRowParser.singleOpt)
            // `.get` is OK here, we want to rollback if we cannot get back the entity
            .get
        }
      }.toEither.left.map { error =>
        Error.SqlException(
          EventType.MandatError,
          s"Impossible de créer un mandat par l'utilisateur ${userId}",
          error,
          none
        )
      }
    }

  def linkToApplication(id: Mandat.Id, applicationId: UUID): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          SQL"""UPDATE mandat
                SET application_id = $applicationId::uuid
                WHERE id = ${id.underlying}::uuid
             """
            .executeUpdate()
        }
      ).fold(
        e =>
          Left(
            Error.SqlException(
              EventType.ApplicationLinkedToMandatError,
              s"Impossible de faire le lien entre le mandat $id et la demande $applicationId",
              e,
              none
            )
          ),
        (nrOfRows: Int) =>
          if (nrOfRows === 1)
            Right(())
          else
            Left(
              Error.Database(
                EventType.ApplicationLinkedToMandatError,
                s"Impossible de faire le lien entre le mandat $id et la demande $applicationId : " +
                  s"nombre de lignes mises à jour incorrect ($nrOfRows)",
                none
              )
            )
      )
    )

  def addSmsToMandat(id: Mandat.Id, sms: Sms): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val smsJson: JsValue = Json.toJson(sms)
          SQL"""UPDATE mandat
            SET sms_thread = sms_thread || $smsJson::jsonb
            WHERE id = ${id.underlying}::uuid
         """
            .executeUpdate()
          ()
        }
      ).toEither.left.map(e =>
        Error.SqlException(
          EventType.MandatError,
          s"Impossible d'ajouter le SMS ${sms.apiId.underlying} " +
            s"créé à ${sms.creationDate} au mandat $id",
          e,
          none
        )
      )
    )

  def addSmsResponse(sms: Sms.Incoming): Future[Either[Error, Mandat.Id]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          val localPhone = sms.originator.toLocalPhoneFrance
          // Check if a thread is open
          val allOpenMandats =
            SQL(
              s"""SELECT $fieldsInSelect
                  FROM mandat
                  WHERE usager_phone_local = {localPhone}
                  AND version = 1
                  AND sms_thread_closed = false
               """
            ).on("localPhone" -> localPhone)
              .as(mandatRowParser.*)
          allOpenMandats.headOption match {
            case None =>
              Left(
                Error.Database(
                  EventType.MandatNotFound,
                  s"Le SMS ${sms.apiId.underlying} émis à ${sms.creationDate} " +
                    s"n'a pas de mandat en cours de validation toujours ouvert",
                  none
                )
              )
            case Some(mandat) =>
              val smsJson = Json.toJson(sms: Sms)
              SQL"""UPDATE mandat
                    SET sms_thread = sms_thread || $smsJson::jsonb,
                        sms_thread_closed = true
                    WHERE usager_phone_local = $localPhone
                    AND version = 1
                    AND sms_thread_closed = false
                 """
                .executeUpdate()
              Right(mandat.id)
          }
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.MandatError,
            s"Impossible d'ajouter le SMS ${sms.apiId.underlying} " +
              s"émis à ${sms.creationDate} à un mandat en cours",
            e,
            none
          )
        )
        .flatten
    )

  def allOrThrow: List[Mandat] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fieldsInSelect FROM mandat""").as(mandatRowParser.*)
    }

  /*
  def wipePersonalData(retentionInMonths: Long): Future[Either[Error, List[Mandat]]] =
    Future(
      Try {
        val before = ZonedDateTime.now().minusMonths(retentionInMonths)
        val fields = tableFields.map(field => s"mandat.$field").mkString(", ")
        val mandats = db.withConnection { implicit connection =>
          SQL(s"""SELECT $fields
                  FROM mandat
                  LEFT JOIN application ON mandat.application_id = application.id
                  WHERE mandat.personal_data_wiped = false
                  AND (
                         (mandat.application_id IS NOT NULL
                          AND application.closed_date < {before})
                      OR (mandat.application_id IS NULL
                          AND mandat.creation_date < {before})
                  );""")
            .on("before" -> before)
            .as(mandatRowParser.*)
        }
        mandats.flatMap { mandat =>
          db.withConnection { implicit connection =>
            val wiped = mandat.withWipedPersonalData
            SQL(s"""UPDATE mandat
                    SET
                      usager_prenom = '',
                      usager_nom = '',
                      usager_birth_date = '',
                      usager_phone_local = '',
                      sms_thread = {smsThread}::jsonb,
                      personal_data_wiped = true
                    WHERE id = {id}::uuid
                    RETURNING $fieldsInSelect;""")
              .on(
                "id" -> mandat.id.underlying,
                "smsThread" -> Json.toJson(wiped.smsThread)
              )
              .as(mandatRowParser.singleOpt)
          }
        }
      }.toEither.left
        .map(e =>
          Error.SqlException(
            EventType.WipeDataError,
            s"Impossible de supprimer les informations personnelles des mandats",
            e,
            none
          )
        )
    )
   */

}
