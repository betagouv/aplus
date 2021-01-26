package controllers

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import actions.{LoginAction, RequestWithUserData}
import Operators._
import javax.inject.{Inject, Singleton}
import models.{Area, Organisation, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{email, ignored, list, mapping, of, optional, text, uuid}
import play.api.mvc.{Action, AnyContent, InjectedController}
import play.libs.ws.WSClient
import services._
import helper.BooleanHelper.not
import helper.Time
import helper.StringHelper.commonStringInputNormalization
import models.EventType.{
  AddGroupUnauthorized,
  AddUserGroupError,
  AddUserToGroupUnauthorized,
  EditGroupShowed,
  EditGroupUnauthorized,
  EditUserGroupError,
  GroupDeletionUnauthorized,
  UserGroupCreated,
  UserGroupDeleted,
  UserGroupDeletionUnauthorized,
  UserGroupEdited
}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class GroupController @Inject() (
    loginAction: LoginAction,
    groupService: UserGroupService,
    notificationService: NotificationService,
    eventService: EventService,
    configuration: Configuration,
    ws: WSClient,
    userService: UserService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with GroupOperators
    with UserOperators {

  def deleteUnusedGroupById(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { group: UserGroup =>
        asAdminOfGroupZone(group) { () =>
          GroupDeletionUnauthorized -> s"Droits insuffisants pour la suppression du groupe ${groupId}."
        } { () =>
          val empty = groupService.isGroupEmpty(group.id)
          if (not(empty)) {
            eventService.log(
              UserGroupDeletionUnauthorized,
              description = s"Tentative de suppression d'un groupe utilisé ($groupId)"
            )
            Future(Unauthorized("Le groupe est utilisé."))
          } else {
            groupService.deleteById(groupId)
            eventService.log(UserGroupDeleted, s"Groupe supprimé ${group.toLogString}")
            Future(
              Redirect(
                routes.UserController.all(group.areaIds.headOption.getOrElse(Area.allArea.id)),
                303
              )
            )
          }
        }
      }
    }

  def editGroup(id: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(id) { group: UserGroup =>
        if (!group.canHaveUsersAddedBy(request.currentUser)) {
          eventService.log(EditGroupUnauthorized, s"Accès non autorisé à l'edition de ce groupe")
          Future(
            Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?")
          )
        } else {
          val groupUsers = userService.byGroupIds(List(id), includeDisabled = true)
          eventService.log(EditGroupShowed, s"Visualise la vue de modification du groupe")
          val isEmpty = groupService.isGroupEmpty(group.id)
          Future(
            Ok(
              views.html.editGroup(request.currentUser, request.rights)(
                group,
                groupUsers,
                isEmpty
              )
            )
          )
        }
      }
    }

  def addGroup(): Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() => AddGroupUnauthorized -> s"Accès non autorisé pour ajouter un groupe") { () =>
        addGroupForm(Time.timeZoneParis)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val errorString: String = formWithErrors.errors.mkString
              eventService
                .log(
                  AddUserGroupError,
                  s"Essai d'ajout d'un groupe avec des erreurs de validation: $errorString"
                )
              Future(
                Redirect(routes.UserController.home()).flashing(
                  "error" -> s"Impossible d'ajouter le groupe : $errorString"
                )
              )
            },
            group =>
              groupService
                .add(group)
                .fold(
                  { error: String =>
                    val message =
                      s"Impossible d'ajouter le groupe dans la BDD. " +
                        group.toLogString + s" Détail de l'erreur: $error"
                    eventService
                      .log(AddUserGroupError, message)
                    Future(
                      Redirect(routes.UserController.home())
                        .flashing("error" -> s"Impossible d'ajouter le groupe : $error")
                    )
                  },
                  { _ =>
                    eventService.log(
                      UserGroupCreated,
                      s"Groupe ${group.name} ajouté par l'utilisateur d'id ${request.currentUser.id} ${group.toLogString}"
                    )
                    Future(
                      Redirect(routes.GroupController.editGroup(group.id))
                        .flashing("success" -> "Groupe ajouté")
                    )
                  }
                )
          )
      }
    }

  def editGroupPost(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() => EditGroupUnauthorized -> s"Accès non autorisé à l'edition de ce groupe") { () =>
        withGroup(groupId) { currentGroup: UserGroup =>
          if (not(currentGroup.canHaveUsersAddedBy(request.currentUser))) {
            eventService.log(
              AddUserToGroupUnauthorized,
              s"L'utilisateur ${request.currentUser.id} n'est pas authorisé à ajouter des utilisateurs au groupe ${currentGroup.id}."
            )
            Future(
              Unauthorized("Vous n'êtes pas authorisé à ajouter des utilisateurs à ce groupe.")
            )
          } else {
            addGroupForm(Time.timeZoneParis)
              .bindFromRequest()
              .fold(
                formWithError => {
                  eventService.log(
                    EditUserGroupError,
                    s"Tentative d'edition du groupe ${currentGroup.id} avec des erreurs de validation"
                  )
                  Future(
                    Redirect(routes.GroupController.editGroup(groupId)).flashing(
                      "error" -> s"Impossible de modifier le groupe (erreur de formulaire) : ${formWithError.errors.mkString}"
                    )
                  )
                },
                group => {
                  val newGroup = group.copy(id = groupId)
                  if (groupService.edit(newGroup)) {
                    eventService.log(
                      UserGroupEdited,
                      s"Groupe édité ${currentGroup.toDiffLogString(newGroup)}"
                    )
                    Future(
                      Redirect(routes.GroupController.editGroup(groupId))
                        .flashing("success" -> "Groupe modifié")
                    )
                  } else {
                    eventService
                      .log(
                        EditUserGroupError,
                        s"Impossible de modifier le groupe dans la BDD ${currentGroup.toDiffLogString(newGroup)}"
                      )
                    Future(
                      Redirect(routes.GroupController.editGroup(groupId))
                        .flashing(
                          "error" -> "Impossible de modifier le groupe: erreur en base de donnée"
                        )
                    )
                  }
                }
              )
          }
        }
      }
    }

  def addGroupForm[A](timeZone: ZoneId)(implicit request: RequestWithUserData[A]) =
    Form(
      mapping(
        "id" -> ignored(UUID.randomUUID()),
        "name" -> text(maxLength = 60)
          .transform[String](commonStringInputNormalization, commonStringInputNormalization),
        "description" -> optional(text).transform[Option[String]](
          _.map(commonStringInputNormalization).filter(_.nonEmpty),
          _.map(commonStringInputNormalization).filter(_.nonEmpty)
        ),
        "insee-code" -> list(text),
        "creationDate" -> ignored(ZonedDateTime.now(timeZone)),
        "area-ids" -> list(uuid)
          .verifying(
            "Vous devez sélectionner les territoires sur lequel vous êtes admin",
            areaIds => areaIds.forall(request.currentUser.areas.contains[UUID])
          )
          .verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
        "organisation" -> optional(of[Organisation.Id]),
        "email" -> optional(email),
        "publicNote" -> optional(text).transform[Option[String]](
          _.map(commonStringInputNormalization).filter(_.nonEmpty),
          _.map(commonStringInputNormalization).filter(_.nonEmpty)
        ),
        "internalSupportComment" -> optional(text).transform[Option[String]](
          _.map(commonStringInputNormalization).filter(_.nonEmpty),
          _.map(commonStringInputNormalization).filter(_.nonEmpty)
        )
      )(UserGroup.apply)(UserGroup.unapply)
    )

}
