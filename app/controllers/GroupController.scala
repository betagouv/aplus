package controllers

import actions.{LoginAction, RequestWithUserData}
import cats.effect.IO
import cats.syntax.all._
import constants.Constants
import controllers.Operators._
import helper.BooleanHelper.not
import helper.PlayFormHelpers.formErrorsLog
import helper.Time
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.{Area, Authorization, Error, EventType, User, UserGroup}
import models.EventType.{
  AddGroupUnauthorized,
  AddUserGroupError,
  EditGroupShowed,
  EditGroupUnauthorized,
  EditUserGroupError,
  GroupDeletionUnauthorized,
  UserGroupCreated,
  UserGroupDeleted,
  UserGroupDeletionUnauthorized,
  UserGroupEdited
}
import models.forms.{AddGroupFormData, AddUserToGroupFormData}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{
  Action,
  AnyContent,
  BaseController,
  Call,
  ControllerComponents,
  RequestHeader,
  Result
}
import play.libs.ws.WSClient
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys
import services._
import views.editMyGroups.UserInfos

@Singleton
case class GroupController @Inject() (
    applicationService: ApplicationService,
    config: AppConfig,
    val controllerComponents: ControllerComponents,
    dependencies: ServicesDependencies,
    eventService: EventService,
    groupService: UserGroupService,
    loginAction: LoginAction,
    userService: UserService,
    ws: WSClient,
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends BaseController
    with I18nSupport
    with Operators.Common
    with GroupOperators
    with UserOperators {

  import dependencies.ioRuntime

  private val groupPageRedirectValue: String = "groupPage"

  private def groupModificationOriginPage(implicit request: RequestHeader): (Boolean, Call) =
    (
      request.getQueryString(Keys.QueryParam.redirect),
      request.getQueryString(Keys.QueryParam.groupId)
    ) match {
      case (Some(`groupPageRedirectValue`), Some(groupId)) =>
        (true, routes.GroupController.editGroup(UUID.fromString(groupId)))
      case _ =>
        (false, routes.GroupController.showEditMyGroups)
    }

  def addToGroup(groupId: UUID): Action[AnyContent] =
    addOrRemoveUserAction(groupId) { implicit request => group =>
      val (originIsGroupPage, redirectPage) = groupModificationOriginPage
      AddUserToGroupFormData.form
        .bindFromRequest()
        .fold(
          err => {
            val message = "L’adresse email n'est pas correcte"
            eventService.log(EventType.AddUserToGroupBadUserInput, message)
            if (originIsGroupPage) withGroup(groupId)(group => editGroupPage(group, err))
            else editMyGroupsPage(request.currentUser, request.rights, err)
          },
          data =>
            userService
              .byEmailFuture(data.email, includeDisabled = true)
              .zip(userService.byGroupIdsFuture(List(groupId), includeDisabled = true))
              .flatMap {
                case (None, _) =>
                  eventService.log(
                    EventType.AddUserToGroupBadUserInput,
                    s"Tentative d'ajout d'un utilisateur inexistant au groupe $groupId",
                    s"Email '${data.email}'".some
                  )
                  Future.successful(
                    Redirect(redirectPage)
                      .flashing(
                        "error" -> ("Le compte n’existe pas dans Administration+. " +
                          "Celui-ci peut être créé par un responsable identifiable dans la liste ci-dessous.")
                      )
                  )
                case (Some(userToAdd), usersInGroup)
                    if usersInGroup.map(_.id).contains[UUID](userToAdd.id) =>
                  eventService.log(
                    EventType.AddUserToGroupBadUserInput,
                    s"Tentative d'ajout de l'utilisateur ${userToAdd.id} déjà présent au groupe $groupId",
                    involvesUser = userToAdd.id.some
                  )
                  Future.successful(
                    Redirect(redirectPage)
                      .flashing("error" -> "L’utilisateur est déjà présent dans le groupe")
                  )
                case (Some(userToAdd), _) =>
                  userService
                    .addToGroup(userToAdd.id, groupId)
                    .map { _ =>
                      eventService.log(
                        EventType.UserGroupEdited,
                        s"Utilisateur ${userToAdd.id} ajouté au groupe $groupId",
                        involvesUser = userToAdd.id.some
                      )
                      Redirect(redirectPage)
                        .flashing("success" -> "L’utilisateur a été ajouté au groupe")
                    }
              }
        )
    }

  def enableUser(userId: UUID, groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      val (_, redirectPage) = groupModificationOriginPage
      withUser(
        userId,
        includeDisabled = true,
        errorMessage = s"L'utilisateur $userId n'existe pas et ne peut pas être réactivé".some,
        errorResult = Redirect(redirectPage)
          .flashing(
            "error" -> ("L’utilisateur n’existe pas dans Administration+. " +
              "S’il s’agit d’une erreur, vous pouvez contacter le support.")
          )
          .some
      ) { otherUser =>
        groupService.byIdsFuture(otherUser.groupIds).flatMap { otherUserGroups =>
          asUserWithAuthorization(Authorization.canEnableOtherUser(otherUser, otherUserGroups))(
            EventType.EditUserUnauthorized,
            s"L'utilisateur n'est pas autorisé à réactiver l'utilisateur $userId"
          ) { () =>
            userService
              .enable(userId, groupId)
              .map(
                _.fold(
                  error => {
                    eventService.logError(error)
                    Redirect(redirectPage).flashing("error" -> Constants.error500FlashMessage)
                  },
                  _ => {
                    eventService.log(
                      EventType.UserEdited,
                      s"Utilisateur $userId réactivé (groupe $groupId)",
                      involvesUser = userId.some
                    )
                    Redirect(redirectPage)
                      .flashing("success" -> "L’utilisateur a bien été réactivé.")
                  }
                )
              )
          }
        }
      }
    }

  def removeFromGroup(userId: UUID, groupId: UUID): Action[AnyContent] =
    addOrRemoveUserAction(groupId) { implicit request => group =>
      val (_, redirectPage) = groupModificationOriginPage
      withUser(
        userId,
        includeDisabled = true,
        errorMessage = s"L'utilisateur $userId n'existe pas et ne peut pas être désactivé".some,
        errorResult = Redirect(redirectPage)
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
                Redirect(redirectPage).flashing("success" -> "L’utilisateur a bien été désactivé.")
              })
          } else {
            userService
              .removeFromGroup(userId, groupId)
              .map(_.map { _ =>
                eventService.log(
                  EventType.UserGroupEdited,
                  s"Utilisateur $userId retiré du groupe $groupId",
                  involvesUser = userId.some
                )
                Redirect(redirectPage)
                  .flashing("success" -> "L’utilisateur a bien été retiré du groupe.")
              })
          }
        result.map(
          _.fold(
            error => {
              eventService.logError(error)
              Redirect(redirectPage).flashing("error" -> Constants.error500FlashMessage)
            },
            identity
          )
        )
      }
    }

  def removeAllUsersFromGroup(groupId: UUID): Action[AnyContent] = loginAction.async {
    implicit request =>
      userService.byGroupIdsFuture(List(groupId), includeDisabled = true).flatMap { users =>
        users
          .map { user =>
            val deactivation: Future[Either[Error, Unit]] =
              if user.groupIds.toSet.size <= 1 then
                userService
                  .disable(user.id)
                  .map(_.map { _ =>
                    eventService.log(
                      EventType.UserEdited,
                      s"Utilisateur ${user.id} désactivé",
                      involvesUser = user.id.some
                    )
                  })
              else Future.successful(Right(()))
            val result: Future[Either[Error, Unit]] = deactivation.flatMap {
              case Left(error) =>
                Future.successful(Left(error))
              case Right(_) =>
                userService
                  .removeFromGroup(user.id, groupId)
                  .map(_.map { _ =>
                    eventService.log(
                      EventType.UserGroupEdited,
                      s"Utilisateur ${user.id} retiré du groupe $groupId",
                      involvesUser = user.id.some
                    )
                  })
            }
            result
          }
          .sequence
          .map { results =>
            val errors = results.collect { case Left(error) => error }
            for error <- errors do eventService.logError(error)
            if errors.isEmpty then
              eventService.log(
                UserGroupEdited,
                s"Tous les utilisateurs ont été retirés du groupe $groupId",
                s"Groupe ${groupId}".some
              )
              Redirect(routes.GroupController.editGroup(groupId))
            else
              Redirect(routes.GroupController.editGroup(groupId))
                .flashing(
                  "error" -> "Une erreur est survenue lors de la suppression des utilisateurs"
                )
          }

      }
  }

  def showEditMyGroups: Action[AnyContent] =
    loginAction.async { implicit request =>
      editMyGroupsPage(request.currentUser, request.rights, AddUserToGroupFormData.form)
    }

  def showEditMyGroupsAs(otherUserId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withUser(otherUserId) { (otherUser: User) =>
        asUserWithAuthorization(Authorization.canSeeOtherUserNonPrivateViews(otherUser))(
          EventType.MasqueradeUnauthorized,
          s"Accès non autorisé pour voir la page mes groupes de $otherUserId",
          errorInvolvesUser = Some(otherUser.id)
        ) { () =>
          LoginAction.readUserRights(otherUser).flatMap { userRights =>
            editMyGroupsPage(otherUser, userRights, AddUserToGroupFormData.form)
          }
        }
      }
    }

  def deleteUnusedGroupById(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { (group: UserGroup) =>
        asAdminOfGroupZone(group)(
          GroupDeletionUnauthorized,
          s"Droits insuffisants pour la suppression du groupe $groupId"
        ) { () =>
          val empty = groupService.isGroupEmpty(group.id)
          if (not(empty)) {
            eventService.log(
              UserGroupDeletionUnauthorized,
              s"Tentative de suppression d'un groupe utilisé ($groupId)"
            )
            Future(Unauthorized("Le groupe est utilisé."))
          } else {
            val _ = groupService.deleteById(groupId)
            eventService.log(
              UserGroupDeleted,
              "Groupe supprimé",
              s"Groupe ${group.toLogString}".some
            )
            Future(
              Redirect(
                routes.UserController.all(group.areaIds.headOption.getOrElse(Area.allArea.id)),
                303
              )
            )
          }
        }
      }
    }

  def editGroup(id: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(id) { (group: UserGroup) =>
        asUserWithAuthorization(Authorization.canEditGroup(group))(
          EditGroupUnauthorized,
          s"Tentative d'accès non autorisé à l'edition du groupe ${group.id}",
          Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?").some
        ) { () =>
          editGroupPage(group, AddUserToGroupFormData.form)
        }
      }
    }

  def addGroup: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(AddGroupUnauthorized, s"Accès non autorisé pour ajouter un groupe") { () =>
        AddGroupFormData
          .form(Time.timeZoneParis, request.currentUser)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              eventService
                .log(
                  AddUserGroupError,
                  s"Essai d'ajout d'un groupe avec des erreurs de validation (${formErrorsLog(formWithErrors)})"
                )
              Future(
                Redirect(routes.UserController.home).flashing(
                  "error" -> s"Impossible d'ajouter le groupe : ${formWithErrors.errors.map(_.format).mkString(", ")}"
                )
              )
            },
            group =>
              groupService
                .add(group)
                .fold(
                  { (error: String) =>
                    eventService
                      .log(
                        AddUserGroupError,
                        "Impossible d'ajouter le groupe dans la BDD",
                        s"Groupe ${group.toLogString} Erreur '$error'".some
                      )
                    Future(
                      Redirect(routes.UserController.home)
                        .flashing("error" -> s"Impossible d'ajouter le groupe : $error")
                    )
                  },
                  { _ =>
                    eventService.log(
                      UserGroupCreated,
                      s"Groupe ${group.id} ajouté par l'utilisateur d'id ${request.currentUser.id}",
                      s"Groupe ${group.toLogString}".some
                    )
                    Future(
                      Redirect(routes.GroupController.editGroup(group.id))
                        .flashing("success" -> "Groupe ajouté")
                    )
                  }
                )
          )
      }
    }

  def editGroupPost(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { (currentGroup: UserGroup) =>
        asUserWithAuthorization(Authorization.canEditGroup(currentGroup))(
          EditGroupUnauthorized,
          s"Tentative d'édition non autorisée du groupe ${currentGroup.id}",
          Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?").some
        ) { () =>
          AddGroupFormData
            .form(Time.timeZoneParis, request.currentUser)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                eventService.log(
                  EditUserGroupError,
                  s"Tentative d'edition du groupe ${currentGroup.id} avec des erreurs de validation (${formErrorsLog(formWithErrors)})"
                )
                Future(
                  Redirect(routes.GroupController.editGroup(groupId)).flashing(
                    "error" -> s"Impossible de modifier le groupe (erreur de formulaire) : ${formWithErrors.errors.map(_.format).mkString(", ")}"
                  )
                )
              },
              group => {
                // Only admins can change areas
                val groupAreaIds =
                  if (Authorization.isAdmin(request.rights))
                    group.areaIds
                  else
                    currentGroup.areaIds
                val newGroup = group.copy(id = groupId, areaIds = groupAreaIds)
                if (groupService.edit(newGroup)) {
                  eventService.log(
                    UserGroupEdited,
                    s"Groupe $groupId édité",
                    s"Groupe ${currentGroup.toDiffLogString(newGroup)}".some
                  )
                  Future(
                    Redirect(routes.GroupController.editGroup(groupId))
                      .flashing("success" -> "Groupe modifié")
                  )
                } else {
                  eventService
                    .log(
                      EditUserGroupError,
                      s"Impossible de modifier le groupe $groupId dans la BDD",
                      s"Groupe ${currentGroup.toDiffLogString(newGroup)}".some
                    )
                  Future(
                    Redirect(routes.GroupController.editGroup(groupId))
                      .flashing(
                        "error" -> "Impossible de modifier le groupe: erreur en base de donnée"
                      )
                  )
                }
              }
            )
        }
      }
    }

  private def addOrRemoveUserAction(
      groupId: UUID
  )(inner: RequestWithUserData[_] => UserGroup => Future[Result]): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { group =>
        asUserWithAuthorization(Authorization.canAddOrRemoveOtherUser(group))(
          EventType.EditGroupUnauthorized,
          s"L'utilisateur n'est pas autorisé à éditer le groupe $groupId",
          Redirect(routes.GroupController.showEditMyGroups)
            .flashing("error" -> "Vous n’avez pas le droit de modifier ce groupe")
            .some
        )(() => inner(request)(group))
      }
    }

  private def computeUsersInfos(
      usersIds: List[UUID]
  )(implicit request: RequestWithUserData[_]): Future[Map[UUID, UserInfos]] = userService
    .usersInfos(usersIds)
    .flatMap(
      _.fold(
        error => IO.blocking(eventService.logError(error)).as(Map.empty[UUID, UserInfos]),
        IO.pure
      )
    )
    .unsafeToFuture()

  private def adminLastActivity(ids: List[UUID], rights: Authorization.UserRights) =
    if (Authorization.isAdmin(rights))
      eventService.lastActivity(ids)
    else
      Future.successful(Nil.asRight)

  private def editMyGroupsPage(
      user: User,
      rights: Authorization.UserRights,
      addUserForm: Form[AddUserToGroupFormData]
  )(implicit request: RequestWithUserData[_]): Future[Result] = {
    val groupsFuture =
      if (Authorization.isAreaManager(rights))
        groupService.allForAreaManager(user)
      else
        groupService.byIdsFuture(user.groupIds)
    for {
      groups <- groupsFuture
      users <- userService.byGroupIdsFuture(groups.map(_.id), includeDisabled = true)
      usersInfos <- computeUsersInfos(users.map(_.id))
      lastActivityResult <- adminLastActivity(users.map(_.id), rights)
    } yield {
      lastActivityResult.fold(
        error => {
          eventService.logError(error)
          InternalServerError(Constants.genericError500Message)
        },
        lastActivity => {
          eventService.log(EventType.EditMyGroupShowed, "Visualise la modification de ses groupes")
          Ok(
            views.editMyGroups
              .page(
                user,
                rights,
                addUserForm,
                groups,
                users,
                usersInfos,
                lastActivity.toMap,
                identity
              )
          )
        }
      )
    }
  }

  private def editGroupPage(group: UserGroup, addUserForm: Form[AddUserToGroupFormData])(implicit
      request: RequestWithUserData[_]
  ): Future[Result] = {
    val groupUsers = userService.byGroupIds(List(group.id), includeDisabled = true)
    eventService.log(EditGroupShowed, s"Visualise la vue de modification du groupe")
    val isEmpty = groupService.isGroupEmpty(group.id)
    val lastActivityFuture = adminLastActivity(groupUsers.map(_.id), request.rights)
    lastActivityFuture.flatMap(
      _.fold(
        error => {
          eventService.logError(error)
          Future.successful(InternalServerError(Constants.genericError500Message))
        },
        lastActivity => {
          computeUsersInfos(groupUsers.map(_.id)).map(usersInfos =>
            Ok(
              views.html.editGroup(request.currentUser, request.rights)(
                group,
                groupUsers,
                isEmpty,
                usersInfos,
                lastActivity.toMap,
                addUserForm,
                url =>
                  (url + "?" +
                    Keys.QueryParam.redirect + "=" + groupPageRedirectValue + "&" +
                    Keys.QueryParam.groupId + "=" + group.id.toString)
              )
            )
          )
        }
      )
    )
  }

}
