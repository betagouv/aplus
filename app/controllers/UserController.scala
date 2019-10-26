package controllers

import java.util.{Locale, UUID}

import javax.inject.{Inject, Singleton}
import actions.{LoginAction, RequestWithUserData}
import extentions.{Hash, Time, UUIDHelper}
import forms.FormsPlusMap
import models.User.date
import models.{Area, Organisation, User, UserGroup}
import org.joda.time.{DateTime, DateTimeZone}
import org.postgresql.util.PSQLException
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc.{Action, AnyContent, Call, InjectedController, Result}
import play.filters.csrf.CSRF
import play.filters.csrf.CSRF.Token
import services.{ApplicationService, EventService, NotificationService, UserGroupService, UserService}

@Singleton
class UserController @Inject()(loginAction: LoginAction,
                               userService: UserService,
                               userGroupService: UserGroupService,
                               applicationService: ApplicationService,
                               notificationsService: NotificationService,
                               eventService: EventService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport {

  def all(areaId: UUID) = loginAction { implicit request =>
    if(request.currentUser.canSeeUsersInArea(areaId) == false) {
      eventService.warn("ALL_USER_UNAUTHORIZED", s"Accès non autorisé à l'admin des utilisateurs")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      val selectedArea = Area.fromId(areaId).get
      val users = (request.currentUser.admin, request.currentUser.groupAdmin, selectedArea.id == Area.allArea.id) match {
        case (true, _, false)  => userService.byArea(areaId)
        case (true, _, true)  => userService.byAreas(request.currentUser.areas)
        case (false, true, _) => userService.byGroupIds(request.currentUser.groupIds)
        case _ =>
          eventService.warn("ALL_USER_INCORRECT_SETUP", s"Erreur d'accès aux utilisateurs")
          List()
      }

      val applications = applicationService.allByArea(selectedArea.id, true)
      val groups: List[UserGroup] = (request.currentUser.admin, request.currentUser.groupAdmin, selectedArea.id == Area.allArea.id) match {
        case (true, _, false)  => userGroupService.allGroupByAreas(List[UUID](areaId))
        case (true, _, true) => userGroupService.allGroupByAreas(request.currentUser.areas)
        case (false, true, _) => userGroupService.groupByIds(request.currentUser.groupIds)
        case _ =>
          eventService.warn("ALL_USER_INCORRECT_SETUP", s"Erreur d'accès aux groupes")
          List()
      }

      eventService.info("ALL_USER_SHOWED", s"Visualise la vue des utilisateurs")

      Ok(views.html.allUsers(request.currentUser)(groups, users, applications, selectedArea))
    }
  }


  def allCSV(areaId: java.util.UUID) = loginAction { implicit request =>
    if(request.currentUser.admin == false || request.currentUser.canSeeUsersInArea(areaId) == false) {
      eventService.warn("ALL_USER_CSV_UNAUTHORIZED", s"Accès non autorisé à l'export utilisateur")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      val area = Area.fromId(areaId).get
      val users = if(areaId == Area.allArea.id) {
        userService.byAreas(request.currentUser.areas) }
      else {
        userService.byArea(areaId)
      }
      val groups = userGroupService.allGroupByAreas(request.currentUser.areas)
      eventService.info("ALL_USER_CSV_SHOWED", s"Visualise le CSV de tous les zones de l'utilisateur")

      def userToCSV(user: User): String = {
        List[String](
          user.id.toString,
          user.name,
          user.qualite,
          user.email,
          user.creationDate.toString("dd-MM-YYYY-HHhmm", new Locale("fr")),
          if(user.helper) { "Aidant" } else { " " },
          if(user.instructor) { "Instructeur" } else { " " },
          if(user.groupAdmin) { "Responsable" } else { " " },
          if(user.expert) { "Expert" } else { " " },
          if(user.admin) { "Admin" } else { " " },
          if(user.disabled) { "Désactivé" } else { " " },
          user.communeCode,
          user.areas.flatMap(Area.fromId).map(_.name).mkString(","),
          user.groupIds.flatMap(id => groups.find(_.id == id)).map(_.name).mkString(","),
          if(user.cguAcceptationDate.nonEmpty) { "CGU Acceptées" } else { "" },
          if(user.newsletterAcceptationDate.nonEmpty) { "Newsletter Acceptée"} else { "" },
        ).mkString(";")
      }
      val headers = List[String]("Id", "Nom", "Qualité", "Email", "Création","Aidant","Instructeur","Responsable","Expert","Admin","Actif","Commune INSEE", "Territoires","Groupes", "CGU", "Newsletter").mkString(";")
      val csv = (List(headers) ++ users.map(userToCSV)).mkString("\n")
      val date = DateTime.now(timeZone).toString("dd-MMM-YYY-HHhmm", new Locale("fr"))

      Ok(csv).withHeaders("Content-Disposition" -> s"""attachment; filename="aplus-${date}-users-${area.name.replace(" ","-")}.csv"""" )
    }
  }

  private val timeZone = DateTimeZone.forID("Europe/Paris")

  def userMapping = mapping(
    "id" -> optional(uuid).transform[UUID]({
        case None => UUID.randomUUID()
        case Some(id) => id
      }, { Some(_) }),
    "key" -> ignored("key"),
    "name" -> nonEmptyText.verifying(maxLength(100)),
    "qualite" -> nonEmptyText.verifying(maxLength(100)),
    "email" -> email.verifying(maxLength(200), nonEmpty),
    "helper" -> boolean,
    "instructor" -> boolean,
    "admin" -> boolean,
    "areas" -> list(uuid).verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
    "creationDate" -> ignored(DateTime.now(timeZone)),
    "hasAcceptedCharte" -> boolean,
    "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),
    "adminGroup" -> boolean,
    "disabled" -> boolean,
    "expert" -> ignored(false),
    "groupIds" -> default(list(uuid), List()),
    "delegations" -> seq(tuple(
      "name" -> nonEmptyText,
      "email" -> email
    )).transform[Map[String,String]]({ _.toMap }, { _.toSeq }),
    "cguAcceptationDate" -> optional(ignored(Time.now())),
    "newsletterAcceptationDate" -> optional(ignored(Time.now()))
  )(User.apply)(User.unapply)

  val userForm = Form(userMapping)

  def usersForm(implicit area: Area) = Form(
    single(
      "users" -> list(mapping(
        "id" -> optional(uuid).transform[UUID]({
          case None => UUID.randomUUID()
          case Some(id) => id
        }, { Some(_) }),
        "key" -> ignored("key"),
        "name" -> nonEmptyText.verifying(maxLength(100)),
        "qualite" -> nonEmptyText.verifying(maxLength(100)),
        "email" -> email.verifying(maxLength(200), nonEmpty),
        "helper" -> boolean,
        "instructor" -> boolean,
        "admin" -> ignored(false),
        "areas" -> ignored(List(area.id)),
        "creationDate" -> ignored(DateTime.now(timeZone)),
        "hasAcceptedCharte" -> ignored(false),
        "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),
        "adminGroup" -> ignored(false),
        "disabled" -> ignored(false),
        "expert" -> ignored(false),
        "groupIds" -> default(list(uuid), List()),
        "delegations" -> ignored(Map[String,String]()),
        "cguAcceptationDate" -> optional(ignored(Time.now())),
        "newsletterAcceptationDate" -> optional(ignored(Time.now()))
      )(User.apply)(User.unapply))
    )
  )

  def editUser(userId: UUID): Action[AnyContent] = loginAction { implicit request =>
    if (!request.currentUser.admin) {
      eventService.warn("VIEW_USER_UNAUTHORIZED", s"Accès non autorisé pour voir $userId")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      userService.byIdCheckDisabled(userId, true) match {
        case None =>
          eventService.error("USER_NOT_FOUND", s"L'utilisateur $userId n'existe pas")
          NotFound("Nous n'avons pas trouvé cet utilisateur")
        case Some(user) if user.canBeEditedBy(request.currentUser) =>
          val form = userForm.fill(user)
          val groups = userGroupService.allGroups
          val unused = isUserUnused(user)
          val Token(tokenName, tokenValue) = CSRF.getToken.get
          eventService.info("USER_SHOWED", s"Visualise la vue de modification l'utilisateur ", user = Some(user))
          Ok(views.html.editUser(request.currentUser, request.currentArea)(form, userId, groups, unused, tokenName = tokenName, tokenValue = tokenValue))
        case _ =>
          eventService.warn("VIEW_USER_UNAUTHORIZED", s"Accès non autorisé pour voir $userId")
          Unauthorized("Vous n'avez pas le droit de faire ça")
      }
    }
  }

  def isUserUnused(user: User): Boolean = {
    val applications = applicationService.allForUserId(userId = user.id, anonymous = false)
    applications.isEmpty
  }

  def deleteUnusedUserById(userId: UUID): Action[AnyContent] = loginAction { implicit request =>
    withUser(userId) { user: User =>
      asAdminOfUserZone(user) { () =>
        if (isUserUnused(user)) {
          userService.deleteById(userId)
          val path = "/" + controllers.routes.UserController.all().relativeTo("/")
          Redirect(path, 303)
        } else {
          Unauthorized("User is not unused.")
        }
      } { () =>
        "DELETE_USER_UNAUTHORIZED" -> s"Suppression de l'utilisateur $userId refusée."
      }
    }
  }

  def withUser(userId: UUID)(payload: User => Result)(implicit request: RequestWithUserData[AnyContent]): Result = {
    userService.byId(userId).fold({
      NotFound("Utilisateur inexistant.")
    })({ user: User =>
      payload(user)
    })
  }

  def asAdminOfUserZone(user: User)(payload: () => play.api.mvc.Result)(event: () => (String, String))(implicit request: RequestWithUserData[AnyContent]): play.api.mvc.Result = {
    if(request.currentUser.admin) {
      if(request.currentUser.areas.intersect(user.areas).nonEmpty) {
        payload()
      } else {
        Unauthorized("Vous n'êtes pas en charge de la zone de cet utilisateur.")
      }
    } else {
      val (code, description) = event()
      eventService.warn(code, description = description)
      Unauthorized("Vous n'avez pas le droit de faire ça")
    }
  }

  def editUserPost(userId: UUID) = loginAction { implicit request =>
    if(request.currentUser.admin != true) {
      eventService.warn("POST_EDIT_USER_UNAUTHORIZED", s"Accès non autorisé à modifier $userId")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      userForm.bindFromRequest.fold(
        formWithErrors => {
          val groups = userGroupService.allGroups
          eventService.error("ADD_USER_ERROR", s"Essai de modification de l'tilisateur $userId avec des erreurs de validation")
          BadRequest(views.html.editUser(request.currentUser, request.currentArea)(formWithErrors, userId, groups))
        },
        updatedUser => {
          val user = userService.byId(updatedUser.id).get
          if(!user.canBeEditedBy(request.currentUser)) {
            eventService.warn("POST_EDIT_USER_UNAUTHORIZED", s"Accès non autorisé à modifier $userId")
            Unauthorized("Vous n'avez pas le droit de faire ça")
          } else if(userService.update(updatedUser)) {
            eventService.info("EDIT_USER_DONE", s"Utilisateur $userId modifié", user = Some(updatedUser))
            Redirect(routes.UserController.all(Area.allArea.id)).flashing("success" -> "Utilisateur modifié")
          } else {
            val form = userForm.fill(updatedUser).withGlobalError("Impossible de mettre à jour l'utilisateur $userId (Erreur interne)")
            val groups = userGroupService.allGroups
            eventService.error("EDIT_USER_ERROR", s"Impossible de modifier l'utilisateur dans la BDD", user = Some(updatedUser))
            InternalServerError(views.html.editUser(request.currentUser, request.currentArea)(form, userId, groups))
          }
        }
      )
    }
  }

  def add(groupId: UUID) = loginAction { implicit request =>
    val group = userGroupService.groupById(groupId).get
    if(!group.canHaveUsersAddedBy(request.currentUser)) {
      eventService.warn("SHOW_ADD_USER_UNAUTHORIZED", s"Accès non autorisé à l'admin des utilisateurs du groupe $groupId")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      implicit val area = Area.fromId(group.area).get
      val rows = request.getQueryString("rows").map(_.toInt).getOrElse(1)
      eventService.info("EDIT_USER_SHOWED", s"Visualise la vue d'ajouts des utilisateurs")
      Ok(views.html.editUsers(request.currentUser, request.currentArea)(usersForm, rows, routes.UserController.addPost(groupId)))
    }
  }

  def addPost(groupId: UUID) = loginAction { implicit request =>
    val group = userGroupService.groupById(groupId).get
    if(!group.canHaveUsersAddedBy(request.currentUser)) {
      eventService.warn("POST_ADD_USER_UNAUTHORIZED", s"Accès non autorisé à l'admin des utilisateurs")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      val group = userGroupService.groupById(groupId).get
      implicit val area = Area.fromId(group.area).get
      usersForm.bindFromRequest.fold(
        formWithErrors => {
          eventService.error("ADD_USER_ERROR", s"Essai d'ajout d'utilisateurs avec des erreurs de validation")
          BadRequest(views.html.editUsers(request.currentUser, request.currentArea)(formWithErrors, 0, routes.UserController.addPost(groupId)))
        },
        users => {
          try {
            if (userService.add(users.map(_.copy(groupIds = List(groupId))))) {
              eventService.info("ADD_USER_DONE", s"Utilisateurs ajouté")
              Redirect(routes.UserController.editGroup(groupId)).flashing("success" -> "Utilisateurs ajouté")
            } else {
              val form = usersForm.fill(users).withGlobalError("Impossible d'ajouté les utilisateurs (Erreur interne 1)")
              eventService.error("ADD_USER_ERROR", s"Impossible d'ajouter des utilisateurs dans la BDD 1")
              InternalServerError(views.html.editUsers(request.currentUser, request.currentArea)(form, users.length, routes.UserController.addPost(groupId)))
            }
          } catch {
            case ex: PSQLException =>
              val EmailErrorPattern = """[^()@]+@[^()@.]+\.[^()@]+""".r // This didn't work in that case : """ Detail: Key \(email\)=\(([^()]*)\) already exists."""".r  (don't know why, the regex is correct)
              val errorMessage = EmailErrorPattern.findFirstIn(ex.getServerErrorMessage.toString) match {
                case Some(email) => s"Un utilisateur avec l'adresse $email existe déjà."
                case _ =>  "Erreur d'insertion dans la base de donnée : contacter l'administrateur."
              }
              val form = usersForm.fill(users).withGlobalError(errorMessage)
              eventService.error("ADD_USER_ERROR", s"Impossible d'ajouter des utilisateurs dans la BDD : ${ex.getServerErrorMessage}")
              BadRequest(views.html.editUsers(request.currentUser, request.currentArea)(form, users.length, routes.UserController.addPost(groupId)))
          }
        }
      )
    }
  }

  def showCGU() = loginAction { implicit request =>
    eventService.info("CGU_SHOWED", s"CGU visualisé")
    Ok(views.html.showCGU(request.currentUser, request.currentArea))
  }

  private val validateCGUForm = Form(
    tuple(
      "redirect" -> optional(text),
      "newsletter" -> boolean,
      "validate" -> boolean
    )
  )

  def validateCGU() = loginAction { implicit request =>
    validateCGUForm.bindFromRequest.fold(
      formWithErrors => {
        eventService.error("CGU_VALIDATION_ERROR", s"Erreur de formulaire dans la validation des CGU")
        BadRequest(s"Formulaire invalide, prévenez l'administrateur du service. ${formWithErrors.errors.mkString(", ")}")
      },
      {
        case (redirectOption, newsletter, validate) => {
          if (validate) {
            userService.acceptCGU(request.currentUser.id, newsletter)
          }
          eventService.info("CGU_VALIDATED", s"CGU validées")
          redirectOption match {
            case Some(redirect) =>
              Redirect(Call("GET", redirect)).flashing("success" -> "Merci d\'avoir accepté les CGU")
            case _ =>
              Redirect(routes.ApplicationController.my()).flashing("success" -> "Merci d\'avoir accepté les CGU")
          }
        }
      }
    )

  }

  def editGroup(id: UUID) = loginAction { implicit request =>
    userGroupService.groupById(id) match {
      case None =>
        eventService.error("EDIT_GROUPE_NOT_FOUND", s"La demande $id n'existe pas")
        NotFound("Nous n'avons pas trouvé ce groupe")
      case Some(group) =>
        if(!group.canHaveUsersAddedBy(request.currentUser)) {
          eventService.warn("EDIT_GROUPE_UNAUTHORIZED", s"Accès non autorisé à l'edition de ce groupe")
          Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?")
        } else {
          val groupUsers = userService.byGroupIds(List(id))
          eventService.info("EDIT_GROUP_SHOWED", s"Visualise la vue de modification du groupe")
          Ok(views.html.editGroup(request.currentUser, request.currentArea)(group, groupUsers))
        }
    }
  }

  def editGroupPost(id: UUID) = loginAction { implicit request =>
    userGroupService.groupById(id) match {
      case None =>
        eventService.error("EDIT_GROUPE_NOT_FOUND", s"La demande $id n'existe pas")
        NotFound("Nous n'avons pas trouvé ce groupe")
      case Some(group) =>
        if(request.currentUser.admin == false) {
          eventService.warn("EDIT_GROUPE_UNAUTHORIZED", s"Accès non autorisé à l'edition de ce groupe")
          Unauthorized("Vous ne pouvez pas éditer ce groupe : êtes-vous dans la bonne zone ?")
        } else {
          addGroupForm.bindFromRequest.fold(
            formWithErrors => {
              eventService.error("EDIT_USER_GROUP_ERROR", s"Essai d'edition d'un groupe avec des erreurs de validation")
              BadRequest("Impossible de modifier le groupe (erreur de formulaire)")
            },
            group => {
              if (userGroupService.edit(group.copy(id = id))) {
                eventService.info("EDIT_USER_GROUP_DONE", s"Groupe édité")
                Redirect(routes.UserController.editGroup(id)).flashing("success" -> "Groupe modifié")
              } else {eventService.error("EDIT_USER_GROUP_ERROR", s"Impossible de modifier le groupe dans la BDD")
                Redirect(routes.UserController.editGroup(id)).flashing("success" -> "Impossible de modifier le groupe")
              }
            }
          )
        }
    }
  }

  def addGroupForm[A](implicit request: RequestWithUserData[A]) = Form(
    mapping(
      "id" -> ignored(UUID.randomUUID()),
      "name" -> text,
      "insee-code" -> text,
      "creationDate" -> ignored(DateTime.now(timeZone)),
      "create-by-user-id" -> ignored(request.currentUser.id),
      "area" -> uuid.verifying("Vous devez sélectionner un territoire sur lequel vous êtes admin", area => request.currentUser.areas.contains(area)),
      "organisation" -> optional(text),
      "email" -> optional(email)
    )(UserGroup.apply)(UserGroup.unapply)
  )

  def addGroup = loginAction { implicit request =>
    if(request.currentUser.admin == false) {
      eventService.warn("ADD_GROUP_UNAUTHORIZED", s"Accès non autorisé pour ajouter un groupe")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      addGroupForm.bindFromRequest.fold(
        formWithErrors => {
          eventService.error("ADD_USER_GROUP_ERROR", s"Essai d'ajout d'un groupe avec des erreurs de validation")
          BadRequest("Impossible d'ajouter le groupe")//BadRequest(views.html.editUsers(request.currentUser, request.currentArea)(formWithErrors, 0, routes.UserController.addPost()))
        },
        group => {
          if (userGroupService.add(group)) {
            eventService.info("ADD_USER_GROUP_DONE", s"Groupe ajouté")
            Redirect(routes.UserController.editGroup(group.id)).flashing("success" -> "Groupe ajouté")
          } else {eventService.error("ADD_USER_GROUP_ERROR", s"Impossible d'ajouter le groupe dans la BDD")
            Redirect(routes.UserController.all(Area.allArea.id)).flashing("success" -> "Impossible d'ajouter le groupe")
          }
        }
      )
    }
  }

  def allEvents = loginAction { implicit request =>
    if (request.currentUser.admin == false) {
      eventService.warn("EVENTS_UNAUTHORIZED", s"Accès non autorisé pour voir les événements")
      Unauthorized("Vous n'avez pas le droit de faire ça")
    } else {
      val limit = request.getQueryString("limit").map(_.toInt).getOrElse(500)
      val userId = request.getQueryString("fromUserId").flatMap(UUIDHelper.fromString)
      val events = eventService.all(limit, userId)
      eventService.info("EVENTS_SHOWED", s"Affiche les événements")
      Ok(views.html.allEvents(request.currentUser, request.currentArea)(events, limit))
    }
  }
}
