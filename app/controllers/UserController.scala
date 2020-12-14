package controllers

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import java.util.UUID

import actions.{LoginAction, RequestWithUserData}
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
import models.formModels.{EditProfileFormData, ValidateSubscriptionForm}
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
    with play.api.i18n.I18nSupport
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
          val message =
            s"Impossible de visualiser la modification de profil : ${exception.getMessage}"
          eventService.log(UserProfileShowedError, message)
          Future.successful(InternalServerError(views.html.welcome(user, request.rights)))
        }
    }

  def editProfile =
    loginAction.async { implicit request =>
      val user = request.currentUser
      if (user.sharedAccount) {
        eventService.log(UserProfileUpdatedError, "Impossible de modifier un profil partagé")
        successful(BadRequest(views.html.welcome(user, request.rights)))
      } else
        EditProfileFormData.form
          .bindFromRequest()
          .fold(
            errors => {
              eventService.log(UserProfileUpdatedError, "Erreur lors de la modification du profil")
              groupService
                .byIdsFuture(user.groupIds)
                .map(groups =>
                  BadRequest(
                    views.html.editProfile(user, request.rights)(errors, user.email, groups)
                  )
                )
            },
            success => {
              import success._
              val edited =
                userService.editProfile(user.id)(firstName, lastName, qualite, phoneNumber.orEmpty)
              successful(edited)
                .map { _ =>
                  val message = "Votre profil a bien été modifié"
                  eventService.log(UserProfileUpdated, message)
                  Redirect(routes.UserController.editProfile()).flashing("success" -> message)
                }
                .recover { e =>
                  val errorMessage = s"Erreur lors de la modification du profil: ${e.getMessage}"
                  eventService.log(UserProfileUpdatedError, errorMessage)
                  InternalServerError(views.html.welcome(user, request.rights))
                }
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
      asUserWithAuthorization(Authorization.isAdminOrObserver) { () =>
        ViewUserUnauthorized -> s"Accès non autorisé pour voir $userId"
      } { () =>
        userService.byId(userId, includeDisabled = true) match {
          case None =>
            eventService.log(UserNotFound, s"L'utilisateur $userId n'existe pas")
            Future(NotFound("Nous n'avons pas trouvé cet utilisateur"))
          case Some(user) if Authorization.canSeeOtherUser(user)(request.rights) =>
            val form = userForm(Time.timeZoneParis).fill(user)
            val groups = groupService.allGroups
            val unused = not(isAccountUsed(user))
            val Token(tokenName, tokenValue) = CSRF.getToken.get
            eventService
              .log(
                UserShowed,
                "Visualise la vue de modification l'utilisateur ",
                involvesUser = Some(user)
              )
            Future(
              Ok(
                views.html.editUser(request.currentUser, request.rights)(
                  form,
                  userId,
                  groups,
                  unused,
                  tokenName = tokenName,
                  tokenValue = tokenValue
                )
              )
            )
          case _ =>
            eventService.log(ViewUserUnauthorized, s"Accès non autorisé pour voir $userId")
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
            eventService.log(UserIsUsed, description = s"Le compte ${user.id} est utilisé.")
            Future(Unauthorized("User is not unused."))
          } else {
            userService.deleteById(userId)
            val message = s"Utilisateur $userId / ${user.email} a été supprimé"
            eventService.log(UserDeleted, message, involvesUser = Some(user))
            Future(
              Redirect(routes.UserController.home()).flashing("success" -> message)
            )
          }
        }
      }
    }

  def editUserPost(userId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() => PostEditUserUnauthorized -> s"Accès non autorisé à modifier $userId") { () =>
        userForm(Time.timeZoneParis)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val groups = groupService.allGroups
              eventService.log(
                AddUserError,
                s"Essai de modification de l'utilisateur $userId avec des erreurs de validation"
              )
              Future(
                BadRequest(
                  views.html
                    .editUser(request.currentUser, request.rights)(formWithErrors, userId, groups)
                )
              )
            },
            updatedUser =>
              withUser(updatedUser.id, includeDisabled = true) { user: User =>
                // Ensure that user include all areas of his group
                val groups = groupService.byIds(updatedUser.groupIds)
                val areaIds = (updatedUser.areas ++ groups.flatMap(_.areaIds)).distinct
                val userToUpdate = updatedUser.copy(
                  areas = areaIds.intersect(request.currentUser.areas)
                ) // intersect is a safe gard (In case an Admin try to manage an authorized area)
                val rights = request.rights

                if (not(Authorization.canEditOtherUser(user)(rights))) {
                  eventService
                    .log(PostEditUserUnauthorized, s"Accès non autorisé à modifier $userId")
                  Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
                } else {
                  userService.update(userToUpdate).map { updateHasBeenDone =>
                    if (updateHasBeenDone) {
                      eventService
                        .log(
                          UserEdited,
                          s"Utilisateur $userId modifié",
                          involvesUser = Some(updatedUser)
                        )
                      Redirect(routes.UserController.editUser(userId))
                        .flashing("success" -> "Utilisateur modifié")
                    } else {
                      val form: Form[User] = userForm(Time.timeZoneParis)
                        .fill(userToUpdate)
                        .withGlobalError(
                          s"Impossible de mettre à jour l'utilisateur $userId (Erreur interne)"
                        )
                      val groups = groupService.allGroups
                      eventService.log(
                        EditUserError,
                        "Impossible de modifier l'utilisateur dans la BDD",
                        involvesUser = Some(updatedUser)
                      )
                      InternalServerError(
                        views.html
                          .editUser(request.currentUser, request.rights)(form, userId, groups)
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
        if (!group.canHaveUsersAddedBy(request.currentUser)) {
          eventService.log(PostAddUserUnauthorized, "Accès non autorisé à l'admin des utilisateurs")
          Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
        } else {
          usersForm(Time.timeZoneParis, group.areaIds)
            .bindFromRequest()
            .fold(
              { formWithErrors =>
                eventService
                  .log(AddUserError, "Essai d'ajout d'utilisateurs avec des erreurs de validation")
                Future(
                  BadRequest(
                    views.html.editUsers(request.currentUser, request.rights)(
                      formWithErrors,
                      0,
                      routes.UserController.addPost(groupId)
                    )
                  )
                )
              },
              users =>
                try {
                  val userToInsert = users.map(_.copy(groupIds = List(groupId)))
                  userService
                    .add(userToInsert)
                    .fold(
                      { error =>
                        val errorMessage =
                          s"Impossible d'ajouter les utilisateurs. $error"
                        eventService.log(AddUserError, errorMessage)
                        val form = usersForm(Time.timeZoneParis, group.areaIds)
                          .fill(users)
                          .withGlobalError(errorMessage)
                        Future(
                          InternalServerError(
                            views.html.editUsers(request.currentUser, request.rights)(
                              form,
                              users.length,
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
                            s"Ajout de l'utilisateur ${user.name} ${user.email}",
                            involvesUser = Some(user)
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
                    val form = usersForm(Time.timeZoneParis, group.areaIds)
                      .fill(users)
                      .withGlobalError(errorMessage)
                    eventService.log(
                      AddUserError,
                      s"Impossible d'ajouter des utilisateurs dans la BDD : ${ex.getServerErrorMessage}"
                    )
                    Future(
                      BadRequest(
                        views.html.editUsers(request.currentUser, request.rights)(
                          form,
                          users.length,
                          routes.UserController.addPost(groupId)
                        )
                      )
                    )
                }
            )
        }
      }
    }

  def showValidateAccount(): Action[AnyContent] =
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
  ): Int = {
    userService.update(
      user.validateWith(
        firstName.map(commonStringInputNormalization).map(capitalizeName),
        lastName.map(commonStringInputNormalization).map(capitalizeName),
        qualite.map(commonStringInputNormalization),
        phoneNumber.map(commonStringInputNormalization)
      )
    )
    userService.validateCGU(user.id)
  }

  def validateAccount(): Action[AnyContent] =
    loginAction { implicit request =>
      val user = request.currentUser
      ValidateSubscriptionForm
        .validate(user)
        .bindFromRequest()
        .fold(
          { formWithErrors =>
            eventService.log(CGUValidationError, "Erreur de formulaire dans la validation des CGU")
            BadRequest(views.html.validateAccount(user, request.rights, formWithErrors))
          },
          {
            case ValidateSubscriptionForm(
                  Some(redirect),
                  true,
                  firstName,
                  lastName,
                  qualite,
                  phoneNumber
                ) if redirect =!= routes.ApplicationController.myApplications().url =>
              validateAndUpdateUser(request.currentUser)(firstName, lastName, qualite, phoneNumber)
              eventService.log(CGUValidated, "CGU validées")
              Redirect(Call("GET", redirect)).flashing("success" -> "Merci d’avoir accepté les CGU")
            case ValidateSubscriptionForm(_, true, firstName, lastName, qualite, phoneNumber) =>
              validateAndUpdateUser(request.currentUser)(firstName, lastName, qualite, phoneNumber)
              eventService.log(CGUValidated, "CGU validées")
              Redirect(routes.HomeController.welcome())
                .flashing("success" -> "Merci d’avoir accepté les CGU")
            case ValidateSubscriptionForm(Some(redirect), false, _, _, _, _)
                if redirect =!= routes.ApplicationController.myApplications().url =>
              Redirect(Call("GET", redirect))
            case ValidateSubscriptionForm(_, false, _, _, _, _) =>
              Redirect(routes.HomeController.welcome())
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
            Redirect(routes.HomeController.welcome())
              .flashing("success" -> "Merci d’avoir terminé votre inscription")
          }
        )
    }

  def add(groupId: UUID): Action[AnyContent] =
    loginAction.async { implicit request =>
      withGroup(groupId) { group: UserGroup =>
        if (!group.canHaveUsersAddedBy(request.currentUser)) {
          eventService.log(
            ShowAddUserUnauthorized,
            s"Accès non autorisé à l'admin des utilisateurs du groupe $groupId"
          )
          Future(Unauthorized("Vous n'avez pas le droit de faire ça"))
        } else {
          val rows =
            request
              .getQueryString(Keys.QueryParam.rows)
              .flatMap(rows => Try(rows.toInt).toOption)
              .getOrElse(1)
          eventService.log(EditUserShowed, "Visualise la vue d'ajouts des utilisateurs")
          Future(
            Ok(
              views.html.editUsers(request.currentUser, request.rights)(
                usersForm(Time.timeZoneParis, group.areaIds),
                rows,
                routes.UserController.addPost(groupId)
              )
            )
          )
        }
      }
    }

  def allEvents(): Action[AnyContent] =
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

  def usersForm(timeZone: ZoneId, areaIds: List[UUID]): Form[List[User]] =
    Form(
      single(
        "users" -> list(
          mapping(
            "id" -> optional(uuid).transform[UUID](
              {
                case None     => UUID.randomUUID()
                case Some(id) => id
              },
              Some(_)
            ),
            "key" -> ignored("key"),
            "firstName" -> optional(text.verifying(maxLength(100))),
            "lastName" -> optional(text.verifying(maxLength(100))),
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
            "observableOrganisationIds" -> list(
              of[Organisation.Id].verifying(
                "Organisation non reconnue",
                organisationId =>
                  Organisation.all
                    .exists(organisation => organisation.id === organisationId)
              )
            ),
            Keys.User.sharedAccount -> boolean
          )(User.apply)(User.unapply)
        )
      )
    )

  def userForm(timeZone: ZoneId): Form[User] = Form(userMapping(timeZone))

  private def userMapping(implicit timeZone: ZoneId): Mapping[User] =
    mapping(
      "id" -> optional(uuid).transform[UUID](
        {
          case None     => UUID.randomUUID()
          case Some(id) => id
        },
        Option.apply
      ),
      "key" -> ignored("key"),
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
      "observableOrganisationIds" -> list(of[Organisation.Id]),
      Keys.User.sharedAccount -> boolean
    )(User.apply)(User.unapply)

}
