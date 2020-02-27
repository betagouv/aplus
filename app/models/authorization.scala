package models

import java.util.UUID

object authorization {

  def readUserRights(user: User): UserRights = {
    import UserRight._
    UserRights(
      Set[Option[UserRight]](
        Some(HasUserId(user.id)),
        if (user.admin) Some(Admin) else None
      ).flatten
    )
  }

  sealed trait UserRight

  object UserRight {

    case object Admin extends UserRight
    case class AdminOfAreas(areasAdministrated: Set[UUID]) extends UserRight
    case class Observer(organisations: Set[Organisation.Id]) extends UserRight
    case class HasUserId(id: UUID) extends UserRight

  }

  /** Attached to a User
    * `rights` is private to authorization,
    * this enforces that all possible checks are in this package
    */
  case class UserRights(
      private[authorization] rights: Set[UserRight]
  )

  // List of all possible authorization checks
  // TODO: Maybe rename `checks` or `authorizationChecks`
  object policies {
    type Check = UserRights => Boolean

    def isAdmin: Check =
      _.rights.contains(UserRight.Admin)

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
      rights => isAdmin(rights) || isAdminOfOneOfAreas(editedUser.areas.toSet)(rights)

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

  }

}
