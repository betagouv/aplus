package controllers

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

import actions.{LoginAction, RequestWithUserData}
import helper.BooleanHelper.not
import Operators.{GroupOperators, UserOperators}
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
  PostAddUserUnauthorized,
  PostEditUserUnauthorized,
  ShowAddUserUnauthorized,
  UserDeleted,
  UserEdited,
  UserIsUsed,
  UserNotFound,
  UserShowed,
  UsersCreated,
  UsersShowed,
  ViewUserUnauthorized
}
import models.{Area, EventType, User, UserGroup}
import org.postgresql.util.PSQLException
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.{Form, Mapping}
import play.api.mvc._
import play.filters.csrf.CSRF
import play.filters.csrf.CSRF.Token
import serializers.{Keys, UserAndGroupCsvSerializer}
import services._

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
    with play.api.i18n.I18nSupport
    with UserOperators
    with GroupOperators {

  def home = loginAction {
    TemporaryRedirect(controllers.routes.UserController.all(Area.allArea.id).url)
  }

  def all(areaId: UUID): Action[AnyContent] = loginAction.async {
    implicit request: RequestWithUserData[AnyContent] =>
      asUserWhoSeesUsersOfArea(areaId) { () =>
        AllUserUnauthorized -> "Accès non autorisé à l'admin des utilisateurs"
      } { () =>
        val selectedArea = Area.fromId(areaId).get
        val usersFuture: Future[List[User]] = (
          request.currentUser.admin,
          request.currentUser.groupAdmin,
          selectedArea.id == Area.allArea.id
        ) match {
          case (true, _, false) => {
            groupService.byArea(areaId).map { groupsOfArea =>
              userService.byGroupIds(groupsOfArea.map(_.id))
            }
          }
          case (true, _, true) => {
            val groupsOfArea = groupService.byAreas(request.currentUser.areas)
            Future(userService.byGroupIds(groupsOfArea.map(_.id)))
          }
          case (false, true, _) => Future(userService.byGroupIds(request.currentUser.groupIds))
          case _ =>
            eventService.log(AllUserIncorrectSetup, "Erreur d'accès aux utilisateurs")
            Future(Nil)
        }
        val applications = applicationService.allByArea(selectedArea.id, anonymous = true)
        val groups: List[UserGroup] = (
          request.currentUser.admin,
          request.currentUser.groupAdmin,
          selectedArea.id == Area.allArea.id
        ) match {
          case (true, _, false) => groupService.allGroupByAreas(List[UUID](areaId))
          case (true, _, true)  => groupService.allGroupByAreas(request.currentUser.areas)
          case (false, true, _) => groupService.byIds(request.currentUser.groupIds)
          case _ =>
            eventService.log(AllUserIncorrectSetup, "Erreur d'accès aux groupes")
            List()
        }
        eventService.log(UsersShowed, "Visualise la vue des utilisateurs")
        usersFuture.map { users =>
          val result = request.getQueryString("vue").getOrElse("nouvelle") match {
            case "nouvelle" if request.currentUser.admin =>
              views.html.allUsersNew(request.currentUser)(
                groups,
                users,
                applications,
                selectedArea,
                configuration.underlying.getString("geoplus.host")
              )
            case _ =>
              views.html.allUsersByGroup(request.currentUser)(
                groups,
                users,
                applications,
                selectedArea,
                configuration.underlying.getString("geoplus.host")
              )
          }
          Ok(result)
        }
      }
  }

  def allCSV(areaId: java.util.UUID): Action[AnyContent] = loginAction.async {
    implicit request: RequestWithUserData[AnyContent] =>
      asAdminWhoSeesUsersOfArea(areaId) { () =>
        AllUserCSVUnauthorized -> "Accès non autorisé à l'export utilisateur"
      } { () =>
        val area = Area.fromId(areaId).get
        val usersFuture: Future[List[User]] = if (areaId == Area.allArea.id) {
          val groupsOfArea = groupService.byAreas(request.currentUser.areas)
          Future(userService.byGroupIds(groupsOfArea.map(_.id)))
        } else {
          groupService.byArea(areaId).map { groupsOfArea =>
            userService.byGroupIds(groupsOfArea.map(_.id))
          }
        }
        val groups = groupService.allGroupByAreas(request.currentUser.areas)
        eventService.log(AllUserCsvShowed, "Visualise le CSV de tous les zones de l'utilisateur")

        def userToCSV(user: User): String =
          List[String](
            user.id.toString,
            user.name,
            user.qualite,
            user.email,
            Time.formatPatternFr(user.creationDate, "dd-MM-YYYY-HHhmm"),
            if (user.sharedAccount) "Compte Partagé" else " ",
            if (user.helper) "Aidant" else " ",
            if (user.instructor) "Instructeur" else " ",
            if (user.groupAdmin) "Responsable" else " ",
            if (user.expert) "Expert" else " ",
            if (user.admin) "Admin" else " ",
            if (user.disabled) "Désactivé" else " ",
            user.communeCode,
            user.areas.flatMap(Area.fromId).map(_.name).mkString(","),
            user.groupIds.flatMap(id => groups.find(_.id == id)).map(_.name).mkString(","),
            if (user.cguAcceptationDate.nonEmpty) "CGU Acceptées" else "",
            if (user.newsletterAcceptationDate.nonEmpty) "Newsletter Acceptée" else ""
          ).mkString(";")

        val headers = List[String](
          "Id",
          UserAndGroupCsvSerializer.USER_NAME.prefixes(0),
          "Qualité",
          UserAndGroupCsvSerializer.USER_EMAIL.prefixes(0),
          "Création",
          UserAndGroupCsvSerializer.USER_ACCOUNT_IS_SHARED.prefixes(0),
          "Aidant",
          UserAndGroupCsvSerializer.USER_INSTRUCTOR.prefixes(0),
          UserAndGroupCsvSerializer.USER_GROUP_MANAGER.prefixes(0),
          "Expert",
          "Admin",
          "Actif",
          "Commune INSEE",
          UserAndGroupCsvSerializer.GROUP_AREAS_IDS.prefixes(0),
          UserAndGroupCsvSerializer.GROUP_NAME.prefixes(0),
          "CGU",
          "Newsletter"
        ).mkString(";")

        usersFuture.map { users =>
          val csvContent = (List(headers) ++ users.map(userToCSV)).mkString("\n")
          val date = Time.formatPatternFr(Time.nowParis(), "dd-MMM-YYY-HH'h'mm")
          val filename = "aplus-" + date + "-users-" + area.name.replace(" ", "-") + ".csv"
          Ok(csvContent)
            .withHeaders("Content-Disposition" -> s"""attachment; filename="$filename"""")
            .as("text/csv")
        }
      }
  }

  def editUser(userId: UUID): Action[AnyContent] = loginAction {
    implicit request: RequestWithUserData[AnyContent] =>
      asAdmin { () =>
        ViewUserUnauthorized -> s"Accès non autorisé pour voir $userId"
      } { () =>
        userService.byId(userId, includeDisabled = true) match {
          case None =>
            eventService.log(UserNotFound, s"L'utilisateur $userId n'existe pas")
            NotFound("Nous n'avons pas trouvé cet utilisateur")
          case Some(user) if user.canBeEditedBy(request.currentUser) =>
            val form = userForm(Time.timeZoneParis).fill(user)
            val groups = groupService.allGroups
            val unused = not(isAccountUsed(user))
            val Token(tokenName, tokenValue) = CSRF.getToken.get
            eventService
              .log(UserShowed, "Visualise la vue de modification l'utilisateur ", user = Some(user))
            Ok(
              views.html.editUser(request.currentUser)(
                form,
                userId,
                groups,
                unused,
                tokenName = tokenName,
                tokenValue = tokenValue
              )
            )
          case _ =>
            eventService.log(ViewUserUnauthorized, s"Accès non autorisé pour voir $userId")
            Unauthorized("Vous n'avez pas le droit de faire ça")
        }
      }
  }

  def isAccountUsed(user: User): Boolean =
    applicationService.allForUserId(userId = user.id, anonymous = false).nonEmpty

  def deleteUnusedUserById(userId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withUser(userId, includeDisabled = true) { user: User =>
      asAdminOfUserZone(user) { () =>
        DeleteUserUnauthorized -> s"Suppression de l'utilisateur $userId refusée."
      } { () =>
        if (isAccountUsed(user)) {
          eventService.log(UserIsUsed, description = s"Le compte ${user.id} est utilisé.")
          Unauthorized("User is not unused.")
        } else {
          userService.deleteById(userId)
          val message = s"Utilisateur $userId / ${user.email} a été supprimé"
          eventService.log(UserDeleted, message, user = Some(user))
          Redirect(controllers.routes.UserController.home).flashing("success" -> message)
        }
      }
    }
  }

  def editUserPost(userId: UUID): Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      PostEditUserUnauthorized -> s"Accès non autorisé à modifier $userId"
    } { () =>
      userForm(Time.timeZoneParis).bindFromRequest.fold(
        formWithErrors => {
          val groups = groupService.allGroups
          eventService.log(
            AddUserError,
            s"Essai de modification de l'tilisateur $userId avec des erreurs de validation"
          )
          BadRequest(views.html.editUser(request.currentUser)(formWithErrors, userId, groups))
        },
        updatedUser =>
          withUser(updatedUser.id, includeDisabled = true) { user: User =>
            // Ensure that user include all areas of his group
            val groups = groupService.byIds(updatedUser.groupIds)
            val areaIds = (updatedUser.areas ++ groups.flatMap(_.areaIds)).distinct
            val userToUpdate = updatedUser.copy(
              areas = areaIds.intersect(request.currentUser.areas)
            ) // intersect is a safe gard (In case an Admin try to manage an authorized area)

            if (not(user.canBeEditedBy(request.currentUser))
                || areaIds
                  .diff(request.currentUser.areas)
                  .nonEmpty) { // fail if in case an Admin try to manage an authorized area
              eventService.log(PostEditUserUnauthorized, s"Accès non autorisé à modifier $userId")
              Unauthorized("Vous n'avez pas le droit de faire ça")
            } else if (userService.update(userToUpdate)) {
              eventService.log(UserEdited, s"Utilisateur $userId modifié", user = Some(updatedUser))
              Redirect(routes.UserController.editUser(userId))
                .flashing("success" -> "Utilisateur modifié")
            } else {
              val form = userForm(Time.timeZoneParis)
                .fill(userToUpdate)
                .withGlobalError(
                  s"Impossible de mettre à jour l'utilisateur $userId (Erreur interne)"
                )
              val groups = groupService.allGroups
              eventService.log(
                EditUserError,
                "Impossible de modifier l'utilisateur dans la BDD",
                user = Some(updatedUser)
              )
              InternalServerError(views.html.editUser(request.currentUser)(form, userId, groups))
            }
          }
      )
    }
  }

  def addPost(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(groupId) { group: UserGroup =>
      if (!group.canHaveUsersAddedBy(request.currentUser)) {
        eventService.log(PostAddUserUnauthorized, "Accès non autorisé à l'admin des utilisateurs")
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        usersForm(Time.timeZoneParis, group.areaIds).bindFromRequest.fold(
          { formWithErrors =>
            eventService
              .log(AddUserError, "Essai d'ajout d'utilisateurs avec des erreurs de validation")
            BadRequest(
              views.html.editUsers(request.currentUser)(
                formWithErrors,
                0,
                routes.UserController.addPost(groupId)
              )
            )
          }, { users =>
            try {
              val userToInsert = users.map(_.copy(groupIds = List(groupId)))
              userService
                .add(userToInsert)
                .fold(
                  {
                    error =>
                      val errorMessage =
                        s"Impossible d'ajouté les utilisateurs (Erreur interne 1) $error"
                      eventService.log(AddUserError, errorMessage)
                      val form = usersForm(Time.timeZoneParis, group.areaIds)
                        .fill(users)
                        .withGlobalError(errorMessage)
                      InternalServerError(
                        views.html.editUsers(request.currentUser)(
                          form,
                          users.length,
                          routes.UserController.addPost(groupId)
                        )
                      )
                  }, {
                    Unit =>
                      users.foreach { user =>
                        notificationsService.newUser(user)
                        eventService.log(
                          EventType.UserCreated,
                          s"Ajout de l'utilisateur ${user.name} ${user.email}",
                          user = Some(user)
                        )
                      }
                      eventService.log(UsersCreated, "Utilisateurs ajoutés")
                      Redirect(routes.GroupController.editGroup(groupId))
                        .flashing("success" -> "Utilisateurs ajouté")
                  }
                )
            } catch {
              case ex: PSQLException =>
                val EmailErrorPattern = """[^()@]+@[^()@.]+\.[^()@]+""".r // This didn't work in that case : """ Detail: Key \(email\)=\(([^()]*)\) already exists."""".r  (don't know why, the regex is correct)
                val errorMessage =
                  EmailErrorPattern.findFirstIn(ex.getServerErrorMessage.toString) match {
                    case Some(email) => s"Un utilisateur avec l'adresse $email existe déjà."
                    case _ =>
                      "Erreur d'insertion dans la base de donnée : contacter l'administrateur."
                  }
                val form = usersForm(Time.timeZoneParis, group.areaIds)
                  .fill(users)
                  .withGlobalError(errorMessage)
                eventService.log(
                  AddUserError,
                  s"Impossible d'ajouter des utilisateurs dans la BDD : ${ex.getServerErrorMessage}"
                )
                BadRequest(
                  views.html.editUsers(request.currentUser)(
                    form,
                    users.length,
                    routes.UserController.addPost(groupId)
                  )
                )
            }
          }
        )
      }
    }
  }

  def showCGU(): Action[AnyContent] = loginAction { implicit request =>
    eventService.log(CGUShowed, "CGU visualisé")
    Ok(views.html.showCGU(request.currentUser))
  }

  def validateCGU(): Action[AnyContent] = loginAction { implicit request =>
    validateCGUForm.bindFromRequest.fold(
      { formWithErrors =>
        eventService.log(CGUValidationError, "Erreur de formulaire dans la validation des CGU")
        BadRequest(
          s"Formulaire invalide, prévenez l'administrateur du service. ${formWithErrors.errors.mkString(", ")}"
        )
      }, {
        case (redirectOption, newsletter, validate) =>
          if (validate) {
            userService.acceptCGU(request.currentUser.id, newsletter)
          }
          eventService.log(CGUValidated, "CGU validées")
          redirectOption match {
            case Some(redirect) =>
              Redirect(Call("GET", redirect))
                .flashing("success" -> "Merci d\'avoir accepté les CGU")
            case _ =>
              Redirect(routes.ApplicationController.myApplications())
                .flashing("success" -> "Merci d\'avoir accepté les CGU")
          }
      }
    )
  }

  val validateCGUForm: Form[(Option[String], Boolean, Boolean)] = Form(
    tuple(
      "redirect" -> optional(text),
      "newsletter" -> boolean,
      "validate" -> boolean
    )
  )

  def add(groupId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withGroup(groupId) { group: UserGroup =>
      if (!group.canHaveUsersAddedBy(request.currentUser)) {
        eventService.log(
          ShowAddUserUnauthorized,
          s"Accès non autorisé à l'admin des utilisateurs du groupe $groupId"
        )
        Unauthorized("Vous n'avez pas le droit de faire ça")
      } else {
        val rows = request.getQueryString("rows").map(_.toInt).getOrElse(1)
        eventService.log(EditUserShowed, "Visualise la vue d'ajouts des utilisateurs")
        Ok(
          views.html.editUsers(request.currentUser)(
            usersForm(Time.timeZoneParis, group.areaIds),
            rows,
            routes.UserController.addPost(groupId)
          )
        )
      }
    }
  }

  def allEvents: Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      EventsUnauthorized -> "Accès non autorisé pour voir les événements"
    } { () =>
      val limit = request.getQueryString("limit").map(_.toInt).getOrElse(500)
      val userId = request.getQueryString("fromUserId").flatMap(UUIDHelper.fromString)
      val events = eventService.all(limit, userId)
      eventService.log(EventsShowed, s"Affiche les événements")
      Ok(views.html.allEvents(request.currentUser)(events, limit))
    }
  }

  def usersForm(timeZone: ZoneId, areaIds: List[UUID]): Form[List[User]] = Form(
    single(
      "users" -> list(
        mapping(
          "id" -> optional(uuid).transform[UUID]({
            case None     => UUID.randomUUID()
            case Some(id) => id
          }, {
            Some(_)
          }),
          "key" -> ignored("key"),
          "name" -> nonEmptyText.verifying(maxLength(100)),
          "qualite" -> text.verifying(maxLength(100)),
          "email" -> email.verifying(maxLength(200), nonEmpty),
          "helper" -> ignored(true),
          "instructor" -> boolean,
          "admin" -> ignored(false),
          "areas" -> ignored(areaIds),
          "creationDate" -> ignored(ZonedDateTime.now(timeZone)),
          "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),
          "adminGroup" -> boolean,
          "disabled" -> ignored(false),
          "expert" -> ignored(false),
          "groupIds" -> default(list(uuid), List()),
          "cguAcceptationDate" -> ignored(Option.empty[ZonedDateTime]),
          "newsletterAcceptationDate" -> ignored(Option.empty[ZonedDateTime]),
          "phone-number" -> optional(text),
          Keys.User.sharedAccount -> boolean
        )(User.apply)(User.unapply)
      )
    )
  )

  def userForm(timeZone: ZoneId): Form[User] = Form(userMapping(timeZone))

  private def userMapping(implicit timeZone: ZoneId): Mapping[User] =
    mapping(
      "id" -> optional(uuid).transform[UUID]({
        case None     => UUID.randomUUID()
        case Some(id) => id
      }, {
        Some(_)
      }),
      "key" -> ignored("key"),
      "name" -> nonEmptyText.verifying(maxLength(100)),
      "qualite" -> text.verifying(maxLength(100)),
      "email" -> email.verifying(maxLength(200), nonEmpty),
      "helper" -> boolean,
      "instructor" -> boolean,
      "admin" -> boolean,
      "areas" -> list(uuid).verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
      "creationDate" -> ignored(ZonedDateTime.now(timeZone)),
      "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),
      "adminGroup" -> boolean,
      "disabled" -> boolean,
      "expert" -> ignored(false),
      "groupIds" -> default(list(uuid), Nil),
      "cguAcceptationDate" -> ignored(Option.empty[ZonedDateTime]),
      "newsletterAcceptationDate" -> ignored(Option.empty[ZonedDateTime]),
      "phone-number" -> optional(text),
      Keys.User.sharedAccount -> boolean
    )(User.apply)(User.unapply)
}
