package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Result}
import play.api.i18n.I18nSupport
import models.formModels.AccountCreationFormData
import scala.concurrent.{ExecutionContext, Future}
import helper.ScalatagsHelpers.writeableOf_Modifier
import helper.PlayFormHelper
import play.api.Logger
import models.{
  AccountCreation,
  AccountCreationRequest,
  AccountCreationSignature,
  Authorization,
  EventType,
  User,
  UserGroup
}
import java.time.Instant
import java.util.UUID
import constants.Constants
import actions.{LoginAction, RequestWithUserData}
import services.{AccountCreationService, EventService, UserGroupService, UserService}
import cats.syntax.all._
import helper.Time

@Singleton
class AccountCreationController @Inject() (
    accountCreationService: AccountCreationService,
    val controllerComponents: ControllerComponents,
    val eventService: EventService,
    loginAction: LoginAction,
    userGroupService: UserGroupService,
    val userService: UserService,
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport
    with Operators.UserOperators {

  // Using Logger here, because we don't want to have the events logged in the db
  private val log = Logger(classOf[AccountCreationController])

  def accountTypeForm: Action[AnyContent] = Action { implicit request =>
    Ok(views.accountCreation.accountTypePage())
  }

  def handleAccountTypeForm: Action[AnyContent] = Action { implicit request =>
    AccountCreationFormData.accountTypeForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val errors = PlayFormHelper.formErrorsLog(formWithErrors)
          log.warn(s"Erreur dans le choix te type de création de compte : $errors")
          BadRequest(views.accountCreation.accountTypePage())
        },
        formData => {
          if (formData.isNamedAccount)
            Redirect(controllers.routes.AccountCreationController.namedAccountCreationForm)
          else
            Redirect(controllers.routes.AccountCreationController.sharedAccountCreationForm)
        }
      )
  }

  def namedAccountCreationForm: Action[AnyContent] = Action { implicit request =>
    Ok(
      views.accountCreation.accountCreationPage(AccountCreationFormData.form, isNamedAccount = true)
    )
  }

  def sharedAccountCreationForm: Action[AnyContent] = Action { implicit request =>
    Ok(
      views.accountCreation
        .accountCreationPage(AccountCreationFormData.form, isNamedAccount = false)
    )
  }

  def transformAccountCreationFormData(formData: AccountCreationFormData): AccountCreation = {
    val accountCreationRequest = AccountCreationRequest(
      id = UUID.randomUUID(),
      requestDate = Instant.now(),
      email = formData.email,
      isNamedAccount = formData.isNamedAccount,
      firstName = formData.firstName,
      lastName = formData.lastName,
      phoneNumber = formData.phoneNumber,
      areaIds = formData.area,
      qualite = formData.qualite,
      organisationId = formData.organisation,
      miscOrganisation = formData.miscOrganisation,
      isManager = formData.isManager,
      isInstructor = formData.isInstructor,
      message = formData.message,
      rejectionUserId = None,
      rejectionDate = None,
      rejectionReason = None
    )

    val accountCreationSignatures = formData.signatures.map { signature =>
      AccountCreationSignature(
        id = UUID.randomUUID(),
        formId = accountCreationRequest.id,
        firstName = signature.firstName,
        lastName = signature.lastName,
        phoneNumber = signature.phoneNumber
      )
    }

    AccountCreation(
      form = accountCreationRequest,
      signatures = accountCreationSignatures
    )
  }

  def transformAccountCreationToFormData(
      accountCreation: AccountCreation
  ): AccountCreationFormData = {
    val accountCreationRequest = accountCreation.form
    val accountCreationSignatures = accountCreation.signatures.map { signature =>
      AccountCreationFormData.Signature(
        firstName = signature.firstName,
        lastName = signature.lastName,
        phoneNumber = signature.phoneNumber
      )
    }
    AccountCreationFormData(
      email = accountCreationRequest.email,
      isNamedAccount = accountCreationRequest.isNamedAccount,
      firstName = accountCreationRequest.firstName,
      lastName = accountCreationRequest.lastName,
      phoneNumber = accountCreationRequest.phoneNumber,
      area = accountCreationRequest.areaIds,
      qualite = accountCreationRequest.qualite,
      organisation = accountCreationRequest.organisationId,
      miscOrganisation = accountCreationRequest.miscOrganisation,
      isManager = accountCreationRequest.isManager,
      isInstructor = accountCreationRequest.isInstructor,
      message = accountCreationRequest.message,
      signatures = accountCreationSignatures,
      groups = Nil
    )
  }

  def handleNamedAccountCreationForm: Action[AnyContent] =
    handleAccountCreationForm(isNamedAccount = true)

  def handleSharedAccountCreationForm: Action[AnyContent] =
    handleAccountCreationForm(isNamedAccount = false)

  private def handleAccountCreationForm(isNamedAccount: Boolean): Action[AnyContent] =
    Action.async { implicit request =>
      AccountCreationFormData.form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            val errors = PlayFormHelper.formErrorsLog(formWithErrors)
            val message = "Erreur dans le formulaire de création de compte " +
              (if (isNamedAccount) "nominatif" else "partagé") +
              s" : $errors"
            log.warn(message)
            Future.successful(
              BadRequest(
                views.accountCreation
                  .accountCreationPage(formWithErrors, isNamedAccount = isNamedAccount)
              )
            )
          },
          formData => {
            val data = transformAccountCreationFormData(formData)
            accountCreationService
              .add(data)
              .map(
                _.fold(
                  error => {
                    val message = s"${error.eventType.code} ${error.description}"
                    error.underlyingException.fold(log.error(message))(e => log.error(message, e))
                    InternalServerError(Constants.genericError500Message)
                  },
                  _ => {
                    val message = s"Demande de création d’un compte " +
                      (if (isNamedAccount) "nominatif" else "partagé") +
                      s" : ${data.toLogString}"
                    log.info(message)
                    Redirect(routes.AccountCreationController.formSent)
                  }
                )
              )
          }
        )
    }

  def formSent: Action[AnyContent] = Action {
    Ok(views.accountCreation.formSentPage())
  }

  private def withAccountCreationForm(formId: UUID)(
      inner: AccountCreation => Future[Result]
  )(implicit request: RequestWithUserData[_]): Future[Result] =
    accountCreationService
      .byId(formId)
      .flatMap(
        _.fold(
          error => {
            eventService.logError(error)
            Future.successful(InternalServerError(Constants.genericError500Message))
          },
          {
            case None => Future.successful(NotFound("Ce formulaire n’existe pas")) // TODO 404 page
            case Some(formValues) => inner(formValues)
          }
        )
      )

  private def managingGroups(user: User): Future[List[UserGroup]] =
    if (user.admin)
      userGroupService.all
    else
      userGroupService.byAreasAndOrganisations(
        user.managingAreaIds,
        user.managingOrganisationIds
      )

  def managerValidation(formId: UUID): Action[AnyContent] = loginAction.async { implicit request =>
    withAccountCreationForm(formId) { formValues =>
      asUserWithAuthorization(Authorization.canManageAccountCreationForm(formValues.form))(
        EventType.SignupsUnauthorized,
        s"L'utilisateur n'est pas autorisé à éditer le formulaire de création de compte"
      ) { () =>
        managingGroups(request.currentUser).map { managingGroups =>
          val form = AccountCreationFormData.form
            .fill(transformAccountCreationToFormData(formValues))
          Ok(
            views.accountCreation.managerValidationPage(
              formId,
              form,
              isNamedAccount = formValues.form.isNamedAccount,
              managingGroups
            )
          )
        }
      }
    }
  }

  // TODO: handle cases where user asks more areas than the manager
  // in general, having the manager assign one group, then another assigning another
  // or some warning that one might want to send to another area that has the correct group
  def handleManagerValidation(formId: UUID): Action[AnyContent] = loginAction.async {
    implicit request =>
      withAccountCreationForm(formId) { formValues =>
        asUserWithAuthorization(Authorization.canManageAccountCreationForm(formValues.form))(
          EventType.SignupsUnauthorized,
          s"L'utilisateur n'est pas autorisé à éditer le formulaire de création de compte"
        ) { () =>
          managingGroups(request.currentUser).flatMap { managingGroups =>
            val form = AccountCreationFormData.form
              .bindFromRequest()
            form
              .fold(
                formWithErrors => {
                  val errors = PlayFormHelper.formErrorsLog(formWithErrors)
                  val message =
                    s"Erreur dans le formulaire de validation de création de compte : $errors"
                  eventService.log(
                    EventType.SignupsValidationError,
                    message,
                  )
                  Future.successful(
                    BadRequest(
                      views.accountCreation.managerValidationPage(
                        formId,
                        formWithErrors,
                        isNamedAccount = formValues.form.isNamedAccount,
                        managingGroups
                      )
                    )
                  )
                },
                formData => {
                  val wantedGroups = formData.groups
                  val canManageGroups = wantedGroups.toSet.subsetOf(managingGroups.map(_.id).toSet)
                  if (canManageGroups) {
                    // TODO: check if email exists to provide a nice error
                    val newUserId = UUID.randomUUID()
                    val name: String = s"${formData.lastName.toUpperCase} ${formData.firstName}"
                    val user = User(
                      id = newUserId,
                      key = "", // generated by UserService
                      firstName = formData.firstName.some,
                      lastName = formData.lastName.some,
                      name = name,
                      qualite = formData.qualite.orEmpty,
                      email = formData.email,
                      helper = true,
                      instructor = formData.isInstructor,
                      admin = false,
                      areas = formData.area,
                      creationDate = Time.nowParis(),
                      communeCode = "0",
                      groupAdmin = formData.isManager,
                      disabled = false,
                      expert = false,
                      groupIds = wantedGroups,
                      cguAcceptationDate = none,
                      newsletterAcceptationDate = none,
                      firstLoginDate = none,
                      phoneNumber = formData.phoneNumber,
                      observableOrganisationIds = Nil,
                      managingOrganisationIds = Nil,
                      managingAreaIds = Nil,
                      sharedAccount = !formData.isNamedAccount,
                      internalSupportComment = None
                    )
                    userService
                      .addFuture(user :: Nil)
                      .flatMap(
                        _.fold(
                          errorMessage => {
                            eventService.log(
                              EventType.SignupFormError,
                              s"Erreur lors de l'ajout d'un utilisateur par formulaire d'inscription $formId",
                              s"Message : '$errorMessage' / Utilisateur ayant échoué : ${user.toLogString}".some
                            )
                            val userMessage =
                              "Une erreur imprévue est survenue. Ce n’est pas de votre faute. " +
                                "Nous vous invitons à réessayer plus tard, " +
                                s"ou contacter le support ${Constants.supportEmail} si celle-ci persiste."
                            val formWithErrors = AccountCreationFormData.form
                              .fill(formData)
                              .withGlobalError(userMessage)
                            Future.successful(
                              InternalServerError(
                                views.accountCreation.managerValidationPage(
                                  formId,
                                  formWithErrors,
                                  isNamedAccount = formData.isNamedAccount,
                                  managingGroups
                                )
                              )
                            )
                          },
                          _ => {
                            eventService.log(
                              EventType.SignupFormSuccessful,
                              s"Le responsable a créé l'utilisateur $newUserId",
                              s"Utilisateur ${user.toLogString}".some,
                              involvesUser = newUserId.some
                            )
                            // TODO: should we make a Redirect here?
                            Future
                              .successful(Ok(views.accountCreation.userAccountCreatedPage(user)))
                          }
                        )
                      )
                  } else {
                    eventService.log(
                      EventType.SignupsUnauthorized,
                      s"Tentative d’ajout de compte dans des groupes non autorisés ($wantedGroups)"
                    )
                    Future.successful(
                      Unauthorized("Vous n'avez pas le droit de faire ça")
                    )
                  }
                }
              )
          }
        }
      }
  }

  // TODO: add rejection reason
  def handleManagerRejection(formId: UUID): Action[AnyContent] = loginAction.async {
    implicit request =>
      withAccountCreationForm(formId) { formValues =>
        asUserWithAuthorization(Authorization.canManageAccountCreationForm(formValues.form))(
          EventType.SignupsUnauthorized,
          s"L'utilisateur n'est pas autorisé à éditer le formulaire de création de compte"
        ) { () =>
          accountCreationService
            .reject(formId, request.currentUser.id, Instant.now(), "")
            .map(
              _.fold(
                error => {
                  eventService.logError(error)
                  InternalServerError(Constants.genericError500Message)
                },
                _ => {
                  eventService.log(
                    EventType.SignupFormRejected,
                    s"Reject du formulaire d'inscription ($formId)"
                  )
                  Ok(views.accountCreation.rejectionPage())
                }
              )
            )
        }
      }
  }

}
