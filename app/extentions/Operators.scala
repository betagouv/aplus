package extentions

import java.util.UUID

import actions.RequestWithUserData
import models.{User, UserGroup}
import play.api.mvc.{AnyContent, Result, Results}
import services.{EventService, UserGroupService, UserService}

object Operators {

  val not: Boolean => Boolean = !_

  trait GroupOperators {

    def groupService: UserGroupService
    def eventService: EventService

    import Results._

    def withGroup(groupId: UUID)(payload: UserGroup => Result)
                 (implicit request: RequestWithUserData[AnyContent]): Result = {
      groupService.groupById(groupId).fold({
        eventService.error(code = "INEXISTING_GROUP", description = "Tentative d'accès à un groupe inexistant.")
        NotFound("Groupe inexistant.")
      })({ group: UserGroup =>
        payload(group)
      })
    }

    def asAdminOfGroupZone(group: UserGroup)(event: () => (String, String))(payload: () => play.api.mvc.Result)
                          (implicit request: RequestWithUserData[AnyContent]): Result = {
      if (not(request.currentUser.admin)) {
        val (code, description) = event()
        eventService.warn(code, description = description)
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        if (request.currentUser.areas.contains(group.area)) {
          payload()
        } else {
          eventService.error(code = "ADMIN_OUT_OF_RANGE", description = "L'administrateur n'est pas dans son périmètre de responsabilité.")
          Unauthorized("Vous n'êtes pas en charge de la zone de ce groupe.")
        }
      }
    }
  }

  trait UserOperators {

    def userService: UserService
    def eventService: EventService

    import Results._

    def withUser(userId: UUID, includeDisabled: Boolean = false)(payload: User => Result)(implicit request: RequestWithUserData[AnyContent]): Result = {
      userService.byIdCheckDisabled(userId, includeDisabled).fold({
        eventService.error(code = "USER_NOT_FOUND", description = "Tentative d'accès à un utilisateur inexistant.")
        NotFound("Utilisateur inexistant.")
      })({ user: User =>
        payload(user)
      })
    }

    def asAdmin(event: () => (String, String))(payload: () => play.api.mvc.Result)(implicit request: RequestWithUserData[AnyContent]): Result = {
      if (not(request.currentUser.admin)) {
        val (code, description) = event()
        eventService.warn(code, description = description)
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        payload()
      }
    }

    def asAdminOfUserZone(user: User)(event: () => (String, String))(payload: () => play.api.mvc.Result)(implicit request: RequestWithUserData[AnyContent]): play.api.mvc.Result = {
      if (not(request.currentUser.admin)) {
        val (code, description) = event()
        eventService.warn(code, description = description)
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        if (request.currentUser.areas.intersect(user.areas).isEmpty) {
          eventService.error(code = "ADMIN_OUT_OF_RANGE", description = "L'administrateur n'est pas dans son périmètre de responsabilité.")
          Unauthorized("Vous n'êtes pas en charge de la zone de cet utilisateur.")
        } else {
          payload()
        }
      }
    }
  }
}
