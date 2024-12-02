package services

import anorm._
import aplus.macros.Macros
import cats.effect.IO
import cats.syntax.all._
import helper.{Time, UUIDHelper}
import java.sql.ResultSet
import java.util.UUID
import javax.inject.Inject
import models.{Error, EventType, FranceService, Organisation, User, UserGroup}
import org.postgresql.util.PSQLException
import play.api.db.Database
import scala.concurrent.Future
import scala.util.Try

@javax.inject.Singleton
class UserGroupService @Inject() (
    val db: Database,
    val dependencies: ServicesDependencies
) {

  import dependencies.databaseExecutionContext

  private val (parser, tableFields) = Macros.parserWithFields[UserGroup](
    "id",
    "name",
    "description",
    "insee_code",
    "creation_date",
    "area_ids",
    "organisation",
    "email",
    "public_note",
    "internal_support_comment"
  )

  private val simpleUserGroup =
    parser.map(a => a.copy(creationDate = a.creationDate.withZoneSameInstant(Time.timeZoneParis)))

  private val fieldsInSelect: String = tableFields.mkString(", ")

  def add(groups: List[UserGroup]): Either[String, Unit] =
    try {
      val result = db.withTransaction { implicit connection =>
        groups.foldRight(true) { (group, success) =>
          success &&
          SQL"""
      INSERT INTO user_group(id, name, description, insee_code, creation_date, create_by_user_id, area_ids, organisation, email) VALUES (
         ${group.id}::uuid,
         ${group.name},
         ${group.description},
         array[${group.inseeCode}]::character varying(5)[],
         ${group.creationDate},
         ${UUIDHelper.namedFrom("deprecated")}::uuid,
         array[${group.areaIds}]::uuid[],
         ${group.organisationId.map(_.id)},
         ${group.email})
      """.executeUpdate() === 1
        }
      }
      if (result)
        Right(())
      else
        Left("Aucun groupe n'a été ajouté")
    } catch {
      case ex: PSQLException =>
        val EmailErrorPattern =
          """[^()@]+@[^()@.]+\.[^()@]+""".r // This didn't work in that case : """ Detail: Key \(email\)=\(([^()]*)\) already exists."""".r  (don't know why, the regex is correct)
        val errorMessage = EmailErrorPattern.findFirstIn(ex.getServerErrorMessage.toString) match {
          case Some(email) => s"Un groupe avec l'adresse $email existe déjà."
          case _           => s"SQL Erreur : ${ex.getServerErrorMessage.toString}"
        }
        Left(errorMessage)
    }

  def add(group: UserGroup): Either[String, Unit] = add(List(group))

  def addGroup(group: UserGroup): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withTransaction { implicit connection =>
          val _ = SQL"""INSERT INTO user_group(
                  id,
                  name,
                  description,
                  insee_code,
                  creation_date,
                  create_by_user_id,
                  area_ids,
                  organisation,
                  email
                ) VALUES (
                  ${group.id}::uuid,
                  ${group.name},
                  ${group.description},
                  array[${group.inseeCode}]::character varying(5)[],
                  ${group.creationDate},
                  ${UUIDHelper.namedFrom("deprecated")}::uuid,
                  array[${group.areaIds}]::uuid[],
                  ${group.organisationId.map(_.id)},
                  ${group.email})
             """.executeUpdate()
          ().asRight
        }
      ).toEither.left.map {
        case sqlerror: PSQLException
            if sqlerror.getServerErrorMessage.toString.contains(
              "duplicate key value violates unique constraint \"name_user_group_unique_idx\""
            ) =>
          Error.SqlException(
            EventType.EditUserGroupBadRequest,
            "Un groupe avec le même nom existe déjà",
            sqlerror,
            s"Nom '${group.name}'".some
          )
        case e =>
          Error.SqlException(
            EventType.EditUserGroupError,
            "Impossible d'ajouter un groupe : erreur de base de données",
            e,
            none
          )
      }.flatten
    )

  def edit(group: UserGroup): Boolean =
    db.withConnection { implicit connection =>
      SQL"""
          UPDATE user_group SET
          name = ${group.name},
          description = ${group.description},
          organisation = ${group.organisationId.map(_.id)},
          area_ids = array[${group.areaIds}]::uuid[],
          email = ${group.email},
          public_note = ${group.publicNote},
          internal_support_comment = ${group.internalSupportComment}
          WHERE id = ${group.id}::uuid
       """.executeUpdate() === 1
      // TODO: insee_code = array[${group.inseeCode}]::character varying(5)[], have been remove temporary
    }

  def allOrThrow: List[UserGroup] =
    db.withConnection { implicit connection =>
      SQL(s"SELECT $fieldsInSelect FROM user_group").as(simpleUserGroup.*)
    }

  def all: IO[List[UserGroup]] = IO.blocking(allOrThrow)

  def byIds(groupIds: List[UUID]): List[UserGroup] =
    db.withConnection { implicit connection =>
      val ids = groupIds.distinct
      SQL(s"""SELECT $fieldsInSelect
              FROM user_group
              WHERE ARRAY[{ids}]::uuid[] @> ARRAY[id]::uuid[]""")
        .on("ids" -> ids)
        .as(simpleUserGroup.*)
    }

  def byIdsFuture(groupIds: List[UUID]): Future[List[UserGroup]] =
    Future(byIds(groupIds))

  def groupById(groupId: UUID): Option[UserGroup] =
    db.withConnection { implicit connection =>
      SQL(s"SELECT $fieldsInSelect FROM user_group WHERE id = {groupId}::uuid")
        .on("groupId" -> groupId)
        .as(simpleUserGroup.singleOpt)
    }

  def groupByIdFuture(groupId: UUID): Future[Option[UserGroup]] =
    Future(groupById(groupId))

  def groupByName(groupName: String): Option[UserGroup] =
    db.withConnection { implicit connection =>
      SQL(s"SELECT $fieldsInSelect FROM user_group WHERE name = {groupName}")
        .on("groupName" -> groupName)
        .as(simpleUserGroup.singleOpt)
    }

  def deleteById(groupId: UUID): Boolean =
    db.withConnection { implicit connection =>
      SQL"""DELETE FROM "user_group" WHERE id = $groupId::uuid""".execute()
    }

  def isGroupEmpty(groupId: UUID): Boolean =
    db.withConnection { implicit connection =>
      val cardinality: Int =
        SQL"""SELECT COUNT(id) as cardinality FROM "user" WHERE group_ids @> ARRAY[$groupId]::uuid[]"""
          .executeQuery()
          .resultSet
          .apply[Int]({ (rs: ResultSet) =>
            rs.next()
            rs.getInt("cardinality")
          })
      cardinality === 0
    }

  def byArea(areaId: UUID): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""SELECT $fieldsInSelect
                FROM "user_group"
                WHERE area_ids @> ARRAY[{areaId}]::uuid[]""")
          .on("areaId" -> areaId)
          .as(simpleUserGroup.*)
      }
    }

  def byAreas(areaIds: List[UUID]): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""SELECT $fieldsInSelect
                FROM "user_group"
                WHERE ARRAY[{areaIds}]::uuid[] && area_ids""")
          .on("areaIds" -> areaIds)
          .as(simpleUserGroup.*)
      }
    }

  def byOrganisationIds(organisationIds: List[Organisation.Id]): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        val organisationIdStrings = organisationIds.map(_.id)
        SQL(s"""SELECT $fieldsInSelect
                FROM "user_group"
                WHERE ARRAY[{organisationIds}]::varchar[] @> ARRAY[organisation]""")
          .on("organisationIds" -> organisationIdStrings)
          .as(simpleUserGroup.*)
      }
    }

  def byAreasAndOrganisationIds(
      areaIds: List[UUID],
      organisationIds: List[Organisation.Id]
  ): Future[List[UserGroup]] =
    Future {
      db.withConnection { implicit connection =>
        val organisationIdStrings = organisationIds.map(_.id)
        SQL(s"""SELECT $fieldsInSelect
                FROM "user_group"
                WHERE ARRAY[{areaIds}]::uuid[] && area_ids
                AND ARRAY[{organisationIds}]::varchar[] @> ARRAY[organisation]""")
          .on("areaIds" -> areaIds, "organisationIds" -> organisationIdStrings)
          .as(simpleUserGroup.*)
      }
    }

  def allForAreaManager(user: User): Future[List[UserGroup]] =
    byAreasAndOrganisationIds(user.managingAreaIds, user.managingOrganisationIds)
      .flatMap { areaGroups =>
        val areaGroupIds = areaGroups.map(_.id).toSet
        val userGroupIds = user.groupIds.toSet
        val missingGroupIds = userGroupIds.diff(areaGroupIds)
        if (missingGroupIds.isEmpty)
          Future.successful(areaGroups)
        else
          byIdsFuture(missingGroupIds.toList)
            .map(missingGroups => missingGroups ::: areaGroups)
      }

  def search(searchQuery: String, limit: Int): Future[Either[Error, List[UserGroup]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val query = UserService.toStarTsquery(searchQuery)
          SQL(s"""SELECT $fieldsInSelect
                  FROM "user_group"
                  WHERE (
                    to_tsvector('french_unaccent', name) ||
                    to_tsvector('french_unaccent', coalesce(description, '')) ||
                    to_tsvector('french_unaccent', coalesce(array_to_string(insee_code, ' ', ' '), '')) ||
                    to_tsvector('french_unaccent', coalesce(organisation, '')) ||
                    to_tsvector('french_unaccent', translate(coalesce(email, ''), '@.', '  '))
                  ) @@ to_tsquery('french_unaccent', {query})
                  LIMIT {limit}""")
            .on("query" -> query, "limit" -> limit)
            .as(simpleUserGroup.*)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.SearchUsersError,
            s"Impossible de faire une recherche",
            e,
            s"Recherche '$searchQuery'".some
          )
        )
    )

  private val (fsParser, fsTableFields) = Macros.parserWithFields[FranceService](
    "group_id",
    "matricule",
  )

  private val fsFieldsInSelect: String = fsTableFields.mkString(", ")

  def franceServiceRowsOrThrow: List[FranceService] =
    db.withConnection { implicit connection =>
      SQL(s"""SELECT $fsFieldsInSelect FROM france_service""")
        .as(fsParser.*)
    }

  def franceServices: Future[Either[Error, List[(Option[FranceService], Option[UserGroup])]]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val fields = fsFieldsInSelect + "," + fieldsInSelect
          SQL(s"""SELECT $fields
                FROM france_service
                LEFT JOIN user_group
                  ON user_group.id = france_service.group_id
                UNION
                SELECT
                  null as matricule,
                  null as group_id,
                  $fieldsInSelect
                FROM user_group
                WHERE user_group.id NOT IN (
                  SELECT group_id FROM france_service
                )
                AND organisation = 'MFS'
             """).as((fsParser.? ~ simpleUserGroup.?).map(SqlParser.flatten).*)
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FSMatriculeError,
            "Impossible de lister les matricules des France Services",
            e,
            none
          )
        )
    )

  def addFSMatricule(groupId: UUID, matricule: Int): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val existing =
            SQL(s"""SELECT $fsFieldsInSelect
                    FROM france_service
                    WHERE matricule = {matricule}
                 """)
              .on("matricule" -> matricule)
              .as(fsParser.*)
          existing match {
            case Nil =>
              val numRows =
                SQL"""INSERT INTO france_service (group_id, matricule)
                      VALUES ($groupId::uuid, $matricule)
                   """.executeUpdate()
              assert(numRows === 1)
              ().asRight
            case matricules =>
              Error
                .RequirementFailed(
                  EventType.FSMatriculeInvalidData,
                  s"Impossible d'assigner le matricule $matricule au groupe $groupId : " +
                    s"matricule déjà assigné au groupe " + matricules.map(_.groupId).mkString(", "),
                  none
                )
                .asLeft
          }
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FSMatriculeError,
            s"Impossible d'ajouter le matricule '$matricule' au groupe $groupId" +
              " : erreur de base de donnée",
            e,
            none
          )
        )
        .flatten
    )

  /** Updates the matricule of a group */
  def updateFSMatricule(groupId: UUID, newMatricule: Int): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val existing =
            SQL(s"""SELECT $fsFieldsInSelect
                    FROM france_service
                    WHERE matricule = {matricule}
                 """)
              .on("matricule" -> newMatricule)
              .as(fsParser.*)
          existing match {
            case Nil =>
              val existingMatricule =
                SQL(s"""SELECT $fsFieldsInSelect
                        FROM france_service
                        WHERE group_id = {groupId}::uuid
                     """)
                  .on("groupId" -> groupId)
                  .as(fsParser.*)
              existingMatricule match {
                case Nil =>
                  val numRows =
                    SQL"""INSERT INTO france_service (group_id, matricule)
                          VALUES ($groupId::uuid, $newMatricule)
                       """.executeUpdate()
                  assert(numRows === 1)
                case _ =>
                  val numRows =
                    SQL"""UPDATE france_service
                          SET matricule = $newMatricule
                          WHERE group_id = $groupId::uuid
                       """.executeUpdate()
                  assert(
                    numRows <= 1,
                    s"$numRows mises à jour pour le nouveau matricule '$newMatricule' groupe $groupId"
                  )
              }
              ().asRight
            case matricules =>
              Error
                .RequirementFailed(
                  EventType.FSMatriculeInvalidData,
                  s"Impossible d'assigner le nouveau matricule '$newMatricule' au groupe $groupId : " +
                    "le matricule est déjà assigné au groupe " +
                    matricules.map(_.groupId).mkString(", "),
                  none
                )
                .asLeft
          }
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FSMatriculeError,
            s"Impossible de mettre à jour le matricule du groupe $groupId " +
              s"(nouveau matricule '$newMatricule') : erreur de base de donnée",
            e,
            none
          )
        )
        .flatten
    )

  /** Updates the group with a certain matricule */
  def updateFSGroup(matricule: Int, newGroupId: UUID): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val existing =
            SQL(s"""SELECT $fsFieldsInSelect
                    FROM france_service
                    WHERE matricule = {matricule}
                 """)
              .on("matricule" -> matricule)
              .as(fsParser.*)
          existing match {
            case Nil =>
              val numRows =
                SQL"""INSERT INTO france_service (group_id, matricule)
                      VALUES ($newGroupId::uuid, $matricule)
                   """.executeUpdate()
              assert(numRows === 1)
              ().asRight
            case _ =>
              val numRows =
                SQL"""UPDATE france_service
                      SET group_id = $newGroupId::uuid
                      WHERE matricule = $matricule
                   """.executeUpdate()
              assert(
                numRows <= 1,
                s"$numRows mises à jour pour le matricule '$matricule' nouveau groupe $newGroupId"
              )
              ().asRight
          }
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FSMatriculeError,
            s"Impossible de mettre à jour le groupe du matricule $matricule " +
              s"(nouveau groupe '$newGroupId') : erreur de base de donnée",
            e,
            none
          )
        )
        .flatten
    )

  def deleteFSMatricule(matricule: Int): Future[Either[Error, Unit]] =
    Future(
      Try(
        db.withConnection { implicit connection =>
          val existing =
            SQL(s"""SELECT $fsFieldsInSelect
                    FROM france_service
                    WHERE matricule = {matricule}
                 """)
              .on("matricule" -> matricule)
              .as(fsParser.*)
          existing match {
            case Nil =>
              Error
                .RequirementFailed(
                  EventType.FSMatriculeInvalidData,
                  s"Matricule '$matricule' n'existe pas",
                  none
                )
                .asLeft
            case _ =>
              val _ =
                SQL"""DELETE FROM france_service WHERE matricule = $matricule""".executeUpdate()
              ().asRight
          }
        }
      ).toEither.left
        .map(e =>
          Error.SqlException(
            EventType.FSMatriculeError,
            s"Impossible de supprimer le matricule '$matricule'" +
              " : erreur de base de donnée",
            e,
            none
          )
        )
        .flatten
    )

}
