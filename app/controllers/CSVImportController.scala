package controllers

import java.util.UUID

import actions.LoginAction
import Operators.{GroupOperators, UserOperators}
import models.formModels.{CSVImportData, UserFormData, UserGroupFormData}
import javax.inject.Inject
import models.{Area, Organisation, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, InjectedController}
import services.{EventService, NotificationService, UserGroupService, UserService}
import org.joda.time.DateTime
import helper.Time
import helper.StringHelper._
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
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import serializers.UserAndGroupCsvSerializer

case class CSVImportController @Inject() (
    loginAction: LoginAction,
    userService: UserService,
    groupService: UserGroupService,
    notificationsService: NotificationService,
    eventService: EventService
)(implicit val webJarsUtil: WebJarsUtil)
    extends InjectedController
    with play.api.i18n.I18nSupport
    with UserOperators
    with GroupOperators {

  private val csvImportContentForm: Form[CSVImportData] = Form(
    mapping(
      "csv-lines" -> nonEmptyText,
      "area-default-ids" -> list(uuid),
      "separator" -> char
        .verifying("Séparateur incorrect", (value: Char) => (value == ';' || value == ','))
    )(CSVImportData.apply)(CSVImportData.unapply)
  )

  def importUsersFromCSV: Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      ImportUserUnauthorized -> "Accès non autorisé pour importer les utilisateurs"
    } { () =>
      Ok(views.html.importUsersCSV(request.currentUser, request.rights)(csvImportContentForm))
    }
  }

  /** Checks with the DB if Users or UserGroups already exist. */
  private def augmentUserGroupInformation(
      userGroupFormData: UserGroupFormData
  ): UserGroupFormData = {
    val userEmails = userGroupFormData.users.map(_.user.email)
    val alreadyExistingUsers = userService.byEmails(userEmails)
    val newUsersFormDataList = userGroupFormData.users.map { userDataForm =>
      alreadyExistingUsers
        .find(_.email.stripSpecialChars == userDataForm.user.email.stripSpecialChars)
        .fold {
          userDataForm
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
      alreadyExistsOrAllUsersAlreadyExist =
        withGroup.alreadyExistingGroup.nonEmpty ||
          newUsersFormDataList.forall(_.alreadyExists)
    )
  }

  private def userImportMapping(date: DateTime): Mapping[User] =
    mapping(
      "id" -> optional(uuid).transform[UUID]({
        case None     => UUID.randomUUID()
        case Some(id) => id
      }, {
        Some(_)
      }),
      "key" -> ignored("key"),
      "name" -> nonEmptyText.verifying(maxLength(100)),
      "quality" -> default(text, ""),
      "email" -> email.verifying(maxLength(200), nonEmpty),
      "helper" -> ignored(true),
      "instructor" -> boolean,
      "admin" -> ignored(false),
      "areas" -> list(uuid),
      "creationDate" -> ignored(date),
      "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),
      "adminGroup" -> boolean,
      "disabled" -> boolean,
      "expert" -> ignored(false),
      "groupIds" -> default(list(uuid), List()),
      "cguAcceptationDate" -> ignored(Option.empty[DateTime]),
      "newsletterAcceptationDate" -> ignored(Option.empty[DateTime]),
      "phone-number" -> optional(text),
      // TODO: put also in forms/imports?
      "observableOrganisationIds" -> list(of[Organisation.Id])
    )(User.apply)(User.unapply)

  private def groupImportMapping(date: DateTime): Mapping[UserGroup] =
    mapping(
      "id" -> optional(uuid).transform[UUID]({
        case None     => UUID.randomUUID()
        case Some(id) => id
      }, {
        Some(_)
      }),
      "name" -> text(maxLength = 60),
      "description" -> optional(text),
      "insee-code" -> list(text),
      "creationDate" -> ignored(date),
      "area-ids" -> list(uuid)
        .verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
      "organisation" -> optional(of[Organisation.Id]).verifying(
        "Vous devez sélectionner une organisation dans la liste",
        _.map(Organisation.isValidId).getOrElse(false)
      ),
      "email" -> optional(email)
    )(UserGroup.apply)(UserGroup.unapply)

  private def importUsersAfterReviewForm(date: DateTime): Form[List[UserGroupFormData]] = Form(
    single(
      "groups" -> list(
        mapping(
          "group" -> groupImportMapping(date),
          "users" -> list(
            mapping(
              "user" -> userImportMapping(date),
              "line" -> number,
              "alreadyExists" -> boolean,
              "alreadyExistingUser" -> ignored(Option.empty[User])
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
    loginAction { implicit request =>
      asAdmin { () =>
        ImportGroupUnauthorized -> "Accès non autorisé pour importer les utilisateurs"
      } { () =>
        csvImportContentForm.bindFromRequest.fold(
          { csvImportContentFormWithError =>
            eventService.log(
              CsvImportInputEmpty,
              description = "Le champ d'import de CSV est vide ou le séparateur n'est pas défini."
            )
            BadRequest(
              views.html.importUsersCSV(request.currentUser, request.rights)(
                csvImportContentFormWithError
              )
            )
          }, { csvImportData =>
            val defaultAreas = csvImportData.areaIds.flatMap(Area.fromId)
            UserAndGroupCsvSerializer
              .csvLinesToUserGroupData(csvImportData.separator, defaultAreas, Time.now())(
                csvImportData.csvLines
              )
              .fold(
                { error: String =>
                  val csvImportContentFormWithError =
                    csvImportContentForm.fill(csvImportData).withGlobalError(error)
                  eventService
                    .log(CSVImportFormError, description = "Erreur de formulaire Importation")
                  BadRequest(
                    views.html.importUsersCSV(request.currentUser, request.rights)(
                      csvImportContentFormWithError
                    )
                  )
                }, {
                  case (
                      userNotImported: List[String],
                      userGroupDataForm: List[UserGroupFormData]
                      ) =>
                    val augmentedUserGroupInformation: List[UserGroupFormData] =
                      userGroupDataForm.map(augmentUserGroupInformation)

                    val currentDate = Time.now()
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
                    Ok(
                      views.html
                        .reviewUsersImport(request.currentUser, request.rights)(formWithError)
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
  def importUsersAfterReview: Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      ImportUsersUnauthorized -> "Accès non autorisé pour importer les utilisateurs"
    } { () =>
      val currentDate = Time.now()
      importUsersAfterReviewForm(currentDate).bindFromRequest.fold(
        { importUsersAfterReviewFormWithError =>
          eventService.log(ImportUserFormError, description = "Erreur de formulaire de review")
          BadRequest(
            views.html.reviewUsersImport(request.currentUser, request.rights)(
              importUsersAfterReviewFormWithError
            )
          )
        }, { userGroupDataForm: List[UserGroupFormData] =>
          val augmentedUserGroupInformation: List[UserGroupFormData] =
            userGroupDataForm.map(augmentUserGroupInformation)

          val groupsToInsert = augmentedUserGroupInformation
            .filterNot(_.doNotInsert)
            .filterNot(_.alreadyExistsOrAllUsersAlreadyExist)
            .filter(_.alreadyExistingGroup.isEmpty)
            .map(_.group)
          groupService
            .add(groupsToInsert)
            .fold(
              {
                error: String =>
                  val description = s"Impossible d'importer les groupes : $error"
                  eventService.log(ImportUserError, description)
                  val formWithError = importUsersAfterReviewForm(currentDate)
                    .fill(augmentedUserGroupInformation)
                    .withGlobalError(description)
                  InternalServerError(
                    views.html.reviewUsersImport(request.currentUser, request.rights)(formWithError)
                  )
              }, {
                _ =>
                  groupsToInsert.foreach { userGroup =>
                    eventService.log(UserGroupCreated, s"Groupe ${userGroup.id} ajouté")
                  }
                  val usersToInsert: List[User] = augmentedUserGroupInformation
                    .filterNot(_.doNotInsert)
                    .map(associateGroupToUsers)
                    .flatMap(_.users)
                    .filter(_.alreadyExistingUser.isEmpty)
                    .map(_.user)

                  userService
                    .add(usersToInsert)
                    .fold(
                      {
                        error: String =>
                          val description = s"Impossible d'importer les utilisateurs : $error"
                          eventService.log(ImportUserError, description)
                          val formWithError = importUsersAfterReviewForm(currentDate)
                            .fill(augmentedUserGroupInformation)
                            .withGlobalError(description)
                          InternalServerError(
                            views.html
                              .reviewUsersImport(request.currentUser, request.rights)(formWithError)
                          )
                      }, {
                        _ =>
                          usersToInsert.foreach { user =>
                            notificationsService.newUser(user)
                            eventService.log(
                              UserCreated,
                              s"Ajout de l'utilisateur ${user.name} ${user.email}",
                              user = Some(user)
                            )
                          }
                          eventService.log(UsersImported, "Utilisateurs ajoutés par l'importation")
                          Redirect(routes.UserController.all(request.currentArea.id))
                            .flashing("success" -> "Utilisateurs importés.")
                      }
                    )
              }
            )
        }
      )
    }
  }
}
