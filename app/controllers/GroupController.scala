package controllers

import java.util.UUID

import actions.{LoginAction, RequestWithUserData}
import extentions.Operators._
import extentions.Time
import javax.inject.{Inject, Singleton}
import models.{Area, UserGroup}
import org.joda.time.{DateTime, DateTimeZone}
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{boolean, email, ignored, list, mapping, optional, text, uuid}
import play.api.mvc.{Action, AnyContent, InjectedController}
import play.libs.ws.WSClient
import services._
import extentions.BooleanHelper.not
import models.EventType.{UserGroupCreated, UserGroupDeleted, EditGroupShowed, UserGroupEdited}

import scala.collection.parallel.immutable.ParSeq

@Singleton
case class GroupController @Inject()(loginAction: LoginAction,
                                     groupService: UserGroupService,
                                     notificationService: NotificationService,
                                     eventService: EventService,
                                     configuration: Configuration,
                                     ws:WSClient,
                                     userService: UserService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with GroupOperators with UserOperators {

  def deleteUnusedGroupById(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(groupId) { group: UserGroup =>
      asAdminOfGroupZone(group) { () =>
        "GROUP_DELETION_UNAUTHORIZED" -> s"Droits insuffisants pour la suppression du groupe ${groupId}."
      } { () =>
        val empty = groupService.isGroupEmpty(group.id)
        if (not(empty)) {
          eventService.error(code = "USED_GROUP_DELETION_UNAUTHORIZED", description = "Tentative de suppression d'un groupe utilisé.")
          Unauthorized("Group is not unused.")
        } else {
          groupService.deleteById(groupId)
          eventService.log(UserGroupDeleted, s"Suppression du groupe ${groupId}.")
          val path = "/" + controllers.routes.AreaController.all.relativeTo("/")
          Redirect(path, 303)
        }
      }
    }
  }

  def editGroup(id: UUID): Action[AnyContent] = loginAction { implicit request =>
    val Host = configuration.underlying.getString("geoplus.host")

    withGroup(id) { group: UserGroup =>
      if (!group.canHaveUsersAddedBy(request.currentUser)) {
        eventService.warn("EDIT_GROUPE_UNAUTHORIZED", s"Accès non autorisé à l'edition de ce groupe")
        Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?")
      } else {
        val groupUsers = userService.byGroupIds(List(id))
        eventService.log(EditGroupShowed, s"Visualise la vue de modification du groupe")
        val isEmpty = groupService.isGroupEmpty(group.id)
        val areas: ParSeq[(String, String)] = for {
          code <- group.inseeCode.par
        } yield {
          val url = s"https://${Host}/bycode/?code=$code"
          code -> ws.url(url).get().toCompletableFuture.get().getBody
        }
        val zoneAsJson = areas.map({ case (code, name) => s"""{ "code": "$code", "name": "$name" }""" }).mkString("[", ",", "]")
        Ok(views.html.editGroup(request.currentUser)(group, groupUsers, isEmpty, zoneAsJson, Host))
      }
    }
  }

  def addGroup(): Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      "ADD_GROUP_UNAUTHORIZED" -> s"Accès non autorisé pour ajouter un groupe"
    } { () =>
      addGroupForm(Time.dateTimeZone).bindFromRequest.fold(formWithErrors => {
        eventService.error("ADD_USER_GROUP_ERROR", s"Essai d'ajout d'un groupe avec des erreurs de validation")
        Redirect(routes.UserController.home).flashing("error" -> s"Impossible d'ajouter le groupe : ${formWithErrors.errors.mkString}")
      }, group => {
        groupService.add(group).fold({ error: String =>
          eventService.error("ADD_USER_GROUP_ERROR", s"Impossible d'ajouter le groupe dans la BDD")
          Redirect(routes.UserController.home).flashing("error" -> s"Impossible d'ajouter le groupe : $error")
        }, {  Unit =>
          eventService.log(UserGroupCreated, s"Groupe ${group.name} (id : ${group.id}) ajouté par l'utilisateur d'id ${request.currentUser.id}")
          Redirect(routes.GroupController.editGroup(group.id)).flashing("success" -> "Groupe ajouté")
        })
      })
    }
  }

  def editGroupPost(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      "EDIT_GROUPE_UNAUTHORIZED" -> s"Accès non autorisé à l'edition de ce groupe"
    } { () =>
      withGroup(groupId) { currentGroup: UserGroup =>
        if (not(currentGroup.canHaveUsersAddedBy(request.currentUser))) {
          eventService.warn("ADD_USER_TO_GROUP_UNAUTHORIZED", s"L'utilisateur ${request.currentUser.id} n'est pas authorisé à ajouter des utilisateurs au groupe ${currentGroup.id}.")
          Unauthorized("Vous n'êtes pas authorisé à ajouter des utilisateurs à ce groupe.")
        } else {
          addGroupForm(Time.dateTimeZone).bindFromRequest.fold( formWithError => {
            eventService.error("EDIT_USER_GROUP_ERROR", s"Essai d'edition d'un groupe avec des erreurs de validation")
            Redirect(routes.GroupController.editGroup(groupId)).flashing("error" -> s"Impossible de modifier le groupe (erreur de formulaire) : ${formWithError.errors.mkString}")
          }, group => {
            if (groupService.edit(group.copy(id = groupId))) {
              eventService.log(UserGroupEdited, s"Groupe édité")
              Redirect(routes.GroupController.editGroup(groupId)).flashing("success" -> "Groupe modifié")
            } else {
              eventService.error("EDIT_USER_GROUP_ERROR", s"Impossible de modifier le groupe dans la BDD")
              Redirect(routes.GroupController.editGroup(groupId)).flashing("error" -> "Impossible de modifier le groupe: erreur en base de donnée")
            }
          }
          )
        }
      }
    }
  }

  def addGroupForm[A](timeZone: DateTimeZone)(implicit request: RequestWithUserData[A]) = Form(
    mapping(
      "id" -> ignored(UUID.randomUUID()),
      "name" -> text(maxLength = 60),
      "description" -> optional(text),
      "insee-code" -> list(text),
      "creationDate" -> ignored(DateTime.now(timeZone)),
      "area-ids" -> list(uuid).verifying(
                          "Vous devez sélectionner les territoires sur lequel vous êtes admin",
                          areaIds => areaIds.forall(request.currentUser.areas.contains)
                        ).verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
      "organisation" -> optional(text),
      "email" -> optional(email)
    )(UserGroup.apply)(UserGroup.unapply)
  )
}