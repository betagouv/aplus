package models

import helper.BooleanHelper.not
import java.util.UUID
import models.mandat.Mandat

object Authorization {

  def readUserRights(user: User): UserRights = {
    import UserRight._
    UserRights(
      Set[Option[UserRight]](
        Some(HasUserId(user.id)),
        if (user.helper && not(user.disabled)) Some(Helper) else None,
        if (user.expert && not(user.disabled))
          Some(ExpertOfAreas(user.areas.toSet))
        else None,
        if (user.admin && not(user.disabled))
          Some(AdminOfAreas(user.areas.toSet))
        else None,
        if (user.instructor && not(user.disabled))
          Some(InstructorOfGroups(user.groupIds.toSet))
        else None,
        if (user.groupAdmin && not(user.disabled))
          Some(ManagerOfGroups(user.groupIds.toSet))
        else None,
        if (user.observableOrganisationIds.nonEmpty && not(user.disabled))
          Some(ObserverOfOrganisations(user.observableOrganisationIds.toSet))
        else None
      ).flatten
    )
  }

  private[Authorization] sealed trait UserRight

  object UserRight {
    case class HasUserId(id: UUID) extends UserRight
    case object Helper extends UserRight
    case class ExpertOfAreas(expertOfAreas: Set[UUID]) extends UserRight
    case class InstructorOfGroups(groupsManaged: Set[UUID]) extends UserRight
    case class AdminOfAreas(administeredAreas: Set[UUID]) extends UserRight
    case class ManagerOfGroups(groupsManaged: Set[UUID]) extends UserRight
    case class ObserverOfOrganisations(organisations: Set[Organisation.Id]) extends UserRight
  }

  /** Attached to a User
    * `rights` is private to authorization,
    * this enforces that all possible checks are in this package
    */
  case class UserRights(
      private[Authorization] val rights: Set[UserRight]
  )

  //
  // List of all possible authorization checks
  //

  type Check = UserRights => Boolean

  def atLeastOneIsAuthorized(checks: Check*): Check =
    rights => checks.exists(_(rights))

  def allMustBeAuthorized(checks: Check*): Check =
    rights => checks.forall(_(rights))

  def isAdmin: Check =
    _.rights.exists {
      case UserRight.AdminOfAreas(_) => true
      case _                         => false
    }

  def isAdminOfArea(areaId: UUID): Check =
    _.rights.exists {
      case UserRight.AdminOfAreas(administeredAreas) if administeredAreas.contains(areaId) => true
      case _                                                                               => false
    }

  def isHelper: Check =
    _.rights.exists {
      case UserRight.Helper => true
      case _                => false
    }

  def isExpert: Check =
    _.rights.exists {
      case UserRight.ExpertOfAreas(_) => true
      case _                          => false
    }

  def isExpert(areaId: UUID): Check =
    _.rights.exists {
      case UserRight.ExpertOfAreas(areas) if areas.contains(areaId) => true
      case _                                                        => false
    }

  def isInstructor: Check =
    _.rights.exists {
      case UserRight.InstructorOfGroups(_) => true
      case _                               => false
    }

  def isManager: Check =
    _.rights.exists {
      case UserRight.ManagerOfGroups(_) => true
      case _                            => false
    }

  def isObserver: Check =
    _.rights.exists {
      case UserRight.ObserverOfOrganisations(organisations) => organisations.nonEmpty
      case _                                                => false
    }

  def isAdminOfOneOfAreas(areas: Set[UUID]): Check =
    rights => areas.exists(area => isAdminOfArea(area)(rights))

  def isAdminOrObserver: Check =
    atLeastOneIsAuthorized(
      isAdmin,
      isObserver
    )

  def canObserveOrganisation(organisationId: Organisation.Id): Check =
    _.rights.exists {
      case UserRight.ObserverOfOrganisations(organisations) =>
        organisations.contains(organisationId)
      case _ => false
    }

  // TODO: weird...
  def userCanBeEditedBy(editorUser: User): Check =
    _ => editorUser.admin && editorUser.areas.intersect(editorUser.areas).nonEmpty

  def canSeeOtherUser(otherUser: User): Check =
    atLeastOneIsAuthorized(
      isObserver,
      canEditOtherUser(otherUser)
    )

  def canEditOtherUser(editedUser: User): Check =
    rights => isAdminOfOneOfAreas(editedUser.areas.toSet)(rights)

  def canEditGroups: Check =
    atLeastOneIsAuthorized(isAdmin, isManager)

  def canSeeApplicationsAsAdmin: Check =
    atLeastOneIsAuthorized(isAdmin, isManager)

  def canSeeStats: Check =
    atLeastOneIsAuthorized(isAdmin, isManager, isObserver)

  def canSeeUsers: Check =
    atLeastOneIsAuthorized(isAdmin, isManager, isObserver)

  //
  // Authorizations concerning Applications
  //

  def isApplicationCreator(application: Application): Check =
    _.rights.exists {
      case UserRight.HasUserId(id) => (application.creatorUserId: UUID) == (id: UUID)
      case _                       => false
    }

  def isInvitedOn(application: Application): Check =
    _.rights.exists {
      case UserRight.HasUserId(id) => application.invitedUsers.isDefinedAt(id)
      case _                       => false
    }

  def canSeeApplication(application: Application): Check =
    rights => {
      val validCase1 = isApplicationCreator(application)(rights)
      val validCase2 = isAdmin(rights)
      val validCase3 =
        (isInstructor(rights) || isHelper(rights)) && not(isExpert(application.area)(rights)) &&
          isInvitedOn(application)(rights)
      val validCase4 =
        isExpert(application.area)(rights) && isInvitedOn(application)(rights) && not(
          application.closed
        )
      validCase1 || validCase2 || validCase3 || validCase4
    }

  def canSeePrivateDataOfApplication(application: Application): Check =
    rights => {
      val isCreatorOrIsInvited = isInvitedOn(application)(rights) || isApplicationCreator(
        application
      )(rights)
      val validCase1 = isCreatorOrIsInvited && !isAdmin(rights)
      // If user is expert, admin and invited to the application he can see the data
      val validCase2 =
        isCreatorOrIsInvited && isExpert(application.area)(rights) && isAdmin(rights) && not(
          application.closed
        )
      validCase1 || validCase2
    }

  def canSeePrivateDataOfMandat(mandat: Mandat): Check =
    _.rights.exists {
      case UserRight.HasUserId(id) => (mandat.userId: UUID) == (id: UUID)
      case _                       => false
    }

  def canSeeMandat(mandat: Mandat): Check =
    rights => {
      val validCase1 = canSeePrivateDataOfMandat(mandat)(rights)
      val validCase2 = isAdmin(rights)
      validCase1 || validCase2
    }

}
