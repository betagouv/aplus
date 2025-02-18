package controllers

import actions.LoginAction
import cats.syntax.all._
import controllers.Operators.{GroupOperators, UserOperators}
import helper.MiscHelpers.toTupleOpt
import helper.PlayFormHelpers.{inOption, normalizedOptionalText, normalizedText}
import helper.StringHelper._
import helper.Time
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import models.{Area, Organisation, User, UserGroup}
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
import models.forms.{
  CSVRawLinesFormData,
  CSVReviewUserFormData,
  CSVUserFormData,
  CSVUserGroupFormData
}
import modules.AppConfig
import org.webjars.play.WebJarsUtil
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import scala.concurrent.{ExecutionContext, Future}
import serializers.UserAndGroupCsvSerializer
import serializers.UserAndGroupCsvSerializer.UserGroupBlock
import services.{EventService, NotificationService, UserGroupService, UserService}

case class CSVImportController @Inject() (
    config: AppConfig,
    val controllerComponents: ControllerComponents,
    loginAction: LoginAction,
    userService: UserService,
    groupService: UserGroupService,
    notificationsService: NotificationService,
    eventService: EventService
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends BaseController
    with I18nSupport
    with Operators.Common
    with UserOperators
    with GroupOperators {

  def importUsersFromCSV: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(ImportUserUnauthorized, "Accès non autorisé pour importer les utilisateurs") { () =>
        Future(
          Ok(
            views.html.importUsersCSV(request.currentUser, request.rights)(
              CSVRawLinesFormData.contentForm
            )
          )
        )
      }
    }

  /** Checks with the DB if Users or UserGroups already exist. */
  private def augmentUserGroupInformation(
      userGroupFormData: CSVUserGroupFormData,
      multiGroupUserEmails: Set[String]
  ): CSVUserGroupFormData = {
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
      groups: List[CSVUserGroupFormData]
  ): List[CSVUserGroupFormData] = {
    val multiGroupUserEmails = groups
      .filterNot(_.doNotInsert)
      .flatMap(group => group.users.map(user => (group.group.id, user.user.email)))
      .groupBy { case (_, userEmail) => userEmail }
      .collect { case (userEmail, groups) if groups.size > 1 => userEmail }
      .toSet
    groups.map(group => augmentUserGroupInformation(group, multiGroupUserEmails))
  }

  // This function was written to wrap legacy code. Signature should be more or less OK.
  private def csvImportDataToReviewFormData(
      groups: List[UserGroupBlock]
  ): List[CSVUserGroupFormData] = {
    val tmpGroups = groups.map(group =>
      CSVUserGroupFormData(
        group = UserGroup(
          id = UUID.randomUUID(),
          name = group.group.name,
          description = group.group.description,
          inseeCode = Nil,
          creationDate = Time.nowParis(),
          areaIds = group.group.areaIds,
          organisationId = group.group.organisationId,
          email = group.group.email,
          isInFranceServicesNetwork = true,
          publicNote = None,
          internalSupportComment = None
        ),
        users = group.users.map(user =>
          CSVUserFormData(
            user = CSVReviewUserFormData(
              id = UUID.randomUUID(),
              firstName = user.userData.firstName,
              lastName = user.userData.lastName,
              name = user.userData.name,
              email = user.userData.email,
              instructor = user.userData.instructor,
              groupAdmin = user.userData.groupAdmin,
              phoneNumber = user.userData.phoneNumber
            ),
            line = user.line,
            alreadyExists = false,
            alreadyExistingUser = None,
            isInMoreThanOneGroup = None
          )
        ),
        alreadyExistsOrAllUsersAlreadyExist = false,
        doNotInsert = false,
        alreadyExistingGroup = None
      )
    )
    augmentUserGroupsInformation(tmpGroups)
  }

  private val userImportMapping: Mapping[CSVReviewUserFormData] =
    mapping(
      "id" -> uuid,
      "firstName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
      "lastName" -> normalizedOptionalText.verifying(inOption(maxLength(100))),
      "name" -> normalizedText.verifying(maxLength(500)),
      "email" -> email.verifying(maxLength(200), nonEmpty),
      "instructor" -> boolean,
      "groupAdmin" -> boolean,
      "phoneNumber" -> normalizedOptionalText
    )(CSVReviewUserFormData.apply)(toTupleOpt)

  private def groupImportMapping(date: ZonedDateTime): Mapping[UserGroup] =
    mapping(
      "id" -> optional(uuid).transform[UUID](
        {
          case None     => UUID.randomUUID()
          case Some(id) => id
        },
        Option.apply
      ),
      "name" -> normalizedText.verifying(maxLength(UserGroup.nameMaxLength)),
      "description" -> normalizedOptionalText,
      "insee-code" -> list(text),
      "creationDate" -> ignored(date),
      "area-ids" -> list(uuid)
        .verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
      "organisation" -> optional(of[Organisation.Id]).verifying(
        "Vous devez sélectionner une organisation dans la liste",
        _.exists(Organisation.isValidId)
      ),
      "email" -> optional(email),
      "isInFranceServicesNetwork" -> ignored(true),
      "publicNote" -> ignored(Option.empty[String]),
      "internalSupportComment" -> ignored(Option.empty[String])
    )(UserGroup.apply)(toTupleOpt)

  private def importUsersAfterReviewForm(date: ZonedDateTime): Form[List[CSVUserGroupFormData]] =
    Form(
      single(
        "groups" -> list(
          mapping(
            "group" -> groupImportMapping(date),
            "users" -> list(
              mapping(
                "user" -> userImportMapping,
                "line" -> number,
                "alreadyExists" -> boolean,
                "alreadyExistingUser" -> ignored(Option.empty[User]),
                "isInMoreThanOneGroup" -> optional(boolean)
              )(CSVUserFormData.apply)(toTupleOpt)
            ),
            "alreadyExistsOrAllUsersAlreadyExist" -> boolean,
            "doNotInsert" -> boolean,
            "alreadyExistingGroup" -> ignored(Option.empty[UserGroup])
          )(CSVUserGroupFormData.apply)(toTupleOpt)
        )
      )
    )

  /** Action that reads the CSV file (CSV file was copy-paste in a web form) and display possible
    * errors.
    */
  def importUsersReview: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(
        ImportGroupUnauthorized,
        "Accès non autorisé pour importer les utilisateurs"
      ) { () =>
        CSVRawLinesFormData.contentForm
          .bindFromRequest()
          .fold(
            { csvImportContentFormWithError =>
              eventService.log(
                CsvImportInputEmpty,
                "Le champ d'import de CSV est vide ou le séparateur n'est pas défini"
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
                .csvLinesToUserGroupData(csvImportData.separator, defaultAreas)(
                  csvImportData.csvLines
                )
                .fold(
                  { (error: String) =>
                    val csvImportContentFormWithError =
                      CSVRawLinesFormData.contentForm.fill(csvImportData).withGlobalError(error)
                    eventService
                      .log(CSVImportFormError, "Erreur de formulaire Importation")
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
                          userGroupDataForm: List[UserGroupBlock]
                        ) =>
                      val augmentedUserGroupInformation: List[CSVUserGroupFormData] =
                        csvImportDataToReviewFormData(userGroupDataForm)

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

  private def toInsertableUser(
      userData: CSVUserFormData,
      groupData: CSVUserGroupFormData
  ): Option[User] =
    if (groupData.doNotInsert || userData.alreadyExistingUser.nonEmpty) {
      None
    } else {
      val groupId = groupData.group.id
      val areaIds = groupData.group.areaIds

      Some(
        User(
          id = UUID.randomUUID(),
          key = "",
          firstName = userData.user.firstName,
          lastName = userData.user.lastName,
          name = userData.user.name,
          qualite = "",
          email = userData.user.email,
          helper = true,
          instructor = userData.user.instructor,
          admin = false,
          areas = areaIds,
          creationDate = Time.nowParis(),
          communeCode = "0",
          groupAdmin = userData.user.groupAdmin,
          disabled = false,
          expert = false,
          groupIds = groupId :: Nil,
          cguAcceptationDate = None,
          newsletterAcceptationDate = None,
          firstLoginDate = none,
          phoneNumber = userData.user.phoneNumber,
          observableOrganisationIds = Nil,
          managingOrganisationIds = Nil,
          managingAreaIds = Nil,
          sharedAccount = userData.user.name.nonEmpty,
          internalSupportComment = None,
          passwordActivated = false,
        )
      )
    }

  /** Import the reviewed CSV. */
  def importUsersAfterReview: Action[AnyContent] =
    loginAction.async { implicit request =>
      asAdmin(
        ImportUsersUnauthorized,
        "Accès non autorisé pour importer les utilisateurs"
      ) { () =>
        val currentDate = Time.nowParis()
        importUsersAfterReviewForm(currentDate)
          .bindFromRequest()
          .fold(
            { importUsersAfterReviewFormWithError =>
              eventService.log(ImportUserFormError, "Erreur de formulaire de review")
              Future(
                BadRequest(
                  views.html.reviewUsersImport(request.currentUser, request.rights)(
                    importUsersAfterReviewFormWithError
                  )
                )
              )
            },
            { (userGroupDataForm: List[CSVUserGroupFormData]) =>
              val augmentedUserGroupInformation: List[CSVUserGroupFormData] =
                augmentUserGroupsInformation(userGroupDataForm)

              val groupsToInsert = augmentedUserGroupInformation
                .filterNot(_.doNotInsert)
                .filterNot(_.alreadyExistsOrAllUsersAlreadyExist)
                .filter(_.alreadyExistingGroup.isEmpty)
                .map(_.group)
              groupService
                .add(groupsToInsert)
                .fold(
                  { (error: String) =>
                    eventService.log(
                      ImportUserError,
                      "Impossible d'importer les groupes",
                      s"Erreur '$error'".some
                    )
                    val message = s"Impossible d'importer les groupes : $error"
                    val formWithError = importUsersAfterReviewForm(currentDate)
                      .fill(augmentedUserGroupInformation)
                      .withGlobalError(message)
                    Future(
                      InternalServerError(
                        views.html
                          .reviewUsersImport(request.currentUser, request.rights)(formWithError)
                      )
                    )
                  },
                  { _ =>
                    groupsToInsert.foreach { userGroup =>
                      eventService.log(
                        UserGroupCreated,
                        "Groupe ajouté",
                        s"Groupe ${userGroup.toLogString}".some
                      )
                    }
                    val usersToInsert: List[User] = augmentedUserGroupInformation
                      .flatMap(group => group.users.flatMap(user => toInsertableUser(user, group)))
                      .groupBy(_.email)
                      .map { case (_, entitiesWithSameEmail) =>
                        // Note: users appear in the same order as given in the import
                        // Safe due to groupBy
                        val repr: User = entitiesWithSameEmail.head
                        val groupIds: List[UUID] = entitiesWithSameEmail.flatMap(_.groupIds)
                        val areas: List[UUID] = entitiesWithSameEmail.flatMap(_.areas).distinct
                        repr.copy(
                          areas = areas,
                          groupIds = groupIds
                        )
                      }
                      .toList

                    userService
                      .add(usersToInsert)
                      .fold(
                        { (error: String) =>
                          eventService.log(
                            ImportUserError,
                            "Impossible d'importer les utilisateurs",
                            s"Erreur '$error'".some
                          )
                          val message = s"Impossible d'importer les utilisateurs : $error"
                          val formWithError = importUsersAfterReviewForm(currentDate)
                            .fill(augmentedUserGroupInformation)
                            .withGlobalError(message)
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
                            val _ = notificationsService.newUser(user)
                            eventService.log(
                              UserCreated,
                              "Utilisateur ajouté",
                              s"Utilisateur ${user.toLogString}".some,
                              involvesUser = Some(user.id)
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
