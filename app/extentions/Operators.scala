package extentions

import java.util.UUID

import actions.RequestWithUserData
import models.UserGroup
import play.api.mvc.{AnyContent, Result, Results}
import services.{EventService, UserGroupService}

object Operators {

  val not: Boolean => Boolean = !_

  trait GroupOperators {

    val gs: UserGroupService
    val es: EventService

    import Results._

    def withGroup(groupId: UUID)(payload: UserGroup => Result)
                 (implicit request: RequestWithUserData[AnyContent]): Result = {
      gs.groupById(groupId).fold({
        es.error(code = "INEXISTING_GROUP", description = "Tentative d'accès à un groupe inexistant.")
        NotFound("Groupe inexistant.")
      })({ group: UserGroup =>
        payload(group)
      })
    }

    def asAdminOfGroupZone(group: UserGroup)(event: () => (String, String))(payload: () => play.api.mvc.Result)
                          (implicit request: RequestWithUserData[AnyContent]): Result = {
      if (not(request.currentUser.admin)) {
        val (code, description) = event()
        es.warn(code, description = description)
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        if (request.currentUser.areas.contains(group.area)) {
          payload()
        } else {
          es.error(code = "ADMIN_OUT_OF_RANGE", description = "L'administrateur n'est pas dans son périmètre de responsabilité.")
          Unauthorized("Vous n'êtes pas en charge de la zone de ce groupe.")
        }
      }
    }
  }
}
