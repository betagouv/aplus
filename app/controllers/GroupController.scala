package controllers

import java.util.UUID

import actions.LoginAction
import extentions.Operators._
import javax.inject.{Inject, Singleton}
import models.UserGroup
import play.api.mvc.{Action, AnyContent, InjectedController}
import services.{EventService, NotificationService, UserGroupService, UserService}

@Singleton
case class GroupController @Inject()(loginAction: LoginAction,
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

  //override val groupService: UserGroupService = groupService
  //override val eventService: EventService = eventService
}