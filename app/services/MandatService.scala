package services

import anorm._
import helper.Time
import java.util.UUID
import javax.inject.Inject
import models.{Authorization, Error, EventType, User}
import models.Authorization.UserRights
import models.mandat.{Mandat, SmsMandatInitiation}
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try
import serializers.ApiModel.CompleteSms

/** This is a "low-level" component, akin to Java's "repositories".
  * This component does not represent the actual business level model.
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
      "creation_date",
      "application_id",
      "usager_prenom",
      "usager_nom",
      "usager_birth_date",
      "usager_phone_local",
      "sms_thread",
      "sms_thread_closed"
    )

  private def byIdNoAuthorizationCheck(id: Mandat.Id): Future[Either[Error, Mandat]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          SQL"""SELECT * FROM mandat WHERE id = ${id.underlying}::uuid"""
            .as(mandatRowParser.singleOpt)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.MandatError,
            s"Impossible d'accéder au mandat ${id.underlying}",
            e
          )
        )
        .flatMap {
          case None =>
            Left(
              Error.EntityNotFound(
                EventType.MandatNotFound,
                s"Tentative d'accès à un mandat non existant: ${id.underlying}"
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
              s"Tentative d'accès à un mandat non autorisé: ${id.underlying}"
            )
          )
        }
      )
    )

  /** `error.getMessage` example:
    * org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "mandat_pkey"
    * Detail: Key (id)=(2254c6ca-0002-456f-9377-2ebb48e4d48c) already exists.
    */
  private def checkUniqueKeyConstraintViolation(error: Throwable, fieldValue: String): Boolean =
    error.getMessage.contains("duplicate key value violates unique constraint") &&
      error.getMessage.contains(fieldValue) &&
      error.getMessage.contains("already exists")

  /** Initiate a `Mandat` using SMS */
  def createSmsMandat(
      initiation: SmsMandatInitiation,
      user: User
  ): Future[Either[Error, Mandat]] = {
    def tryInsert(id: UUID): Try[Mandat] = Try {
      val now = Time.nowParis()
      db.withTransaction { implicit connection =>
        SQL"""
          INSERT INTO mandat (
            id,
            user_id,
            creation_date,
            usager_prenom,
            usager_nom,
            usager_birth_date,
            usager_phone_local
          ) VALUES (
            ${id}::uuid,
            ${user.id}::uuid,
            $now,
            ${initiation.usagerPrenom},
            ${initiation.usagerNom},
            ${initiation.usagerBirthDate},
            ${initiation.usagerPhoneLocal}
          )
        """.executeInsert(SqlParser.scalar[UUID].singleOpt)

        SQL"""SELECT * FROM mandat WHERE id = $id::uuid"""
          .as(mandatRowParser.singleOpt)
          // `.get` is OK here, we want to rollback if we cannot get back the entity
          .get
      }
    }

    def iter(loopNr: Int, maxIterNr: Int): Either[Error, Mandat] = {
      val id = UUID.randomUUID
      tryInsert(id).toEither.left.flatMap { error =>
        if (checkUniqueKeyConstraintViolation(error, id.toString)) {
          if (loopNr >= maxIterNr) {
            Left(
              Error.SqlException(
                EventType.MandatError,
                s"Impossible de créer un mandat par l'utilisateur ${user.id} " +
                  s"après $maxIterNr essais avec des UUID différentes",
                error
              )
            )
          } else {
            iter(loopNr + 1, maxIterNr)
          }
        } else {
          Left(
            Error.SqlException(
              EventType.MandatError,
              s"Impossible de créer un mandat par l'utilisateur ${user.id}",
              error
            )
          )
        }
      }
    }

    Future(iter(1, 5))
  }

  def linkToApplication(id: Mandat.Id, applicationId: UUID): Future[Either[Error, Unit]] = Future(
    Try(
      db.withConnection { implicit connection =>
        SQL"""UPDATE mandat
                SET application_id = ${applicationId}::uuid
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
            e
          )
        ),
      (nrOfRows: Int) =>
        if (nrOfRows == 1)
          Right(())
        else
          Left(
            Error.Database(
              EventType.ApplicationLinkedToMandatError,
              s"Impossible de faire le lien entre le mandat $id et la demande $applicationId : " +
                s"nombre de lignes mises à jour incorrect ($nrOfRows)"
            )
          )
    )
  )

  def addSmsToMandat(id: Mandat.Id, remoteSms: CompleteSms): Future[Either[Error, Unit]] = Future(
    Try(
      db.withConnection { implicit connection =>
        SQL"""UPDATE mandat
            SET sms_thread = sms_thread || ${remoteSms.json}::jsonb
            WHERE id = ${id.underlying}::uuid
         """
          .executeUpdate()
        ()
      }
    ).toEither.left.map(e =>
      Error.SqlException(
        EventType.MandatError,
        s"Impossible d'ajouter le SMS ${remoteSms.sms.id.underlying} " +
          s"créé à ${remoteSms.sms.createdDatetime} au mandat $id",
        e
      )
    )
  )

  def addSmsResponse(localPhone: String, remoteSms: CompleteSms): Future[Either[Error, Mandat.Id]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          // Check if a thread is open
          val allOpenMandats = SQL"""SELECT * FROM mandat
                                WHERE usager_phone_local = $localPhone
                                AND sms_thread_closed = false
                             """
            .as(mandatRowParser.*)
          allOpenMandats.headOption match {
            case None =>
              Left(
                Error.Database(
                  EventType.MandatNotFound,
                  s"Le SMS ${remoteSms.sms.id} émis à ${remoteSms.sms.createdDatetime} " +
                    s"n'a pas de mandat en cours de validation toujours ouvert"
                )
              )
            case Some(mandat) =>
              SQL"""UPDATE mandat
                    SET sms_thread = sms_thread || ${remoteSms.json}::jsonb,
                        sms_thread_closed = true
                    WHERE usager_phone_local = $localPhone
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
            s"Impossible d'ajouter le SMS ${remoteSms.sms.id} " +
              s"émis à ${remoteSms.sms.createdDatetime} à un mandat en cours",
            e
          )
        )
        .flatten
    )

}
