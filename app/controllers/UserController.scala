package controllers

import java.time.LocalDate
import java.util.UUID

import actions.{LoginAction, RequestWithUserData}
import cats.data.EitherT
import cats.syntax.all._
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
  EditProfileFormData,
  EditUserFormData,
  ValidateSubscriptionForm
}
import modules.AppConfig
import org.postgresql.util.PSQLException
import org.webjars.play.WebJarsUtil
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.{Form, Mapping}
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc._
import play.filters.csrf.CSRF
import play.filters.csrf.CSRF.Token
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import serializers.{Keys, UserAndGroupCsvSerializer}
import serializers.ApiModel.{SearchResult, UserGroupInfos, UserInfos}
import services._

@Singleton
case class UserController @Inject() (
    config: AppConfig,
    loginAction: LoginAction,
    userService: UserService,
    groupService: UserGroupService,
    applicationService: ApplicationService,
    notificationsService: NotificationService,
    eventService: EventService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with I18nSupport
    with Operators.Common
    with UserOperators
    with GroupOperators {

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
          eventService.log(
            UserProfileShowedError,
            "Impossible de visualiser la modification de profil",
            s"Message ${exception.getMessage}".some
          )
          Future.successful(InternalServerError(views.html.welcome(user, request.rights)))
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
        Future.successful(BadRequest(views.html.welcome(user, request.rights)))
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
                    .log(
                      UserProfileUpdated,
                      s"Profil ${user.id} édité",
                      s"Utilisateur ${user.toDiffLogString(editedUser)}".some
                    )
                  val message = "Votre profil a bien été modifié"
                  Redirect(routes.UserController.editProfile).flashing("success" -> message)
                }
                .recover { e =>
                  eventService.log(
                    UserProfileUpdatedError,
                    "Erreur lors de la modification du profil",
                    s"Message ${e.getMessage}".some
                  )
                  InternalServerError(views.html.welcome(user, request.rights))
                }
          )
    }

  def home =
    loginAction {
      TemporaryRedirect(routes.UserController.all(Area.allArea.id).url)
    }

  private def groupsInAllAreas(implicit request: RequestWithUserData[_]): Future[List[UserGroup]] =
    if (Authorization.isAdmin(request.rights)) {
      groupService.byAreas(request.currentUser.areas)
    } else if (Authorization.isObserver(request.rights)) {
      groupService.byOrganisationIds(request.currentUser.observableOrganisationIds)
    } else {
      groupService.byIdsFuture(request.currentUser.groupIds)
    }

  private def usersInAllAreas(implicit request: RequestWithUserData[_]) = {
    val groupsFuture = groupsInAllAreas
    val usersFuture: Future[List[User]] =
      if (Authorization.isAdmin(request.rights)) {
        // Includes users without any group for debug purpose
        userService.all
      } else {
        groupsFuture.map(groups =>
          userService.byGroupIds(groups.map(_.id).toSet.toList, includeDisabled = true)
        )
      }
    usersFuture.zip(groupsFuture)
  }

  private def groupsInArea(selectedArea: Area)(implicit request: RequestWithUserData[_]) = {
    val allAreasGroupsFuture: Future[List[UserGroup]] =
      if (Authorization.isAdmin(request.rights)) {
        groupService.byArea(selectedArea.id)
      } else if (Authorization.isObserver(request.rights)) {
        groupService.byOrganisationIds(request.currentUser.observableOrganisationIds)
      } else {
        groupService.byIdsFuture(request.currentUser.groupIds)
      }
    allAreasGroupsFuture.map(_.filter(_.areaIds.contains[UUID](selectedArea.id)))
  }

  private def usersInArea(selectedArea: Area)(implicit request: RequestWithUserData[_]) = {
    val groupsFuture = groupsInArea(selectedArea)
    val usersFuture: Future[List[User]] =
      groupsFuture.map(groups =>
        userService.byGroupIds(groups.map(_.id).toSet.toList, includeDisabled = true)
      )
    usersFuture.zip(groupsFuture)
  }

  private def usersAndGroups(selectedArea: Area)(implicit request: RequestWithUserData[_]) =
    if (selectedArea.id === Area.allArea.id)
      usersInAllAreas
    else
      usersInArea(selectedArea)

  def all(areaId: UUID): Action[AnyContent] =
    loginAction.async { implicit request: RequestWithUserData[AnyContent] =>
      asUserWhoSeesUsersOfArea(areaId)(
        AllUserUnauthorized,
        "Accès non autorisé à l'admin des utilisateurs"
      ) { () =>
        val selectedArea = Area.fromId(areaId).get
        usersAndGroups(selectedArea).map { case (users, groups) =>
          val applications = applicationService.allByArea(selectedArea.id, anonymous = true)
          eventService.log(UsersShowed, "Visualise la vue des utilisateurs")
          val result = request.getQueryString(Keys.QueryParam.vue).getOrElse("nouvelle") match {
            case "nouvelle" if request.currentUser.admin =>
              views.users.page(request.currentUser, request.rights, selectedArea)
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

  def allCSV(areaId: UUID): Action[AnyContent] =
    loginAction.async { implicit request: RequestWithUserData[AnyContent] =>
      asAdminWhoSeesUsersOfArea(areaId)(
        AllUserCSVUnauthorized,
        "Accès non autorisé à l'export utilisateur"
      ) { () =>
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
              user.helperRoleName.getOrElse(""),
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

  def search: Action[AnyContent] =
    loginAction.async { implicit request =>
      def toUserInfos(usersAndGroups: (List[User], List[UserGroup])): List[UserInfos] = {
        val (users, groups) = usersAndGroups
        val idToGroup = groups.map(group => (group.id, group)).toMap
        users.map(user => UserInfos.fromUser(user, idToGroup))
      }
      val area = request
        .getQueryString(Keys.QueryParam.searchAreaId)
        .flatMap(UUIDHelper.fromString)
        .flatMap(Area.fromId)
        .filter(_.id =!= Area.allArea.id)
      val limit = 1000
      val groupsOnly: Boolean =
        request
          .getQueryString(Keys.QueryParam.searchGroupsOnly)
          .map(_.trim)
          .filter(_.nonEmpty) === Some("true")
      val searchQuery =
        request.getQueryString(Keys.QueryParam.searchQuery).map(_.trim).filter(_.nonEmpty)
      val (
        usersT: EitherT[Future, Error, List[UserInfos]],
        groupsT: EitherT[Future, Error, List[UserGroupInfos]]
      ) =
        searchQuery match {
          case None =>
            if (groupsOnly) {
              val groups = area
                .fold(groupsInAllAreas)(area => groupsInArea(area))
                .map(_.map(UserGroupInfos.fromUserGroup).asRight[Error])
              eventService.log(EventType.SearchUsersDone, s"Liste les groupes")
              (EitherT.rightT[Future, Error](List.empty[UserInfos]), EitherT(groups))
            } else {
              val usersWithGroups = area.fold(usersInAllAreas)(area => usersInArea(area))
              val users = usersWithGroups.map(toUserInfos).map(_.asRight[Error])
              val groups = usersWithGroups.map { case (_, groups) =>
                groups.map(UserGroupInfos.fromUserGroup).asRight[Error]
              }
              eventService.log(EventType.SearchUsersDone, s"Liste les utilisateurs")
              (EitherT(users), EitherT(groups))
            }
          case Some(queryString) =>
            val groups = EitherT(groupService.search(queryString, limit)).map(groups =>
              area
                .fold(groups)(area => groups.filter(_.areaIds.contains[UUID](area.id)))
                .map(UserGroupInfos.fromUserGroup)
            )
            val users =
              if (groupsOnly)
                EitherT.rightT[Future, Error](List.empty[UserInfos])
              else
                EitherT(
                  userService
                    .search(queryString, limit)
                    .flatMap(
                      _.fold(
                        e => Future(e.asLeft),
                        users => {
                          val groupIds = users.flatMap(_.groupIds).toSet
                          groupService
                            .byIdsFuture(groupIds.toList)
                            .map(groups =>
                              area.fold((users, groups)) { area =>
                                val filteredGroups =
                                  groups.filter(_.areaIds.contains[UUID](area.id))
                                val groupIds = filteredGroups.map(_.id).toSet
                                val filteredUsers =
                                  users.filter(_.groupIds.toSet.intersect(groupIds).nonEmpty)
                                (filteredUsers, filteredGroups)
                              }
                            )
                            .map(toUserInfos)
                            .map(_.asRight)
                        }
                      )
                    )
                )
            eventService.log(
              EventType.SearchUsersDone,
              "Recherche des " +
                (if (groupsOnly) "groupes" else "utilisateurs") +
                s" [limite $limit]",
              s"Recherche '$queryString'".some
            )
            (users, groups)
        }
      usersT
        .flatMap(users =>
          groupsT.map { groups =>
            val data = SearchResult(users, groups)
            Ok(Json.toJson(data))
          }
        )
        .valueOr { error =>
          eventService.logError(error)
          InternalServerError(Json.toJson(SearchResult(Nil, Nil)))
        }
    }

  def editUser(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request: RequestWithUserData[AnyContent] =>
      asUserWithAuthorization(Authorization.canSeeEditUserPage)(
        ViewUserUnauthorized,
        s"Accès non autorisé pour voir $userId"
      ) { () =>
        withUser(userId, includeDisabled = true) { otherUser: User =>
          asUserWithAuthorization(Authorization.canSeeOtherUser(otherUser))(
            ViewUserUnauthorized,
            s"Accès non autorisé pour voir $userId",
            errorInvolvesUser = otherUser.id.some
          ) { () =>
            val form = EditUserFormData.form.fill(EditUserFormData.fromUser(otherUser))
            val groups = groupService.allOrThrow
            val unused = not(isAccountUsed(otherUser))
            val Token(tokenName, tokenValue) = CSRF.getToken.get
            eventService
              .log(
                UserShowed,
                "Visualise la vue de modification l'utilisateur",
                involvesUser = Some(otherUser.id)
              )
            Future(
              Ok(
                views.html.editUser(request.currentUser, request.rights)(
                  form,
                  otherUser,
                  groups,
                  unused,
                  tokenName = tokenName,
                  tokenValue = tokenValue
                )
              )
            )
          }
        }
      }
    }

  def isAccountUsed(user: User): Boolean =
    applicationService.allForUserId(userId = user.id, anonymous = false).nonEmpty

  def deleteUnusedUserById(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withUser(userId, includeDisabled = true) { user: User =>
        asAdminOfUserZone(user)(
          DeleteUserUnauthorized,
          s"Suppression de l'utilisateur $userId refusée"
        ) { () =>
          if (isAccountUsed(user)) {
            eventService.log(
              UserIsUsed,
              s"Le compte ${user.id} est utilisé",
              involvesUser = user.id.some
            )
            Future(Unauthorized("User is not unused."))
          } else {
            userService.deleteById(userId)
            val flashMessage = s"Utilisateur $userId / ${user.email} supprimé"
            eventService.log(
              UserDeleted,
              s"Utilisateur ${user.id} supprimé",
              s"Utilisateur ${user.toLogString}".some,
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
      asAdmin(PostEditUserUnauthorized, s"Accès non autorisé à modifier $userId") { () =>
        EditUserFormData.form
          .bindFromRequest()
          .fold(
            formWithErrors =>
              formWithErrors.value.map(_.id) match {
                case None =>
                  eventService.log(UserNotFound, s"L'utilisateur $userId n'existe pas")
                  Future(NotFound("Nous n'avons pas trouvé cet utilisateur"))
                case Some(userId) =>
                  withUser(userId, includeDisabled = true) { user: User =>
                    val groups = groupService.allOrThrow
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
                          s"Utilisateur $userId modifié",
                          s"Utilisateur ${oldUser.toDiffLogString(userToUpdate)}".some,
                          involvesUser = Some(userToUpdate.id)
                        )
                      Redirect(routes.UserController.editUser(userId))
                        .flashing("success" -> "Utilisateur modifié")
                    } else {
                      val form = EditUserFormData.form
                        .fill(updatedUserData)
                        .withGlobalError(
                          s"Impossible de mettre à jour l'utilisateur $userId (Erreur interne)"
                        )
                      val groups = groupService.allOrThrow
                      eventService.log(
                        EditUserError,
                        s"Impossible de modifier l'utilisateur $userId dans la BDD",
                        s"Utilisateur ${oldUser.toDiffLogString(userToUpdate)}".some,
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
          PostAddUserUnauthorized,
          s"Tentative non autorisée d'ajout d'utilisateurs au groupe ${group.id}",
          Unauthorized("Vous ne pouvez pas ajouter des utilisateurs à ce groupe.").some
        ) { () =>
          AddUserFormData.addUsersForm
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
                        eventService.log(
                          AddUserError,
                          "Impossible d'ajouter les utilisateurs",
                          s"Erreur '$error'".some
                        )
                        val form = AddUserFormData.addUsersForm
                          .fill(usersToAdd)
                          .withGlobalError(s"Impossible d'ajouter les utilisateurs. $error")
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
                          val host = notificationsService.newUser(user)
                          eventService.log(
                            EventType.UserCreated,
                            s"Utilisateur ${user.id} ajouté [email envoyé via '$host']",
                            s"Utilisateur ${user.toLogString}".some,
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
                    val form = AddUserFormData.addUsersForm
                      .fill(usersToAdd)
                      .withGlobalError(errorMessage)
                    eventService.log(
                      AddUserError,
                      "Impossible d'ajouter des utilisateurs dans la BDD",
                      s"Exception ${ex.getServerErrorMessage}".some
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

      def validateRedirect(uncheckedRedirect: String): String =
        if (PathValidator.isValidPath(uncheckedRedirect)) uncheckedRedirect
        else {
          eventService.log(
            EventType.CGUInvalidRedirect,
            "URL de redirection après les CGU invalide",
            s"URL '$uncheckedRedirect'".some
          )
          routes.HomeController.index.url
        }

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
                  Some(uncheckedRedirect),
                  true,
                  firstName,
                  lastName,
                  qualite,
                  phoneNumber
                ) if uncheckedRedirect =!= routes.ApplicationController.myApplications.url =>
              val redirect = validateRedirect(uncheckedRedirect)
              validateAndUpdateUser(request.currentUser)(firstName, lastName, qualite, phoneNumber)
                .map { updatedUser =>
                  eventService.log(
                    CGUValidated,
                    s"CGU validées par l'utilisateur ${request.currentUser.id}",
                    s"Utilisateur ${request.currentUser.toDiffLogString(updatedUser)}".some
                  )
                  Redirect(Call("GET", redirect))
                    .flashing("success" -> "Merci d’avoir accepté les CGU")
                }
            case ValidateSubscriptionForm(_, true, firstName, lastName, qualite, phoneNumber) =>
              validateAndUpdateUser(request.currentUser)(firstName, lastName, qualite, phoneNumber)
                .map { updatedUser =>
                  eventService.log(
                    CGUValidated,
                    s"CGU validées par l'utilisateur ${request.currentUser.id}",
                    s"Utilisateur ${request.currentUser.toDiffLogString(updatedUser)}".some
                  )
                  Redirect(routes.HomeController.welcome)
                    .flashing("success" -> "Merci d’avoir accepté les CGU")
                }
            case ValidateSubscriptionForm(Some(uncheckedRedirect), false, _, _, _, _)
                if uncheckedRedirect =!= routes.ApplicationController.myApplications.url =>
              val redirect = validateRedirect(uncheckedRedirect)
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
          ShowAddUserUnauthorized,
          s"Tentative non autorisée d'accès à l'ajout d'utilisateurs dans le groupe ${group.id}",
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
                AddUserFormData.addUsersForm,
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
      asAdmin(EventsUnauthorized, "Accès non autorisé pour voir les événements") { () =>
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

}
