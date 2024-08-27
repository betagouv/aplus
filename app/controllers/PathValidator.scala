package controllers

import java.util.UUID
import play.api.mvc.Call
import scala.util.Try
import scala.util.matching.Regex

object PathValidator {

  // We put in the whitelist paths used in emails and
  // paths that might be used as bookmarks.
  //
  // Note that we cannot use Play's router to validate an url, hence the regexes.
  private val pathWhitelist: List[Regex] = {
    val placeholder = "00000000-0000-0000-0000-000000000000"
    val placeholderUUID = UUID.fromString(placeholder)
    val calls: List[Call] = List(
      routes.HomeController.index,
      routes.HomeController.help,
      routes.HomeController.welcome,
      routes.ApplicationController.create,
      routes.ApplicationController.myApplications,
      routes.ApplicationController.show(placeholderUUID),
      routes.MandatController.mandat(placeholderUUID),
      routes.ApplicationController.stats,
      routes.UserController.showEditProfile,
      routes.UserController.home,
      routes.UserController.editUser(placeholderUUID),
      routes.GroupController.showEditMyGroups,
      routes.GroupController.editGroup(placeholderUUID),
      routes.UserController.add(placeholderUUID),
      routes.UserController.showValidateAccount,
      routes.AreaController.all,
      routes.AreaController.deploymentDashboard,
      routes.AreaController.franceServiceDeploymentDashboard,
      routes.ApplicationController.applicationsAdmin,
      routes.UserController.all(placeholderUUID),
    )
    val uuidRegex = "([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})"
    calls.map(call =>
      // this compiles the regex
      new Regex("^" + call.path().replace(placeholder, uuidRegex) + "$")
    )
  }

  def isValidPath(path: String): Boolean =
    pathWhitelist.exists { pathRegex =>
      path match {
        case pathRegex(uuids @ _*) =>
          uuids.forall(uuid => Try(UUID.fromString(uuid)).isSuccess)
        case _ => false
      }
    }

}
