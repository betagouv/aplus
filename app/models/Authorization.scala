package models

import java.util.UUID

// TODO: rename file with upper case
object Authorization {

  // TODO:
  // def readUserRights(user: User, adminAreas: List[Area]): UserRights
  def readUserRights(user: User): UserRights = {
    import UserRight._
    UserRights(
      Set[Option[UserRight]](
        Some(HasUserId(user.id)),
        if (user.helper) Some(Helper) else None,
        if (user.admin) Some(AdminOfAreas(user.areas.toSet)) else None,
        if (user.instructor) Some(InstructorOfGroups(user.groupIds.toSet)) else None,
        if (user.groupAdmin) Some(ManagerOfGroups(user.groupIds.toSet)) else None
        // if (user.observableOrganisationIds.nonEmpty) Some(ObserverOfOrganisations(user.observableOrganisationIds)) else None
      ).flatten
    )
  }

  private[Authorization] sealed trait UserRight

  object UserRight {
    case class HasUserId(id: UUID)                                            extends UserRight
    case object Helper                                                        extends UserRight
    case class InstructorOfGroups(groupsManaged: Set[UUID])                   extends UserRight
    case class AdminOfAreas(areasAdministrated: Set[UUID])                    extends UserRight
    case class ManagerOfGroups(groupsManaged: Set[UUID])                      extends UserRight
    case class ObserverOfOrganisations(organisations: Set[Organisation.Id])   extends UserRight
  }

  /** Attached to a User
    * `rights` is private to authorization,
    * this enforces that all possible checks are in this package
    */
  case class UserRights(
      private[Authorization] rights: Set[UserRight]
  )

  // List of all possible authorization checks
  // TODO: Maybe rename `checks` or `authorizationChecks`
 
  type Check = UserRights => Boolean
  // Idea: nice authz messages
  case class AuthorizationCheck(check: UserRights => Boolean, message: String)

  //def isAdmin: Check =
    // _.rights.contains(UserRight.Admin)

  def isAdminOfArea(area: UUID): Check =
    _.rights.exists {
      case UserRight.AdminOfAreas(administredAreas) if administredAreas.contains(area) => true
      case _                                                                           => false
    }

  def isAdminOfOneOfAreas(areas: Set[UUID]): Check =
    rights => areas.exists(area => isAdminOfArea(area)(rights))

  def userCanBeEditedBy(editorUser: User): Check =
    _ => editorUser.admin && editorUser.areas.intersect(editorUser.areas).nonEmpty

  def canEditOtherUser(editedUser: User): Check =
    rights => isAdminOfOneOfAreas(editedUser.areas.toSet)(rights)

  // TODO:
  // real code is in Application.scala:
  /*
def canBeShowedBy(user: User) =
  user.admin ||
    ((user.instructor || user.helper) && not(user.expert) && invitedUsers.keys.toList
      .contains(user.id)) ||
    (user.expert && invitedUsers.keys.toList.contains(user.id) && !closed) ||
    creatorUserId == user.id
    */
  def canSeeApplication(application: Application): Check =
    _.rights.exists {
      case UserRight.HasUserId(id) => application.invitedUsers.isDefinedAt(id)
      case _                       => false
    }
  def canSeePrivateDataOfApplication(application: Application): Check = ???
   // if ((application
    //              .haveUserInvitedOn(request.currentUser) || request.currentUser.id == application.creatorUserId) && request.currentUser.expert && request.currentUser.admin && !application.closed) {


}
