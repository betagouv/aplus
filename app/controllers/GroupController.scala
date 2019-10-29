package controllers

import java.util.UUID

import actions.LoginAction
import extentions.Operators._
import javax.inject.{Inject, Singleton}
import models.UserGroup
import play.api.mvc.{Action, AnyContent, InjectedController}
import services.{EventService, NotificationService, UserGroupService, UserService}

@Singleton
class GroupController @Inject()(loginAction: LoginAction,
                                groupService: UserGroupService,
                                notificationService: NotificationService,
                                eventService: EventService,
                                userService: UserService) extends InjectedController with GroupOperators {

  def deleteUnusedGroupById(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(groupId) { group: UserGroup =>
      asAdminOfGroupZone(group) { () =>
        "GROUP_DELETION_UNAUTHORIZED" -> s"Droits insuffisants pour la suppression du groupe ${groupId}."
      } { () =>
        val empty = groupService.isGroupEmpty(group.id)
        if (empty) {
          groupService.deleteById(groupId)
          eventService.info("DELETE_GROUP_DONE", s"Suppression du groupe ${groupId}.")
          val path = "/" + controllers.routes.AreaController.all.relativeTo("/")
          Redirect(path, 303)
        } else {
          eventService.error(code = "USED_GROUP_DELETION_UNAUTHORIZED", description = "Tentative de supprimer un groupe utilisé.")
          Unauthorized("Group is not unused.")
        }
      }
    }
  }

  override val gs: UserGroupService = groupService
  override val es: EventService = eventService
}