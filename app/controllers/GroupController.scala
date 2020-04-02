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

import scala.collection.parallel.immutable.ParSeq

@Singleton
case class GroupController @Inject() (
    loginAction: LoginAction,
    groupService: UserGroupService,
    notificationService: NotificationService,
    eventService: EventService,
    configuration: Configuration,
    ws: WSClient,
    userService: UserService
)(implicit val webJarsUtil: WebJarsUtil)
    extends InjectedController
    with GroupOperators
    with UserOperators {

  def deleteUnusedGroupById(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(groupId) { group: UserGroup =>
      asAdminOfGroupZone(group) { () =>
        GroupDeletionUnauthorized -> s"Droits insuffisants pour la suppression du groupe ${groupId}."
      } { () =>
        val empty = groupService.isGroupEmpty(group.id)
        if (not(empty)) {
          eventService.log(
            UserGroupDeletionUnauthorized,
            description = "Tentative de suppression d'un groupe utilisé."
          )
          Unauthorized("Group is not unused.")
        } else {
          groupService.deleteById(groupId)
          eventService.log(UserGroupDeleted, s"Suppression du groupe ${groupId}.")
          Redirect(
            routes.UserController.all(group.areaIds.headOption.getOrElse(Area.allArea.id)),
            303
          )
        }
      }
    }
  }

  def editGroup(id: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(id) { group: UserGroup =>
      if (!group.canHaveUsersAddedBy(request.currentUser)) {
        eventService.log(EditGroupUnauthorized, s"Accès non autorisé à l'edition de ce groupe")
        Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?")
      } else {
        val groupUsers = userService.byGroupIds(List(id))
        eventService.log(EditGroupShowed, s"Visualise la vue de modification du groupe")
        val isEmpty = groupService.isGroupEmpty(group.id)
        Ok(
          views.html.editGroup(request.currentUser, request.rights)(
            group,
            groupUsers,
            isEmpty
          )
        )
      }
    }
  }

  def addGroup(): Action[AnyContent] = loginAction { implicit request =>
    asAdmin(() => AddGroupUnauthorized -> s"Accès non autorisé pour ajouter un groupe") { () =>
      addGroupForm(Time.timeZoneParis).bindFromRequest.fold(
        formWithErrors => {
          eventService
            .log(AddUserGroupError, s"Essai d'ajout d'un groupe avec des erreurs de validation")
          Redirect(routes.UserController.home).flashing(
            "error" -> s"Impossible d'ajouter le groupe : ${formWithErrors.errors.mkString}"
          )
        },
        group =>
          groupService
            .add(group)
            .fold(
              { error: String =>
                eventService.log(AddUserGroupError, s"Impossible d'ajouter le groupe dans la BDD")
                Redirect(routes.UserController.home)
                  .flashing("error" -> s"Impossible d'ajouter le groupe : $error")
              }, { Unit =>
                eventService.log(
                  UserGroupCreated,
                  s"Groupe ${group.name} (id : ${group.id}) ajouté par l'utilisateur d'id ${request.currentUser.id}"
                )
                Redirect(routes.GroupController.editGroup(group.id))
                  .flashing("success" -> "Groupe ajouté")
              }
            )
      )
    }
  }

  def editGroupPost(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    asAdmin(() => EditGroupUnauthorized -> s"Accès non autorisé à l'edition de ce groupe") { () =>
      withGroup(groupId) { currentGroup: UserGroup =>
        if (not(currentGroup.canHaveUsersAddedBy(request.currentUser))) {
          eventService.log(
            AddUserToGroupUnauthorized,
            s"L'utilisateur ${request.currentUser.id} n'est pas authorisé à ajouter des utilisateurs au groupe ${currentGroup.id}."
          )
          Unauthorized("Vous n'êtes pas authorisé à ajouter des utilisateurs à ce groupe.")
        } else {
          addGroupForm(Time.timeZoneParis).bindFromRequest.fold(
            formWithError => {
              eventService.log(
                EditUserGroupError,
                s"Essai d'edition d'un groupe avec des erreurs de validation"
              )
              Redirect(routes.GroupController.editGroup(groupId)).flashing(
                "error" -> s"Impossible de modifier le groupe (erreur de formulaire) : ${formWithError.errors.mkString}"
              )
            },
            group =>
              if (groupService.edit(group.copy(id = groupId))) {
                eventService.log(UserGroupEdited, s"Groupe édité")
                Redirect(routes.GroupController.editGroup(groupId))
                  .flashing("success" -> "Groupe modifié")
              } else {
                eventService
                  .log(EditUserGroupError, s"Impossible de modifier le groupe dans la BDD")
                Redirect(routes.GroupController.editGroup(groupId))
                  .flashing(
                    "error" -> "Impossible de modifier le groupe: erreur en base de donnée"
                  )
              }
          )
        }
      }
    }
  }

  def addGroupForm[A](timeZone: ZoneId)(implicit request: RequestWithUserData[A]) = Form(
    mapping(
      "id" -> ignored(UUID.randomUUID()),
      "name" -> text(maxLength = 60),
      "description" -> optional(text),
      "insee-code" -> list(text),
      "creationDate" -> ignored(ZonedDateTime.now(timeZone)),
      "area-ids" -> list(uuid)
        .verifying(
          "Vous devez sélectionner les territoires sur lequel vous êtes admin",
          areaIds => areaIds.forall(request.currentUser.areas.contains[UUID])
        )
        .verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
      "organisation" -> optional(of[Organisation.Id]),
      "email" -> optional(email)
    )(UserGroup.apply)(UserGroup.unapply)
  )
}
