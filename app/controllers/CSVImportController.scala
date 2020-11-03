package controllers

import java.time.ZonedDateTime
import java.util.UUID

import actions.LoginAction
import cats.syntax.all._
import controllers.Operators.{GroupOperators, UserOperators}
import helper.StringHelper._
import helper.Time
import javax.inject.Inject
import models.EventType.{
  CSVImportFormError,
  CsvImportInputEmpty,
  ImportGroupUnauthorized,
  ImportUserError,
  ImportUserFormError,
  ImportUserUnauthorized,
  ImportUsersUnauthorized,
  UserCreated,
  UserGroupCreated,
  UsersImported
}
import models.User.AccountType.{Nominative, Shared}
import models.form.ImportUserForm
import models.form.UserGroupForm.groupImportMapping
import models.formModels.{CSVImportData, UserFormData, UserGroupFormData}
import models.{Area, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, InjectedController}
import serializers.UserAndGroupCsvSerializer
import services.{EventService, NotificationService, UserGroupService, UserService}

import scala.concurrent.{ExecutionContext, Future}

case class CSVImportController @Inject() (
    loginAction: LoginAction,
    userService: UserService,
    groupService: UserGroupService,
    notificationsService: NotificationService,
    eventService: EventService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends InjectedController
    with play.api.i18n.I18nSupport
    with UserOperators
    with GroupOperators {

  private val csvImportContentForm: Form[CSVImportData] = Form(
    mapping(
      "csv-lines" -> nonEmptyText,
      "area-default-ids" -> list(uuid),
      "separator" -> char
        .verifying("Séparateur incorrect", value => value === ';' || value === ',')
    )(CSVImportData.apply)(CSVImportData.unapply)
  )

  def importUsersFromCSV: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() => ImportUserUnauthorized -> "Accès non autorisé pour importer les utilisateurs") {
        () =>
          Future(
            Ok(views.html.importUsersCSV(request.currentUser, request.rights)(csvImportContentForm))
          )
      }
    }

  /** Checks with the DB if Users or UserGroups already exist. */
  private def augmentUserGroupInformation(
      userGroupFormData: UserGroupFormData,
      multiGroupUserEmails: Set[String]
  ): UserGroupFormData = {
    val userEmails = userGroupFormData.users.map(_.user.email)
    val alreadyExistingUsers = userService.byEmails(userEmails)
    val newUsersFormDataList = userGroupFormData.users.map { userDataForm =>
      alreadyExistingUsers
        .find(_.email.stripSpecialChars === userDataForm.user.email.stripSpecialChars)
        .fold {
          userDataForm.copy(
            isInMoreThanOneGroup = Some(multiGroupUserEmails.contains(userDataForm.user.email))
          )
        } { alreadyExistingUser =>
          userDataForm.copy(
            user = userDataForm.user.copy(id = alreadyExistingUser.id),
            alreadyExistingUser = Some(alreadyExistingUser),
            alreadyExists = true
          )
        }
    }
    val withGroup = groupService
      .groupByName(userGroupFormData.group.name)
      .fold {
        userGroupFormData
      } { alreadyExistingGroup =>
        userGroupFormData.copy(
          group = userGroupFormData.group.copy(id = alreadyExistingGroup.id),
          alreadyExistingGroup = Some(alreadyExistingGroup)
        )
      }
    withGroup.copy(
      users = newUsersFormDataList,
      alreadyExistsOrAllUsersAlreadyExist = withGroup.alreadyExistingGroup.nonEmpty ||
        newUsersFormDataList.forall(_.alreadyExists)
    )
  }

  private def augmentUserGroupsInformation(
      groups: List[UserGroupFormData]
  ): List[UserGroupFormData] = {
    val multiGroupUserEmails = groups
      .filterNot(_.doNotInsert)
      .flatMap(group => group.users.map(user => (group.group.id, user.user.email)))
      .groupBy { case (_, userEmail) => userEmail }
      .collect { case (userEmail, groups) if groups.size > 1 => userEmail }
      .toSet
    groups.map(group => augmentUserGroupInformation(group, multiGroupUserEmails))
  }

  private def importUsersAfterReviewForm(date: ZonedDateTime): Form[List[UserGroupFormData]] =
    Form(
      single(
        "groups" -> list(
          mapping(
            "group" -> groupImportMapping(date),
            "users" -> list(
              mapping(
                "user" -> ImportUserForm.userImportMapping(date),
                "line" -> number,
                "alreadyExists" -> boolean,
                "alreadyExistingUser" -> ignored(Option.empty[User]),
                "isInMoreThanOneGroup" -> optional(boolean)
              )(UserFormData.apply)(UserFormData.unapply)
            ),
            "alreadyExistsOrAllUsersAlreadyExist" -> boolean,
            "doNotInsert" -> boolean,
            "alreadyExistingGroup" -> ignored(Option.empty[UserGroup])
          )(UserGroupFormData.apply)(UserGroupFormData.unapply)
        )
      )
    )

  /** Action that reads the CSV file (CSV file was copy-paste in a web form)
    *  and display possible errors.
    */
  def importUsersReview: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin { () =>
        ImportGroupUnauthorized -> "Accès non autorisé pour importer les utilisateurs"
      } { () =>
        csvImportContentForm
          .bindFromRequest()
          .fold(
            { csvImportContentFormWithError =>
              eventService.log(
                CsvImportInputEmpty,
                description = "Le champ d'import de CSV est vide ou le séparateur n'est pas défini."
              )
              Future(
                BadRequest(
                  views.html.importUsersCSV(request.currentUser, request.rights)(
                    csvImportContentFormWithError
                  )
                )
              )
            },
            { csvImportData =>
              val defaultAreas = csvImportData.areaIds.flatMap(Area.fromId)
              UserAndGroupCsvSerializer
                .csvLinesToUserGroupData(csvImportData.separator, defaultAreas, Time.nowParis())(
                  csvImportData.csvLines
                )
                .fold(
                  { error: String =>
                    val csvImportContentFormWithError =
                      csvImportContentForm.fill(csvImportData).withGlobalError(error)
                    eventService
                      .log(CSVImportFormError, description = "Erreur de formulaire Importation")
                    Future(
                      BadRequest(
                        views.html.importUsersCSV(request.currentUser, request.rights)(
                          csvImportContentFormWithError
                        )
                      )
                    )
                  },
                  {
                    case (
                          userNotImported: List[String],
                          userGroupDataForm: List[UserGroupFormData]
                        ) =>
                      val augmentedUserGroupInformation: List[UserGroupFormData] =
                        augmentUserGroupsInformation(userGroupDataForm)

                      val currentDate = Time.nowParis()
                      val formWithData = importUsersAfterReviewForm(currentDate)
                        .fillAndValidate(augmentedUserGroupInformation)

                      val formWithError = if (userNotImported.nonEmpty) {
                        formWithData.withGlobalError(
                          "Certaines lignes du CSV n'ont pas pu être importé",
                          userNotImported: _*
                        )
                      } else {
                        formWithData
                      }
                      Future(
                        Ok(
                          views.html
                            .reviewUsersImport(request.currentUser, request.rights)(formWithError)
                        )
                      )
                  }
                )
            }
          )
      }
    }

  private def associateGroupToUsers(groupFormData: UserGroupFormData): UserGroupFormData = {
    val groupId = groupFormData.group.id
    val areasId = groupFormData.group.areaIds
    val newUsers = groupFormData.users.map({ userFormData =>
      val newUser = userFormData.user.copy(
        groupIds = (groupId :: userFormData.user.groupIds).distinct,
        areas = (areasId ++ userFormData.user.areas).distinct
      )
      userFormData.copy(user = newUser)
    })
    groupFormData.copy(users = newUsers)
  }

  /** Import the reviewed CSV. */
  def importUsersAfterReview: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(() =>
        ImportUsersUnauthorized -> "Accès non autorisé pour importer les utilisateurs"
      ) { () =>
        val currentDate = Time.nowParis()
        importUsersAfterReviewForm(currentDate)
          .bindFromRequest()
          .fold(
            { importUsersAfterReviewFormWithError =>
              eventService.log(ImportUserFormError, description = "Erreur de formulaire de review")
              Future(
                BadRequest(
                  views.html.reviewUsersImport(request.currentUser, request.rights)(
                    importUsersAfterReviewFormWithError
                  )
                )
              )
            },
            { userGroupDataForm: List[UserGroupFormData] =>
              val augmentedUserGroupInformation: List[UserGroupFormData] =
                augmentUserGroupsInformation(userGroupDataForm)

              val groupsToInsert = augmentedUserGroupInformation
                .filterNot(_.doNotInsert)
                .filterNot(_.alreadyExistsOrAllUsersAlreadyExist)
                .filter(_.alreadyExistingGroup.isEmpty)
                .map(_.group)
              groupService
                .add(groupsToInsert)
                .fold(
                  { error: String =>
                    val description = s"Impossible d'importer les groupes : $error"
                    eventService.log(ImportUserError, description)
                    val formWithError = importUsersAfterReviewForm(currentDate)
                      .fill(augmentedUserGroupInformation)
                      .withGlobalError(description)
                    Future(
                      InternalServerError(
                        views.html
                          .reviewUsersImport(request.currentUser, request.rights)(formWithError)
                      )
                    )
                  },
                  { _ =>
                    groupsToInsert.foreach { userGroup =>
                      eventService.log(UserGroupCreated, s"Groupe ${userGroup.id} ajouté")
                    }
                    val usersToInsert: List[User] = augmentedUserGroupInformation
                      .filterNot(_.doNotInsert)
                      .map(associateGroupToUsers)
                      .flatMap(_.users)
                      .filter(_.alreadyExistingUser.isEmpty)
                      .map(_.user)
                      .map {
                        case user if user.name.nonEmpty =>
                          user.copy(accountType = Shared(user.name))
                        case user => user.copy(accountType = Nominative.newAccount)
                      }
                      // Here we will group users by email, so we can put them in multiple groups
                      .groupBy(_.email)
                      .map { case (_, entitiesWithSameEmail) =>
                        // Note: users appear in the same order as given in the import
                        // Safe due to groupBy
                        val repr: User = entitiesWithSameEmail.head
                        val groupIds: List[UUID] = entitiesWithSameEmail.flatMap(_.groupIds)
                        val areas: List[UUID] = entitiesWithSameEmail.flatMap(_.areas)
                        repr.copy(
                          areas = areas,
                          groupIds = groupIds
                        )
                      }
                      .toList

                    userService
                      .add(usersToInsert)
                      .fold(
                        { error: String =>
                          val description = s"Impossible d'importer les utilisateurs : $error"
                          eventService.log(ImportUserError, description)
                          val formWithError = importUsersAfterReviewForm(currentDate)
                            .fill(augmentedUserGroupInformation)
                            .withGlobalError(description)
                          Future(
                            InternalServerError(
                              views.html
                                .reviewUsersImport(request.currentUser, request.rights)(
                                  formWithError
                                )
                            )
                          )
                        },
                        { _ =>
                          usersToInsert.foreach { user =>
                            notificationsService.newUser(user)
                            eventService.log(
                              UserCreated,
                              s"Ajout de l'utilisateur ${user.name} ${user.email}",
                              involvesUser = Some(user)
                            )
                          }
                          eventService
                            .log(UsersImported, "Utilisateurs ajoutés par l'importation")
                          Future(
                            Redirect(routes.UserController.all(Area.allArea.id))
                              .flashing("success" -> "Utilisateurs importés.")
                          )
                        }
                      )
                  }
                )
            }
          )
      }
    }

}
