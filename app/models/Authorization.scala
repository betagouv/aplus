package models

import java.util.UUID

import cats.syntax.all._
import helper.BooleanHelper.not
import models.mandat.Mandat

object Authorization {

  def readUserRights(user: User): UserRights = {
    import UserRight._
    UserRights(
      Set[Option[UserRight]](
        HasUserId(user.id).some,
        IsInGroups(user.groupIds.toSet).some,
        if (user.helper && not(user.disabled)) Helper.some else none,
        if (user.expert && not(user.disabled))
          ExpertOfAreas(user.areas.toSet).some
        else none,
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
    case class IsInGroups(groups: Set[UUID]) extends UserRight
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

  def forall[A](list: List[A], fn: A => Check): Check =
    rights => list.forall(fn(_)(rights))

  def atLeastOneIsAuthorized(checks: Check*): Check =
    rights => checks.exists(_(rights))

  def allMustBeAuthorized(checks: Check*): Check =
    forall[Check](checks.toList, identity)

  def isInGroup(groupId: UUID): Check =
    _.rights.exists {
      case UserRight.IsInGroups(groups) if groups.contains(groupId) => true
      case _                                                        => false
    }

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

  def isManagerOfGroup(groupId: UUID): Check =
    _.rights.exists {
      case UserRight.ManagerOfGroups(managedGroups) if managedGroups.contains(groupId) => true
      case _                                                                           => false
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

  def canSeeStats: Check =
    atLeastOneIsAuthorized(isAdmin, isManager, isObserver)

  //
  // Authorizations concerning User/UserGroup
  //

  // TODO: weird...
  def userCanBeEditedBy(editorUser: User): Check =
    _ => editorUser.admin && editorUser.areas.intersect(editorUser.areas).nonEmpty

  def canSeeOtherUser(otherUser: User): Check =
    atLeastOneIsAuthorized(
      isObserver,
      canEditOtherUser(otherUser)
    )

  def canEditOtherUser(editedUser: User): Check =
    isAdminOfOneOfAreas(editedUser.areas.toSet)

  def canAddOrRemoveOtherUser(otherUserGroupId: UUID): Check =
    atLeastOneIsAuthorized(isAdmin, isInGroup(otherUserGroupId))

  def canEnableOtherUser(otherUser: User): Check =
    atLeastOneIsAuthorized(isAdmin, atLeastOneIsAuthorized(otherUser.groupIds.map(isInGroup): _*))

  def canEditGroup(group: UserGroup): Check =
    atLeastOneIsAuthorized(
      forall(group.areaIds, isAdminOfArea),
      isManagerOfGroup(group.id)
    )

  /** For organisation & areas. */
  def canEditGroupAnyField(group: UserGroup): Check =
    forall(group.areaIds, isAdminOfArea)

  def canSeeUsers: Check =
    atLeastOneIsAuthorized(isAdmin, isManager, isObserver)

  def canSeeEditUserPage: Check = isAdminOrObserver

  def canSeeSignupsPage: Check = isAdmin

  def canCreateSignups: Check = isAdmin

  def canEditSupportMessages: Check = isAdmin

  //
  // Authorizations concerning Applications
  //

  def canSeeApplicationsAsAdmin: Check =
    atLeastOneIsAuthorized(isAdmin, isManager)

  def isApplicationCreator(application: Application): Check =
    _.rights.exists {
      case UserRight.HasUserId(id) => application.creatorUserId === id
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
      case UserRight.HasUserId(id) => mandat.userId === id
      case _                       => false
    }

  def canSeeMandat(mandat: Mandat): Check =
    rights => {
      val validCase1 = canSeePrivateDataOfMandat(mandat)(rights)
      val validCase2 = isAdmin(rights)
      validCase1 || validCase2
    }

  def answerFileCanBeShowed(filesExpirationInDays: Int)(
      application: Application,
      answerId: UUID
  )(userId: UUID, rights: UserRights): Boolean =
    application.answers.find(_.id === answerId) match {
      case None => false
      case Some(answer) =>
        val hasNotExpired =
          Answer.filesAvailabilityLeftInDays(filesExpirationInDays)(answer).nonEmpty
        val validCase1 =
          hasNotExpired &&
            isHelper(rights) &&
            answer.visibleByHelpers &&
            userId === application.creatorUserId
        val invitedUsersInAnswers: Set[UUID] =
          (application.answers.takeWhile(_.id =!= answerId) :+ answer)
            .flatMap(_.invitedUsers.keys)
            .toSet
        val validCase2 =
          hasNotExpired &&
            isInstructor(rights) &&
            (application.invitedUsers.keys.toSet ++ invitedUsersInAnswers).contains(userId)

        validCase1 || validCase2
    }

  def applicationFileCanBeShowed(filesExpirationInDays: Int)(
      application: Application
  )(userId: UUID, rights: UserRights): Boolean =
    Application.filesAvailabilityLeftInDays(filesExpirationInDays)(application).nonEmpty && not(
      isExpert(rights)
    ) && (
      (isInstructor(rights) && application.invitedUsers.keys.toList.contains(userId)) ||
        (isHelper(rights) && userId === application.creatorUserId)
    )

}
