package services

import models.{Error, EventType}
import models.{AccountCreation, AccountCreationRequest, AccountCreationSignature}
import anorm._
import aplus.macros.Macros
import cats.syntax.all._
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import play.api.db.Database
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}
import models.Organisation

@Singleton
class AccountCreationService @Inject() (
    db: Database
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
          SELECT ${formFields.mkString(", ")}, ${signatureFields.mkString(", ")}
          FROM account_creation_request
          LEFT JOIN account_creation_request_signature
            ON account_creation_request.id = account_creation_request_signature.form_id
          WHERE account_creation_request.id = {id}::uuid
        """)
          .on("id" -> id)
          .as((accountCreationFormParser ~ accountCreationFormSignatureParser.?).*)
          .groupBy(_._1)
          .map { case (form, signatures) =>
            AccountCreation(form, signatures.flatMap(_._2))
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
          SELECT ${formFields.mkString(", ")}, ${signatureFields.mkString(", ")}
          FROM account_creation_request
          LEFT JOIN account_creation_request_signature ON account_creation_request.id = account_creation_request_signature.form_id
          WHERE account_creation_request.area_ids && array[{areaIds}]::uuid[]
          AND account_creation_request.organisation_id = ANY(array[{organisationIds}])
        """)
            .on("areaIds" -> areaIds)
            .on("organisationIds" -> organisationIds.map(_.id.toString))
            .as((accountCreationFormParser ~ accountCreationFormSignatureParser.?).*)
            .groupBy(_._1)
            .map { case (form, signatures) =>
              AccountCreation(form, signatures.flatMap(_._2))
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
            ${accountCreation.form.rejectionUserId}::uuid,
            ${accountCreation.form.rejectionDate},
            ${accountCreation.form.rejectionReason}
          )
        """.executeUpdate()

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

}
