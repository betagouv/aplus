package controllers

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import actions.{LoginAction, RequestWithUserData}
import cats.syntax.all._
import constants.Constants
import Operators._
import javax.inject.{Inject, Singleton}
import models.{Area, Authorization, Error, EventType, Organisation, User, UserGroup}
import models.formModels.{normalizedOptionalText, normalizedText, AddUserToGroupFormData}
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{email, ignored, list, mapping, of, optional, text, uuid}
import play.api.data.validation.Constraints.maxLength
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Call, InjectedController, RequestHeader, Result}
import play.libs.ws.WSClient
import services._
import helper.BooleanHelper.not
import helper.Time
import helper.StringHelper.commonStringInputNormalization
import models.EventType.{
  AddGroupUnauthorized,
  AddUserGroupError,
  AddUserToGroupUnauthorized,
  EditGroupShowed,
  EditGroupUnauthorized,
  EditUserGroupError,
  GroupDeletionUnauthorized,
  UserGroupCreated,
  UserGroupDeleted,
  UserGroupDeletionUnauthorized,
  UserGroupEdited
}
import scala.concurrent.{ExecutionContext, Future}
import serializers.Keys

@Singleton
case class GroupController @Inject() (
    applicationService: ApplicationService,
    loginAction: LoginAction,
    groupService: UserGroupService,
    notificationService: NotificationService,
    eventService: EventService,
    configuration: Configuration,
    ws: WSClient,
    userService: UserService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with I18nSupport
    with Operators.Common
    with GroupOperators
    with UserOperators {

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

  def addToGroup(groupId: UUID) =
    addOrRemoveUserAction(groupId) { implicit request =>
      val (originIsGroupPage, redirectPage) = groupModificationOriginPage
      AddUserToGroupFormData.form
        .bindFromRequest()
        .fold(
          err => {
            val message = "L’adresse email n'est pas correcte"
            eventService.log(EventType.AddUserToGroupBadUserInput, message)
            if (originIsGroupPage) withGroup(groupId)(group => editGroupPage(group, err))
            else editMyGroupsPage(err)
          },
          data =>
            userService
              .byEmailFuture(data.email)
              .zip(userService.byGroupIdsFuture(List(groupId), includeDisabled = true))
              .flatMap {
                case (None, _) =>
                  eventService.log(
                    EventType.AddUserToGroupBadUserInput,
                    s"Tentative d'ajout de l'utilisateur inexistant ${data.email} au groupe $groupId"
                  )
                  Future.successful(
                    Redirect(redirectPage)
                      .flashing("error" -> "L’utilisateur n’existe pas dans Administration+")
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

  def enableUser(userId: UUID) =
    loginAction.async { implicit request =>
      val (_, redirectPage) = groupModificationOriginPage
      withUser(
        userId,
        includeDisabled = true,
        errorMessage = s"L'utilisateur $userId n'existe pas et ne peut pas être réactivé",
        errorResult = Redirect(redirectPage)
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
                  Redirect(redirectPage).flashing("error" -> Constants.error500FlashMessage)
                },
                _ => {
                  eventService.log(
                    EventType.UserEdited,
                    s"Utilisateur $userId réactivé",
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

  def removeFromGroup(userId: UUID, groupId: UUID) =
    addOrRemoveUserAction(groupId) { implicit request =>
      val (_, redirectPage) = groupModificationOriginPage
      withUser(
        userId,
        includeDisabled = true,
        errorMessage = s"L'utilisateur $userId n'existe pas et ne peut pas être désactivé",
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

  def showEditMyGroups =
    loginAction.async { implicit request =>
      editMyGroupsPage(AddUserToGroupFormData.form)
    }

  def deleteUnusedGroupById(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { group: UserGroup =>
        asAdminOfGroupZone(group) { () =>
          GroupDeletionUnauthorized -> s"Droits insuffisants pour la suppression du groupe ${groupId}."
        } { () =>
          val empty = groupService.isGroupEmpty(group.id)
          if (not(empty)) {
            eventService.log(
              UserGroupDeletionUnauthorized,
              description = s"Tentative de suppression d'un groupe utilisé ($groupId)"
            )
            Future(Unauthorized("Le groupe est utilisé."))
          } else {
            groupService.deleteById(groupId)
            eventService.log(UserGroupDeleted, s"Groupe supprimé ${group.toLogString}")
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
      withGroup(id) { group: UserGroup =>
        asUserWithAuthorization(Authorization.canEditGroup(group))(
          () =>
            (
              EditGroupUnauthorized,
              s"Tentative d'accès non autorisé à l'edition du groupe ${group.id}"
            ),
          Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?").some
        ) { () =>
          editGroupPage(group, AddUserToGroupFormData.form)
        }
      }
    }

  def addGroup: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() => AddGroupUnauthorized -> s"Accès non autorisé pour ajouter un groupe") { () =>
        addGroupForm(Time.timeZoneParis)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val errorString: String = formWithErrors.errors.mkString
              eventService
                .log(
                  AddUserGroupError,
                  s"Essai d'ajout d'un groupe avec des erreurs de validation: $errorString"
                )
              Future(
                Redirect(routes.UserController.home).flashing(
                  "error" -> s"Impossible d'ajouter le groupe : $errorString"
                )
              )
            },
            group =>
              groupService
                .add(group)
                .fold(
                  { error: String =>
                    val message =
                      s"Impossible d'ajouter le groupe dans la BDD. " +
                        group.toLogString + s" Détail de l'erreur: $error"
                    eventService
                      .log(AddUserGroupError, message)
                    Future(
                      Redirect(routes.UserController.home)
                        .flashing("error" -> s"Impossible d'ajouter le groupe : $error")
                    )
                  },
                  { _ =>
                    eventService.log(
                      UserGroupCreated,
                      s"Groupe ${group.name} ajouté par l'utilisateur d'id ${request.currentUser.id} ${group.toLogString}"
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
      withGroup(groupId) { currentGroup: UserGroup =>
        asUserWithAuthorization(Authorization.canEditGroup(currentGroup))(
          () =>
            (
              EditGroupUnauthorized,
              s"Tentative d'édition non autorisée du groupe ${currentGroup.id}"
            ),
          Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?").some
        ) { () =>
          addGroupForm(Time.timeZoneParis)
            .bindFromRequest()
            .fold(
              formWithError => {
                eventService.log(
                  EditUserGroupError,
                  s"Tentative d'edition du groupe ${currentGroup.id} avec des erreurs de validation"
                )
                Future(
                  Redirect(routes.GroupController.editGroup(groupId)).flashing(
                    "error" -> s"Impossible de modifier le groupe (erreur de formulaire) : ${formWithError.errors.mkString}"
                  )
                )
              },
              group => {
                val newGroup = group.copy(id = groupId)
                if (groupService.edit(newGroup)) {
                  eventService.log(
                    UserGroupEdited,
                    s"Groupe édité ${currentGroup.toDiffLogString(newGroup)}"
                  )
                  Future(
                    Redirect(routes.GroupController.editGroup(groupId))
                      .flashing("success" -> "Groupe modifié")
                  )
                } else {
                  eventService
                    .log(
                      EditUserGroupError,
                      s"Impossible de modifier le groupe dans la BDD ${currentGroup.toDiffLogString(newGroup)}"
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
  )(inner: RequestWithUserData[_] => Future[Result]): Action[AnyContent] =
    loginAction.async { implicit request =>
      asUserWithAuthorization(Authorization.canAddOrRemoveOtherUser(groupId))(
        () =>
          (
            EventType.EditGroupUnauthorized,
            s"L'utilisateur n'est pas autorisé à éditer le groupe $groupId"
          ), {
          val message = "Vous n’avez pas le droit de modifier ce groupe"
          Redirect(routes.GroupController.showEditMyGroups).flashing("error" -> message).some
        }
      )(() => inner(request))
    }

  private def editMyGroupsPage(
      addUserForm: Form[AddUserToGroupFormData]
  )(implicit request: RequestWithUserData[_]): Future[Result] =
    for {
      groups <- groupService.byIdsFuture(request.currentUser.groupIds)
      users <- userService.byGroupIdsFuture(groups.map(_.id), includeDisabled = true)
      applications <- applicationService.allForUserIds(users.map(_.id))
    } yield {
      eventService.log(EventType.EditMyGroupShowed, "Visualise la modification de ses groupes")
      Ok(
        views.editMyGroups
          .page(
            request.currentUser,
            request.rights,
            addUserForm,
            groups,
            users,
            applications,
            identity
          )
      )
    }

  private def editGroupPage(group: UserGroup, addUserForm: Form[AddUserToGroupFormData])(implicit
      request: RequestWithUserData[_]
  ): Future[Result] = {
    val groupUsers = userService.byGroupIds(List(group.id), includeDisabled = true)
    eventService.log(EditGroupShowed, s"Visualise la vue de modification du groupe")
    val isEmpty = groupService.isGroupEmpty(group.id)
    applicationService
      .allForUserIds(groupUsers.map(_.id))
      .map(applications =>
        Ok(
          views.html.editGroup(request.currentUser, request.rights)(
            group,
            groupUsers,
            isEmpty,
            applications,
            addUserForm,
            url =>
              (url + "?" +
                Keys.QueryParam.redirect + "=" + groupPageRedirectValue + "&" +
                Keys.QueryParam.groupId + "=" + group.id.toString)
          )
        )
      )
  }

  private def addGroupForm[A](timeZone: ZoneId)(implicit request: RequestWithUserData[A]) =
    Form(
      mapping(
        "id" -> ignored(UUID.randomUUID()),
        "name" -> normalizedText.verifying(maxLength(UserGroup.nameMaxLength)),
        "description" -> normalizedOptionalText,
        "insee-code" -> list(text),
        "creationDate" -> ignored(ZonedDateTime.now(timeZone)),
        "area-ids" -> list(uuid)
          .verifying(
            "Vous devez sélectionner les territoires sur lequel vous êtes admin",
            areaIds => areaIds.forall(request.currentUser.areas.contains[UUID])
          )
          .verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
        "organisation" -> optional(of[Organisation.Id]),
        "email" -> optional(email),
        "publicNote" -> normalizedOptionalText,
        "internalSupportComment" -> normalizedOptionalText
      )(UserGroup.apply)(UserGroup.unapply)
    )

}
