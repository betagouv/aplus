package controllers

import java.util.UUID

import actions.{LoginAction, RequestWithUserData}
import extentions.Operators._
import extentions.Time
import javax.inject.{Inject, Singleton}
import models.{Area, UserGroup}
import org.joda.time.{DateTime, DateTimeZone}
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.data.Forms.{email, ignored, mapping, optional, text, uuid}
import play.api.mvc.{Action, AnyContent, InjectedController}
import services.{EventService, NotificationService, UserGroupService, UserService}

@Singleton
case class GroupController @Inject()(loginAction: LoginAction,
                                groupService: UserGroupService,
                                notificationService: NotificationService,
                                eventService: EventService,
                                userService: UserService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with GroupOperators with UserOperators {

  def deleteUnusedGroupById(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(groupId) { group: UserGroup =>
      asAdminOfGroupZone(group) { () =>
        "GROUP_DELETION_UNAUTHORIZED" -> s"Droits insuffisants pour la suppression du groupe ${groupId}."
      } { () =>
        val empty = groupService.isGroupEmpty(group.id)
        if (not(empty)) {
          eventService.error(code = "USED_GROUP_DELETION_UNAUTHORIZED", description = "Tentative de suppression d'un groupe utilisé.")
          Unauthorized("Group is not unused.")
        } else {
          groupService.deleteById(groupId)
          eventService.info("DELETE_GROUP_DONE", s"Suppression du groupe ${groupId}.")
          val path = "/" + controllers.routes.AreaController.all.relativeTo("/")
          Redirect(path, 303)
        }
      }
    }
  }

  def editGroup(id: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(id) { group: UserGroup =>
      if (!group.canHaveUsersAddedBy(request.currentUser)) {
        eventService.warn("EDIT_GROUPE_UNAUTHORIZED", s"Accès non autorisé à l'edition de ce groupe")
        Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?")
      } else {
        val groupUsers = userService.byGroupIds(List(id))
        eventService.info("EDIT_GROUP_SHOWED", s"Visualise la vue de modification du groupe")
        val isEmpty = groupService.isGroupEmpty(group.id)
        Ok(views.html.editGroup(request.currentUser, request.currentArea)(group, groupUsers, isEmpty))
      }
    }
  }

  def addGroup(): Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      "ADD_GROUP_UNAUTHORIZED" -> s"Accès non autorisé pour ajouter un groupe"
    } { () =>
      addGroupForm(Time.dateTimeZone).bindFromRequest.fold(_ => {
        eventService.error("ADD_USER_GROUP_ERROR", s"Essai d'ajout d'un groupe avec des erreurs de validation")
        BadRequest("Impossible d'ajouter le groupe") //BadRequest(views.html.editUsers(request.currentUser, request.currentArea)(formWithErrors, 0, routes.UserController.addPost()))
      }, group => {
        if (groupService.add(group)) {
          eventService.info("ADD_USER_GROUP_DONE", s"Groupe ajouté")
          Redirect(routes.GroupController.editGroup(group.id)).flashing("success" -> "Groupe ajouté")
        } else {
          eventService.error("ADD_USER_GROUP_ERROR", s"Impossible d'ajouter le groupe dans la BDD")
          Redirect(routes.UserController.all(Area.allArea.id)).flashing("success" -> "Impossible d'ajouter le groupe")
        }
      })
    }
  }

  def editGroupPost(id: UUID): Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      "EDIT_GROUPE_UNAUTHORIZED" -> s"Accès non autorisé à l'edition de ce groupe"
    } { () =>
      withGroup(id) { currentGroup: UserGroup =>
        if (not(currentGroup.canHaveUsersAddedBy(request.currentUser))) {
          eventService.warn("ADD_USER_TO_GROUP_UNAUTHORIZED", s"L'utilisateur ${request.currentUser.id} n'est pas authorisé à ajouter des utilisateurs au groupe ${currentGroup.id}.")
          Unauthorized("Vous n'êtes pas authorisé à ajouter des utilisateurs à ce groupe.")
        } else {
          addGroupForm(Time.dateTimeZone).bindFromRequest.fold(_ => {
            eventService.error("EDIT_USER_GROUP_ERROR", s"Essai d'edition d'un groupe avec des erreurs de validation")
            BadRequest("Impossible de modifier le groupe (erreur de formulaire)")
          }, group => {
            if (groupService.edit(group.copy(id = id))) {
              eventService.info("EDIT_USER_GROUP_DONE", s"Groupe édité")
              Redirect(routes.GroupController.editGroup(id)).flashing("success" -> "Groupe modifié")
            } else {
              eventService.error("EDIT_USER_GROUP_ERROR", s"Impossible de modifier le groupe dans la BDD")
              Redirect(routes.GroupController.editGroup(id)).flashing("success" -> "Impossible de modifier le groupe")
            }
          }
          )
        }
      }
    }
  }

  def addGroupForm[A](timeZone: DateTimeZone)(implicit request: RequestWithUserData[A]) = Form(
    mapping(
      "id" -> ignored(UUID.randomUUID()),
      "name" -> text(maxLength = 50),
      "description" -> optional(text),
      "insee-code" -> text,
      "creationDate" -> ignored(DateTime.now(timeZone)),
      "create-by-user-id" -> ignored(request.currentUser.id),
      "area" -> uuid.verifying("Vous devez sélectionner un territoire sur lequel vous êtes admin", area => request.currentUser.areas.contains(area)),
      "organisation" -> optional(text),
      "email" -> optional(email)
    )(UserGroup.apply)(UserGroup.unapply)
  )
}