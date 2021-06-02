package controllers

import java.time.LocalDate
import java.util.UUID

import actions.{LoginAction, RequestWithUserData}
import cats.syntax.all._
import constants.Constants
import controllers.Operators.{GroupOperators, UserOperators}
import helper.BooleanHelper.not
import helper.StringHelper.{capitalizeName, commonStringInputNormalization}
import helper.{Time, UUIDHelper}
import javax.inject.{Inject, Singleton}
import models.EventType.{
  AddUserError,
  AllUserCSVUnauthorized,
  AllUserCsvShowed,
  AllUserIncorrectSetup,
  AllUserUnauthorized,
  CGUShowed,
  CGUValidated,
  CGUValidationError,
  DeleteUserUnauthorized,
  EditMyGroupBadUserInput,
  EditMyGroupShowed,
  EditMyGroupShowedError,
  EditMyGroupUpdated,
  EditMyGroupUpdatedError,
  EditUserError,
  EditUserShowed,
  EventsShowed,
  EventsUnauthorized,
  NewsletterSubscribed,
  NewsletterSubscriptionError,
  PostAddUserUnauthorized,
  PostEditUserUnauthorized,
  ShowAddUserUnauthorized,
  UserDeleted,
  UserEdited,
  UserIsUsed,
  UserNotFound,
  UserProfileShowed,
  UserProfileShowedError,
  UserProfileUpdated,
  UserProfileUpdatedError,
  UserShowed,
  UsersCreated,
  UsersShowed,
  ViewUserUnauthorized
}
import models._
import models.formModels.{
  normalizedOptionalText,
  AddUserFormData,
  AddUserToGroupFormData,
  EditProfileFormData,
  EditUserFormData,
  ValidateSubscriptionForm
}
import org.postgresql.util.PSQLException
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.{Form, Mapping}
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.filters.csrf.CSRF
import play.filters.csrf.CSRF.Token
import serializers.{Keys, UserAndGroupCsvSerializer}
import services._

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
case class UserController @Inject() (
    loginAction: LoginAction,
    userService: UserService,
    groupService: UserGroupService,
    applicationService: ApplicationService,
    notificationsService: NotificationService,
    configuration: Configuration,
    eventService: EventService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with I18nSupport
    with UserOperators
    with GroupOperators {

  private def getGroupsUsersAndApplicationsBy(user: User) = for {
    groups <- groupService.byIdsFuture(user.groupIds)
    users <- userService.byGroupIdsFuture(groups.map(_.id), includeDisabled = true)
    applications <- applicationService.allForUserIds(users.map(_.id))
  } yield (groups, users, applications)

  private def myGroupsAction(
      groupId: UUID
  )(inner: RequestWithUserData[_] => Future[Result]): Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.canAddOrRemoveOtherUser(groupId))(
        () =>
          (
            EventType.EditMyGroupUnauthorized,
            s"L'utilisateur n'est pas autorisé à éditer le groupe $groupId"
          ), {
          val message = "Vous n’avez pas le droit de modifier ce groupe"
          Redirect(routes.UserController.showEditMyGroups).flashing("error" -> message).some
        }
      )(() => inner(request))
    }

  def addToGroup(groupId: UUID) =
    myGroupsAction(groupId) { implicit request =>
      val user = request.currentUser
      AddUserToGroupFormData.form
        .bindFromRequest()
        .fold(
          err => {
            val message = "L’adresse email n'est pas correcte"
            eventService.log(EditMyGroupBadUserInput, message)
            getGroupsUsersAndApplicationsBy(user).map { case (groups, users, applications) =>
              Ok(views.html.editMyGroups(user, request.rights)(err)(groups, users, applications))
            }
          },
          data =>
            userService
              .byEmailFuture(data.email)
              .zip(userService.byGroupIdsFuture(List(groupId), includeDisabled = true))
              .flatMap {
                case (None, _) =>
                  eventService.log(
                    EditMyGroupBadUserInput,
                    s"Tentative d'ajout de l'utilisateur inexistant ${data.email} au groupe $groupId"
                  )
                  successful(
                    Redirect(routes.UserController.showEditMyGroups)
                      .flashing("error" -> "L’utilisateur n’existe pas dans Administration+")
                  )
                case (Some(userToAdd), usersInGroup)
                    if usersInGroup.map(_.id).contains[UUID](userToAdd.id) =>
                  eventService.log(
                    EditMyGroupBadUserInput,
                    s"Tentative d'ajout de l'utilisateur ${userToAdd.id} déjà présent au groupe $groupId",
                    involvesUser = userToAdd.id.some
                  )
                  successful(
                    Redirect(routes.UserController.showEditMyGroups)
                      .flashing("error" -> "L’utilisateur est déjà présent dans le groupe")
                  )
                case (Some(userToAdd), _) =>
                  userService
                    .addToGroup(userToAdd.id, groupId)
                    .map { _ =>
                      eventService.log(
                        EditMyGroupUpdated,
                        s"Utilisateur ${userToAdd.id} ajouté au groupe $groupId",
                        involvesUser = userToAdd.id.some
                      )
                      Redirect(routes.UserController.showEditMyGroups)
                        .flashing("success" -> "L’utilisateur a été ajouté au groupe")
                    }
              }
        )
    }

  def enableUser(userId: UUID) =
    loginAction.async { implicit request =>
      withUser(
        userId,
        includeDisabled = true,
        errorMessage = s"L'utilisateur $userId n'existe pas et ne peut pas être réactivé",
        errorResult = Redirect(routes.UserController.showEditMyGroups)
          .flashing(
            "error" -> ("L’utilisateur n’existe pas dans Administration+. " +
              "S’il s’agit d’une erreur, vous pouvez contacter le support.")
          )
          .some
      ) { otherUser =>
        asUserWithAuthorization(Authorization.canEnableOtherUser(otherUser))(() =>
          (
            EventType.EditUserUnauthorized,
            s"L'utilisateur n'est pas autorisé à réactiver l'utilisateur $userId"
          )
        ) { () =>
          userService
            .enable(userId)
            .map(
              _.fold(
                error => {
                  eventService.logError(error)
                  Redirect(routes.UserController.showEditMyGroups)
                    .flashing("error" -> Constants.error500FlashMessage)
                },
                _ => {
                  eventService.log(
                    EventType.UserEdited,
                    s"Utilisateur $userId réactivé",
                    involvesUser = userId.some
                  )
                  Redirect(routes.UserController.showEditMyGroups)
                    .flashing("success" -> "L’utilisateur a bien été réactivé.")
                }
              )
            )
        }
      }
    }

  def removeFromGroup(userId: UUID, groupId: UUID) =
    myGroupsAction(groupId) { implicit request =>
      withUser(
        userId,
        includeDisabled = true,
        errorMessage = s"L'utilisateur $userId n'existe pas et ne peut pas être désactivé",
        errorResult = Redirect(routes.UserController.showEditMyGroups)
          .flashing(
            "error" -> ("L’utilisateur n’existe pas dans Administration+. " +
              "S’il s’agit d’une erreur, vous pouvez contacter le support.")
          )
          .some
      ) { otherUser =>
        val result: Future[Either[Error, Result]] =
          if (otherUser.groupIds.toSet.size <= 1) {
            userService
              .disable(userId)
              .map(_.map { _ =>
                eventService.log(
                  EventType.UserEdited,
                  s"Utilisateur $userId désactivé",
                  involvesUser = userId.some
                )
                Redirect(routes.UserController.showEditMyGroups)
                  .flashing("success" -> "L’utilisateur a bien été désactivé.")
              })
          } else {
            userService
              .removeFromGroup(userId, groupId)
              .map(_.map { _ =>
                eventService.log(
                  EditMyGroupUpdated,
                  s"Utilisateur $userId retiré du groupe $groupId",
                  involvesUser = userId.some
                )
                Redirect(routes.UserController.showEditMyGroups)
                  .flashing("success" -> "L’utilisateur a bien été retiré du groupe.")
              })
          }
        result.map(
          _.fold(
            error => {
              eventService.logError(error)
              Redirect(routes.UserController.showEditMyGroups)
                .flashing("error" -> Constants.error500FlashMessage)
            },
            identity
          )
        )
      }
    }

  def showEditMyGroups =
    loginAction.async { implicit request =>
      val user = request.currentUser
      val form = AddUserToGroupFormData.form
      getGroupsUsersAndApplicationsBy(user)
        .map { case (groups, users, applications) =>
          eventService.log(EditMyGroupShowed, "Visualise la modification de groupe")
          Ok(views.html.editMyGroups(user, request.rights)(form)(groups, users, applications))
        }
        .recover { case exception =>
          val message = s"Impossible de modifier le groupe : ${exception.getMessage}"
          eventService.log(EditMyGroupShowedError, message)
          InternalServerError(views.html.welcome(user, request.rights))
            .flashing("error" -> "Une erreur est survenue")
        }
    }

  def showEditProfile =
    loginAction.async { implicit request =>
      // Should be better if User could contains List[UserGroup] instead of List[UUID]
      val user = request.currentUser
      val profile = EditProfileFormData(
        user.firstName.orEmpty,
        user.lastName.orEmpty,
        user.qualite,
        user.phoneNumber
      )
      val form = EditProfileFormData.form.fill(profile)
      groupService
        .byIdsFuture(user.groupIds)
        .map { groups =>
          eventService.log(UserProfileShowed, "Visualise la modification de profil")
          Ok(views.html.editProfile(request.currentUser, request.rights)(form, user.email, groups))
        }
        .recoverWith { case exception =>
          val message =
            s"Impossible de visualiser la modification de profil : ${exception.getMessage}"
          eventService.log(UserProfileShowedError, message)
          successful(InternalServerError(views.html.welcome(user, request.rights)))
        }
    }

  def editProfile =
    loginAction.async { implicit request =>
      val user = request.currentUser
      if (user.sharedAccount) {
        eventService.log(
          UserProfileUpdatedError,
          s"Impossible de modifier un profil partagé (${user.id})"
        )
        successful(BadRequest(views.html.welcome(user, request.rights)))
      } else
        EditProfileFormData.form
          .bindFromRequest()
          .fold(
            errors => {
              eventService.log(
                UserProfileUpdatedError,
                s"Erreur lors de la modification du profil (${user.id})"
              )
              groupService
                .byIdsFuture(user.groupIds)
                .map(groups =>
                  BadRequest(
                    views.html.editProfile(user, request.rights)(errors, user.email, groups)
                  )
                )
            },
            success =>
              userService
                .editProfile(user.id)(
                  success.firstName,
                  success.lastName,
                  success.qualite,
                  success.phoneNumber.orEmpty
                )
                // Safe, in theory
                .map(_ => userService.byId(user.id).head)
                .map { editedUser =>
                  eventService
                    .log(UserProfileUpdated, s"Profil édité ${user.toDiffLogString(editedUser)}")
                  val message = "Votre profil a bien été modifié"
                  Redirect(routes.UserController.editProfile).flashing("success" -> message)
                }
                .recover { e =>
                  val errorMessage = s"Erreur lors de la modification du profil: ${e.getMessage}"
                  eventService.log(UserProfileUpdatedError, errorMessage)
                  InternalServerError(views.html.welcome(user, request.rights))
                }
          )
    }

  def home =
    loginAction {
      TemporaryRedirect(routes.UserController.all(Area.allArea.id).url)
    }

  def all(areaId: UUID): Action[AnyContent] =
    loginAction.async { implicit request: RequestWithUserData[AnyContent] =>
      asUserWhoSeesUsersOfArea(areaId) { () =>
        AllUserUnauthorized -> "Accès non autorisé à l'admin des utilisateurs"
      } { () =>
        val selectedArea = Area.fromId(areaId).get

        val allAreasGroupsFuture: Future[List[UserGroup]] =
          if (Authorization.isAdmin(request.rights)) {
            if (selectedArea.id === Area.allArea.id) {
              groupService.byAreas(request.currentUser.areas)
            } else {
              groupService.byArea(areaId)
            }
          } else if (Authorization.isObserver(request.rights)) {
            groupService.byOrganisationIds(request.currentUser.observableOrganisationIds)
          } else if (Authorization.isManager(request.rights)) {
            groupService.byIdsFuture(request.currentUser.groupIds)
          } else {
            eventService.log(AllUserIncorrectSetup, "Erreur d'accès aux groupes")
            Future(Nil)
          }
        val groupsFuture: Future[List[UserGroup]] =
          allAreasGroupsFuture.map(groups =>
            if (selectedArea.id === Area.allArea.id) {
              groups
            } else {
              groups.filter(_.areaIds.contains[UUID](selectedArea.id))
            }
          )
        val usersFuture: Future[List[User]] =
          if (Authorization.isAdmin(request.rights) && areaId === Area.allArea.id) {
            // Includes users without any group for debug purpose
            userService.all
          } else {
            groupsFuture.map(groups =>
              userService.byGroupIds(groups.map(_.id), includeDisabled = true)
            )
          }
        val applications = applicationService.allByArea(selectedArea.id, anonymous = true)

        eventService.log(UsersShowed, "Visualise la vue des utilisateurs")
        usersFuture.zip(groupsFuture).map { case (users, groups) =>
          val result = request.getQueryString(Keys.QueryParam.vue).getOrElse("nouvelle") match {
            case "nouvelle" if request.currentUser.admin =>
              views.html.allUsersNew(request.currentUser, request.rights)(
                groups,
                users,
                selectedArea
              )
            case _ =>
              views.html.allUsersByGroup(request.currentUser, request.rights)(
                groups,
                users,
                applications,
                selectedArea
              )
          }
          Ok(result)
        }
      }
    }

  def allCSV(areaId: java.util.UUID): Action[AnyContent] =
    loginAction.async { implicit request: RequestWithUserData[AnyContent] =>
      asAdminWhoSeesUsersOfArea(areaId) { () =>
        AllUserCSVUnauthorized -> "Accès non autorisé à l'export utilisateur"
      } { () =>
        val area = Area.fromId(areaId).get
        val usersFuture: Future[List[User]] = if (areaId === Area.allArea.id) {
          if (Authorization.isAdmin(request.rights)) {
            // Includes users without any group for debug purpose
            userService.all
          } else {
            groupService.byAreas(request.currentUser.areas).map { groupsOfArea =>
              userService.byGroupIds(groupsOfArea.map(_.id), includeDisabled = true)
            }
          }
        } else {
          groupService.byArea(areaId).map { groupsOfArea =>
            userService.byGroupIds(groupsOfArea.map(_.id), includeDisabled = true)
          }
        }
        val groupsFuture: Future[List[UserGroup]] =
          groupService.byAreas(request.currentUser.areas)
        eventService.log(AllUserCsvShowed, "Visualise le CSV de tous les zones de l'utilisateur")

        usersFuture.zip(groupsFuture).map { case (users, groups) =>
          def userToCSV(user: User): String = {
            val userGroups = user.groupIds.flatMap(id => groups.find(_.id === id))
            List[String](
              user.id.toString,
              user.firstName.orEmpty,
              user.lastName.orEmpty,
              user.email,
              Time.formatPatternFr(user.creationDate, "dd-MM-YYYY-HHhmm"),
              if (user.sharedAccount) "Compte Partagé" else " ",
              if (user.sharedAccount) user.name else " ",
              if (user.helper) "Aidant" else " ",
              if (user.instructor) "Instructeur" else " ",
              if (user.groupAdmin) "Responsable" else " ",
              if (user.expert) "Expert" else " ",
              if (user.admin) "Admin" else " ",
              if (user.disabled) "Désactivé" else " ",
              user.communeCode,
              user.areas.flatMap(Area.fromId).map(_.name).mkString(", "),
              userGroups.map(_.name).mkString(", "),
              userGroups
                .flatMap(_.organisation)
                .flatMap(Organisation.byId)
                .map(_.shortName)
                .mkString(", "),
              if (user.cguAcceptationDate.nonEmpty) "CGU Acceptées" else "",
              if (user.newsletterAcceptationDate.nonEmpty) "Newsletter Acceptée" else ""
            ).mkString(";")
          }

          val headers = List[String](
            "Id",
            UserAndGroupCsvSerializer.USER_FIRST_NAME.prefixes.head,
            UserAndGroupCsvSerializer.USER_LAST_NAME.prefixes.head,
            UserAndGroupCsvSerializer.USER_EMAIL.prefixes.head,
            "Création",
            UserAndGroupCsvSerializer.USER_ACCOUNT_IS_SHARED.prefixes.head,
            UserAndGroupCsvSerializer.SHARED_ACCOUNT_NAME.prefixes.head,
            "Aidant",
            UserAndGroupCsvSerializer.USER_INSTRUCTOR.prefixes.head,
            UserAndGroupCsvSerializer.USER_GROUP_MANAGER.prefixes.head,
            "Expert",
            "Admin",
            "Actif",
            "Commune INSEE",
            UserAndGroupCsvSerializer.GROUP_AREAS_IDS.prefixes.head,
            UserAndGroupCsvSerializer.GROUP_NAME.prefixes.head,
            UserAndGroupCsvSerializer.GROUP_ORGANISATION.prefixes.head,
            "CGU",
            "Newsletter"
          ).mkString(";")

          val csvContent = (List(headers) ++ users.map(userToCSV)).mkString("\n")
          val date = Time.formatPatternFr(Time.nowParis(), "dd-MMM-YYY-HH'h'mm")
          val filename = "aplus-" + date + "-users-" + area.name.replace(" ", "-") + ".csv"

          Ok(csvContent)
            .withHeaders("Content-Disposition" -> s"""attachment; filename="$filename"""")
            .as("text/csv")
        }
      }
    }

  def editUser(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request: RequestWithUserData[AnyContent] =>
      asUserWithAuthorization(Authorization.canSeeEditUserPage) { () =>
        ViewUserUnauthorized -> s"Accès non autorisé pour voir $userId"
      } { () =>
        userService.byId(userId, includeDisabled = true) match {
          case None =>
            eventService.log(UserNotFound, s"L'utilisateur $userId n'existe pas")
            Future(NotFound("Nous n'avons pas trouvé cet utilisateur"))
          case Some(user) if Authorization.canSeeOtherUser(user)(request.rights) =>
            val form = editUserForm.fill(
              EditUserFormData(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                name = user.name,
                qualite = user.qualite,
                email = user.email,
                helper = user.helper,
                instructor = user.instructor,
                areas = user.areas,
                groupAdmin = user.groupAdmin,
                disabled = user.disabled,
                groupIds = user.groupIds,
                phoneNumber = user.phoneNumber,
                observableOrganisationIds = user.observableOrganisationIds,
                sharedAccount = user.sharedAccount,
                internalSupportComment = user.internalSupportComment
              )
            )
            val groups = groupService.allGroups
            val unused = not(isAccountUsed(user))
            val Token(tokenName, tokenValue) = CSRF.getToken.get
            eventService
              .log(
                UserShowed,
                "Visualise la vue de modification l'utilisateur ",
                involvesUser = Some(user.id)
              )
            Future(
              Ok(
                views.html.editUser(request.currentUser, request.rights)(
                  form,
                  user,
                  groups,
                  unused,
                  tokenName = tokenName,
                  tokenValue = tokenValue
                )
              )
            )
          case _ =>
            eventService.log(
              ViewUserUnauthorized,
              s"Accès non autorisé pour voir $userId",
              involvesUser = userId.some
            )
            Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
        }
      }
    }

  def isAccountUsed(user: User): Boolean =
    applicationService.allForUserId(userId = user.id, anonymous = false).nonEmpty

  def deleteUnusedUserById(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withUser(userId, includeDisabled = true) { user: User =>
        asAdminOfUserZone(user) { () =>
          DeleteUserUnauthorized -> s"Suppression de l'utilisateur $userId refusée."
        } { () =>
          if (isAccountUsed(user)) {
            eventService.log(
              UserIsUsed,
              description = s"Le compte ${user.id} est utilisé.",
              involvesUser = user.id.some
            )
            Future(Unauthorized("User is not unused."))
          } else {
            userService.deleteById(userId)
            val flashMessage = s"Utilisateur $userId / ${user.email} supprimé"
            eventService.log(
              UserDeleted,
              s"Utilisateur ${user.toLogString} supprimé",
              involvesUser = Some(user.id)
            )
            Future(
              Redirect(routes.UserController.home).flashing("success" -> flashMessage)
            )
          }
        }
      }
    }

  def editUserPost(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() => PostEditUserUnauthorized -> s"Accès non autorisé à modifier $userId") { () =>
        editUserForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              formWithErrors.value.map(_.id) match {
                case None =>
                  eventService.log(UserNotFound, s"L'utilisateur $userId n'existe pas")
                  Future(NotFound("Nous n'avons pas trouvé cet utilisateur"))
                case Some(userId) =>
                  withUser(userId, includeDisabled = true) { user: User =>
                    val groups = groupService.allGroups
                    eventService.log(
                      AddUserError,
                      s"Essai de modification de l'utilisateur $userId avec des erreurs de validation",
                      involvesUser = user.id.some
                    )
                    Future(
                      BadRequest(
                        views.html
                          .editUser(request.currentUser, request.rights)(
                            formWithErrors,
                            user,
                            groups
                          )
                      )
                    )
                  }
              },
            updatedUserData =>
              withUser(updatedUserData.id, includeDisabled = true) { oldUser: User =>
                // Ensure that user include all areas of his group
                val groups = groupService.byIds(updatedUserData.groupIds)
                val areaIds = (updatedUserData.areas ++ groups.flatMap(_.areaIds)).distinct
                val rights = request.rights
                if (not(Authorization.canEditOtherUser(oldUser)(rights))) {
                  eventService
                    .log(
                      PostEditUserUnauthorized,
                      s"Accès non autorisé à modifier $userId",
                      involvesUser = oldUser.id.some
                    )
                  Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
                } else {
                  val userToUpdate = oldUser.copy(
                    firstName = updatedUserData.firstName,
                    lastName = updatedUserData.lastName,
                    name = updatedUserData.name,
                    qualite = updatedUserData.qualite,
                    email = updatedUserData.email,
                    helper = updatedUserData.helper,
                    instructor = updatedUserData.instructor,
                    // intersect is a safe gard (In case an Admin try to manage an authorized area)
                    areas = areaIds.intersect(request.currentUser.areas).distinct,
                    groupAdmin = updatedUserData.groupAdmin,
                    disabled = updatedUserData.disabled,
                    groupIds = updatedUserData.groupIds.distinct,
                    phoneNumber = updatedUserData.phoneNumber,
                    observableOrganisationIds = updatedUserData.observableOrganisationIds.distinct,
                    sharedAccount = updatedUserData.sharedAccount,
                    internalSupportComment = updatedUserData.internalSupportComment
                  )
                  userService.update(userToUpdate).map { updateHasBeenDone =>
                    if (updateHasBeenDone) {
                      eventService
                        .log(
                          UserEdited,
                          s"Utilisateur $userId modifié ${oldUser.toDiffLogString(userToUpdate)}",
                          involvesUser = Some(userToUpdate.id)
                        )
                      Redirect(routes.UserController.editUser(userId))
                        .flashing("success" -> "Utilisateur modifié")
                    } else {
                      val form = editUserForm
                        .fill(updatedUserData)
                        .withGlobalError(
                          s"Impossible de mettre à jour l'utilisateur $userId (Erreur interne)"
                        )
                      val groups = groupService.allGroups
                      eventService.log(
                        EditUserError,
                        s"Impossible de modifier l'utilisateur dans la BDD ${oldUser.toDiffLogString(userToUpdate)}",
                        involvesUser = Some(oldUser.id)
                      )
                      InternalServerError(
                        views.html
                          .editUser(request.currentUser, request.rights)(form, oldUser, groups)
                      )
                    }
                  }
                }
              }
          )
      }
    }

  def addPost(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { group: UserGroup =>
        asUserWithAuthorization(Authorization.canEditGroup(group))(
          () =>
            (
              PostAddUserUnauthorized,
              s"Tentative non autorisée d'ajout d'utilisateurs au groupe ${group.id}"
            ),
          Unauthorized("Vous ne pouvez pas ajouter des utilisateurs à ce groupe.").some
        ) { () =>
          addUsersForm
            .bindFromRequest()
            .fold(
              { formWithErrors =>
                eventService
                  .log(AddUserError, "Essai d'ajout d'utilisateurs avec des erreurs de validation")
                Future(
                  BadRequest(
                    views.html.addUsers(request.currentUser, request.rights)(
                      formWithErrors,
                      0,
                      routes.UserController.addPost(groupId)
                    )
                  )
                )
              },
              usersToAdd =>
                try {
                  val users: List[User] = usersToAdd.map(userToAdd =>
                    User(
                      id = UUID.randomUUID(),
                      key = "", // generated by UserService
                      firstName = userToAdd.firstName,
                      lastName = userToAdd.lastName,
                      name = userToAdd.name,
                      qualite = userToAdd.qualite,
                      email = userToAdd.email,
                      helper = true,
                      instructor = userToAdd.instructor,
                      admin = false,
                      areas = group.areaIds,
                      creationDate = Time.nowParis(),
                      communeCode = "0",
                      groupAdmin = userToAdd.groupAdmin,
                      disabled = false,
                      expert = false,
                      groupIds = groupId :: Nil,
                      cguAcceptationDate = None,
                      newsletterAcceptationDate = None,
                      phoneNumber = userToAdd.phoneNumber,
                      observableOrganisationIds = Nil,
                      sharedAccount = userToAdd.sharedAccount,
                      internalSupportComment = None
                    )
                  )
                  userService
                    .add(users)
                    .fold(
                      { error =>
                        val errorMessage =
                          s"Impossible d'ajouter les utilisateurs. $error"
                        eventService.log(AddUserError, errorMessage)
                        val form = addUsersForm
                          .fill(usersToAdd)
                          .withGlobalError(errorMessage)
                        Future(
                          InternalServerError(
                            views.html.addUsers(request.currentUser, request.rights)(
                              form,
                              usersToAdd.length,
                              routes.UserController.addPost(groupId)
                            )
                          )
                        )
                      },
                      { _ =>
                        users.foreach { user =>
                          notificationsService.newUser(user)
                          eventService.log(
                            EventType.UserCreated,
                            s"Utilisateur ajouté ${user.toLogString}",
                            involvesUser = Some(user.id)
                          )
                        }
                        eventService.log(UsersCreated, "Utilisateurs ajoutés")
                        Future(
                          Redirect(routes.GroupController.editGroup(groupId))
                            .flashing("success" -> "Utilisateurs ajoutés")
                        )
                      }
                    )
                } catch {
                  case ex: PSQLException =>
                    val EmailErrorPattern =
                      """[^()@]+@[^()@.]+\.[^()@]+""".r // This didn't work in that case : """ Detail: Key \(email\)=\(([^()]*)\) already exists."""".r  (don't know why, the regex is correct)
                    val errorMessage =
                      EmailErrorPattern.findFirstIn(ex.getServerErrorMessage.toString) match {
                        case Some(email) => s"Un utilisateur avec l'adresse $email existe déjà."
                        case _ =>
                          "Erreur d'insertion dans la base de donnée : contacter l'administrateur."
                      }
                    val form = addUsersForm
                      .fill(usersToAdd)
                      .withGlobalError(errorMessage)
                    eventService.log(
                      AddUserError,
                      s"Impossible d'ajouter des utilisateurs dans la BDD : ${ex.getServerErrorMessage}"
                    )
                    Future(
                      BadRequest(
                        views.html.addUsers(request.currentUser, request.rights)(
                          form,
                          usersToAdd.length,
                          routes.UserController.addPost(groupId)
                        )
                      )
                    )
                }
            )
        }
      }
    }

  def showValidateAccount: Action[AnyContent] =
    loginAction { implicit request =>
      eventService.log(CGUShowed, "CGU visualisées")
      val user = request.currentUser
      Ok(
        views.html.validateAccount(
          user,
          request.rights,
          ValidateSubscriptionForm
            .validate(request.currentUser)
            .fill(
              ValidateSubscriptionForm(
                redirect = Option.empty,
                cguChecked = user.cguAcceptationDate.isDefined,
                firstName = user.firstName,
                lastName = user.lastName,
                phoneNumber = user.phoneNumber,
                qualite = user.qualite.some
              )
            )
        )
      )
    }

  private def validateAndUpdateUser(user: User)(
      firstName: Option[String],
      lastName: Option[String],
      qualite: Option[String],
      phoneNumber: Option[String]
  ): Future[User] =
    userService
      .update(
        user.validateWith(
          firstName.map(commonStringInputNormalization).map(capitalizeName),
          lastName.map(commonStringInputNormalization).map(capitalizeName),
          qualite.map(commonStringInputNormalization),
          phoneNumber.map(commonStringInputNormalization)
        )
      )
      .map { _ =>
        userService.validateCGU(user.id)
        // Safe, in theory
        userService.byId(user.id).head
      }

  def validateAccount: Action[AnyContent] =
    loginAction.async { implicit request =>
      val user = request.currentUser
      ValidateSubscriptionForm
        .validate(user)
        .bindFromRequest()
        .fold(
          { formWithErrors =>
            eventService.log(CGUValidationError, "Erreur de formulaire dans la validation des CGU")
            Future(BadRequest(views.html.validateAccount(user, request.rights, formWithErrors)))
          },
          {
            case ValidateSubscriptionForm(
                  Some(redirect),
                  true,
                  firstName,
                  lastName,
                  qualite,
                  phoneNumber
                ) if redirect =!= routes.ApplicationController.myApplications.url =>
              validateAndUpdateUser(request.currentUser)(firstName, lastName, qualite, phoneNumber)
                .map { updatedUser =>
                  val logMessage =
                    s"CGU validées ${request.currentUser.toDiffLogString(updatedUser)}"
                  eventService.log(CGUValidated, logMessage)
                  Redirect(Call("GET", redirect))
                    .flashing("success" -> "Merci d’avoir accepté les CGU")
                }
            case ValidateSubscriptionForm(_, true, firstName, lastName, qualite, phoneNumber) =>
              validateAndUpdateUser(request.currentUser)(firstName, lastName, qualite, phoneNumber)
                .map { updatedUser =>
                  val logMessage =
                    s"CGU validées ${request.currentUser.toDiffLogString(updatedUser)}"
                  eventService.log(CGUValidated, logMessage)
                  Redirect(routes.HomeController.welcome)
                    .flashing("success" -> "Merci d’avoir accepté les CGU")
                }
            case ValidateSubscriptionForm(Some(redirect), false, _, _, _, _)
                if redirect =!= routes.ApplicationController.myApplications.url =>
              Future(Redirect(Call("GET", redirect)))
            case ValidateSubscriptionForm(_, false, _, _, _, _) =>
              Future(Redirect(routes.HomeController.welcome))
          }
        )
    }

  private val subscribeNewsletterForm: Form[Boolean] = Form(
    "newsletter" -> boolean
  )

  def subscribeNewsletter: Action[AnyContent] =
    loginAction { implicit request =>
      subscribeNewsletterForm
        .bindFromRequest()
        .fold(
          { formWithErrors =>
            eventService.log(
              NewsletterSubscriptionError,
              "Erreur de formulaire dans la souscription à la newletter"
            )
            BadRequest(
              s"Formulaire invalide, prévenez l'administrateur du service. ${formWithErrors.errors.mkString(", ")}"
            )
          },
          { newsletter =>
            if (newsletter) {
              userService.acceptNewsletter(request.currentUser.id)
            }
            eventService.log(NewsletterSubscribed, "Newletter subscribed")
            Redirect(routes.HomeController.welcome)
              .flashing("success" -> "Merci d’avoir terminé votre inscription")
          }
        )
    }

  def add(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { group: UserGroup =>
        asUserWithAuthorization(Authorization.canEditGroup(group))(
          () =>
            (
              ShowAddUserUnauthorized,
              s"Tentative non autorisée d'accès à l'ajout d'utilisateurs dans le groupe ${group.id}"
            ),
          Unauthorized("Vous ne pouvez pas ajouter des utilisateurs à ce groupe.").some
        ) { () =>
          val rows =
            request
              .getQueryString(Keys.QueryParam.rows)
              .flatMap(rows => Try(rows.toInt).toOption)
              .getOrElse(1)
          eventService.log(EditUserShowed, "Visualise la vue d'ajouts des utilisateurs")
          Future(
            Ok(
              views.html.addUsers(request.currentUser, request.rights)(
                addUsersForm,
                rows,
                routes.UserController.addPost(groupId)
              )
            )
          )
        }
      }
    }

  def allEvents: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() => EventsUnauthorized -> "Accès non autorisé pour voir les événements") { () =>
        val limit = request
          .getQueryString(Keys.QueryParam.limit)
          .flatMap(limit => Try(limit.toInt).toOption)
          .getOrElse(500)
        val date = request
          .getQueryString(Keys.QueryParam.date)
          .flatMap(date => Try(LocalDate.parse(date)).toOption)
        val userId =
          request.getQueryString(Keys.QueryParam.fromUserId).flatMap(UUIDHelper.fromString)
        eventService.all(limit, userId, date).map { events =>
          eventService.log(EventsShowed, s"Affiche les événements")
          Ok(views.html.allEvents(request.currentUser, request.rights)(events, limit))
        }
      }
    }

  val addUsersForm: Form[List[AddUserFormData]] =
    Form(
      single(
        "users" -> list(AddUserFormData.formMapping)
      )
    )

  val editUserForm: Form[EditUserFormData] =
    Form(
      mapping(
        "id" -> uuid,
        "firstName" -> optional(text.verifying(maxLength(100))),
        "lastName" -> optional(text.verifying(maxLength(100))),
        "name" -> optional(nonEmptyText.verifying(maxLength(100))).transform[String](
          {
            case Some(value) => value
            case None        => ""
          },
          {
            case ""   => Option.empty[String]
            case name => name.some
          }
        ),
        "qualite" -> text.verifying(maxLength(100)),
        "email" -> email.verifying(maxLength(200), nonEmpty),
        "helper" -> boolean,
        "instructor" -> boolean,
        "areas" -> list(uuid)
          .verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
        "groupAdmin" -> boolean,
        "disabled" -> boolean,
        "groupIds" -> default(list(uuid), Nil),
        "phoneNumber" -> optional(text),
        "observableOrganisationIds" -> list(of[Organisation.Id]),
        Keys.User.sharedAccount -> boolean,
        "internalSupportComment" -> normalizedOptionalText
      )(EditUserFormData.apply)(EditUserFormData.unapply)
    )

}
