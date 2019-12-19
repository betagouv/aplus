package controllers

import java.util.UUID

import csv.{GROUP_AREAS, GROUP_EMAIL, GROUP_HEADER, GROUP_HEADERS, GROUP_MANAGER, GROUP_NAME, GROUP_ORGANISATION, Header}
import actions.LoginAction
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.Operators
import extentions.UUIDHelper
import extentions.Operators.{GroupOperators, UserOperators, not}
import forms.Models.{CSVImportData, UserFormData, UserGroupFormData}
import javax.inject.Inject
import models.{Area, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, InjectedController}
import services.{EventService, NotificationService, UserGroupService, UserService}
import org.joda.time.{DateTime, DateTimeZone}

import scala.io.Source
import extentions.Time

case class CSVImportController @Inject()(loginAction: LoginAction,
                                    userService: UserService,
                                    groupService: UserGroupService,
                                    notificationsService: NotificationService,
                                    eventService: EventService)(implicit val webJarsUtil: WebJarsUtil) extends InjectedController with play.api.i18n.I18nSupport with UserOperators with GroupOperators {


  val csvImportContentForm: Form[CSVImportData] = Form(
    mapping(
    "csv-lines" -> nonEmptyText,
    "area-default-ids" -> list(uuid),
    "separator" -> char.verifying(value => value.equals(";") || value.equals(","))
  )(CSVImportData.apply)(CSVImportData.unapply))

  def importUsersFromCSV: Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      "IMPORT_USER_UNAUTHORIZED" -> "Accès non autorisé pour importer les utilisateurs"
    } { () =>
      Ok(views.html.importUsersCSV(request.currentUser)(csvImportContentForm))
    }
  }

  type LineNumber = Int
  type CSVMap = Map[String, String]
  type RawCSVLine = String
  type CSVExtractResult = List[(LineNumber, CSVMap, RawCSVLine)]


  def extractFromCSVToMap(separator: Char)(csvText: String): Either[String, CSVExtractResult] = try {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = separator
    }
    val csvReader = CSVReader.open(Source.fromString(csvText))
    val headers = csvReader.readNext()
    val result = headers.map(headers => {
    val lines = csvReader.all().filter(_.reduce(_+_).nonEmpty)
      lines.map(line => headers.zip(line).toMap -> line.mkString(SemiConFormat.delimiter.toString))
    }).getOrElse(Nil).zipWithIndex
       Right(???)
    } catch {
        case ex: com.github.tototoshi.csv.MalformedCSVException =>
        Left(s"Erreur lors de l'extraction du csv ${ex.getMessage}")
    }

  private val expectedGroupHeaders: List[Header]  = Nil
  private val expectedUserHeaders: List[Header] = Nil


  implicit class CSVMapPreprocessing(csvMap: CSVMap) {
    def csvCleanHeadersWithExpectedHeaders(): CSVMap = {
      def convertToPrefixForm(values: CSVMap, expectedHeaders: List[Header], formPrefix: String): CSVMap = {
        values.map({ case (key, value) =>
          val lowerKey = key.trim.toLowerCase
          expectedHeaders.find(expectedHeader => expectedHeader.lowerPrefixes.exists(lowerKey.startsWith))
            .map(expectedHeader => formPrefix + expectedHeader.key -> value.trim)
        }).flatten.toMap
      }

      convertToPrefixForm(csvMap, expectedGroupHeaders, "group.") ++ convertToPrefixForm(csvMap, expectedUserHeaders, "user.")
    }

    def includeAreaNameInGroupName(defaultAreas: Seq[Area]): CSVMap = {
      val newAreas: Seq[Area] = csvMap.get(csv.GroupAreas.key) match {
        case Some(areas) =>
          areas.split(",").flatMap(_.split(";")).flatMap(Area.searchFromName)
        case None =>
          defaultAreas
      }
      csvMap + (csv.GroupAreas.key -> newAreas.map(_.id.toString).mkString(","))
    }

    def includeFirstnameInLastName(): CSVMap = ???

    def toUserGroupData(lineNumber: LineNumber): Either[String, UserGroupFormData] = {
      groupCSVMapping.bind(csvMap).fold({ errors =>
        Left(errors.mkString(", "))
      }, { group =>
        userCSVMapping.bind(csvMap).fold({ errors =>
          Left(errors.mkString(", "))
        }, { user =>
          Right(UserGroupFormData(group, List(UserFormData(user, lineNumber))))
        })
      })
    }
  }
  
  private val userCSVMapping: Mapping[User] = ???
  private val groupCSVMapping: Mapping[UserGroup] = ???

  def userGroupDataListToUserGroupData(userGroupFormData: List[UserGroupFormData]): List[UserGroupFormData] = ???


  def augmentUserGroupInformation(userGroupFormData: UserGroupFormData): UserGroupFormData = ???
  
/* 
  type CSVExtractResult = List[(/* result */Either[String, CSVMap],/* line */LineNumber, CSVMap, RawCSVLine)]

    val (csvLinesErrorPart: CSVExtractResult, csvMapList: CSVExtractResult) = csvExtractResult.partition(_._1.isLeft)
            val csvLinesError
            for( csvMap 

*/


  def csvLinesToUserGroupData(separator: Char, defaultAreas: Seq[Area])(csvLines: String): Either[String, (List[String], List[UserGroupFormData])] = {
    def partition(list: List[Either[String, UserGroupFormData]]): (List[String], List[UserGroupFormData]) = ???
    
    extractFromCSVToMap(separator)(csvLines)
       .map { csvExtractResult: CSVExtractResult =>
          val result: List[Either[String, UserGroupFormData]] = csvExtractResult.map {
            case (lineNumber: LineNumber, csvMap: CSVMap, rawCSVLine: RawCSVLine) =>
              csvMap.csvCleanHeadersWithExpectedHeaders
                  .includeAreaNameInGroupName(defaultAreas)
                  .includeFirstnameInLastName()
                  .toUserGroupData(lineNumber).left.map { error: String =>
                 s"Ligne $lineNumber : error $error ( $rawCSVLine )"
              }
          }
          partition(result)
       }.map {
          case (linesErrorList: List[String], userGroupFormDataList: List[UserGroupFormData]) =>
          (linesErrorList, userGroupDataListToUserGroupData(userGroupFormDataList))
       }
    }

  /*
  private def userMapping(implicit timeZone: DateTimeZone): Mapping[User] = mapping(
    "id" -> optional(uuid).transform[UUID]({
      case None => UUID.randomUUID()
      case Some(id) => id
    }, {
      Some(_)
    }),
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
    )).transform[Map[String, String]]({
      _.toMap
    }, {
      _.toSeq
    }),
    "cguAcceptationDate" -> ignored(Option.empty[DateTime]),
    "newsletterAcceptationDate" -> ignored(Option.empty[DateTime]),
    csv.USER_PHONE_NUMBER.key -> optional(text),
  )(User.apply)(User.unapply)   */


  private val userImportMapping: Mapping[User] = ???
  private val groupImportMapping: Mapping[UserGroup] = ???

  val importUsersReviewFrom: Form[List[UserGroupFormData]] = Form(
    single(
    "groups" -> list(
            mapping(
              "group" -> groupImportMapping,
              "user" -> list(
                  mapping(
                    "user" -> userImportMapping,
                    "line" -> number,
                    "alreadyExistingUser" -> ignored(Option.empty[User])
                  )(UserFormData.apply)(UserFormData.unapply)
              ),
              "alreadyExistingGroup" -> ignored(Option.empty[UserGroup])
            )(UserGroupFormData.apply)(UserGroupFormData.unapply)
        )
    )
  )

  def importUsersReview: Action[AnyContent] = {
    loginAction { implicit request =>
      asAdmin { () =>
        "IMPORT_GROUP_UNAUTHORIZED" -> "Accès non autorisé pour importer les utilisateurs"
      } { () =>
        csvImportContentForm.bindFromRequest.fold({ csvImportContentFormWithError =>
          eventService.warn(code = "CSV_IMPORT_INPUT_EMPTY", description = "Le champ d'import de CSV est vide ou le séparateur n'est pas défini.")
          BadRequest(views.html.importUsersCSV(request.currentUser)(csvImportContentFormWithError))
        }, { csvImportData =>
          val defaultAreas = csvImportData.areaIds.flatMap(Area.fromId)
          csvLinesToUserGroupData(csvImportData.separator, defaultAreas)(csvImportData.csvLines).fold({
            error: String =>
              val csvImportContentFormWithError = csvImportContentForm.fill(csvImportData).withGlobalError(error)
              BadRequest(views.html.importUsersCSV(request.currentUser)(csvImportContentFormWithError))
          }, {
            case (userNotImported: List[String], userGroupDataForm: List[UserGroupFormData]) =>
              val augmentedUserGroupInformation: List[UserGroupFormData] = userGroupDataForm.map(augmentUserGroupInformation)
              val formWithError = importUsersReviewFrom.fillAndValidate(augmentedUserGroupInformation)

              formWithError.withGlobalError("Certaines lignes du CSV n'ont pas pu être importé", userNotImported)
              Ok(views.html.reviewUsersImport(request.currentUser)(formWithError))
          })
        })
    /*    form.fold({ _ =>
          eventService.warn(code = "CSV_IMPORT_INPUT_EMPTY", description = "Le champ d'import de CSV est vide ou le département n'est pas défini.")
          BadRequest(views.html.importUsersCSV(request.currentUser)(form))
        }, { case (csvImportContent, area, separator) =>
          val csvMap = extractFromCSVToMap(csvImportContent, separator.head)
          if(csvMap.isEmpty){
            eventService.warn(code = "INVALID_CSV", description = "Le format du CSV fourni est invalide.")
            BadRequest(views.html.importUsersCSV(request.currentUser)(csvImportContentForm))
          }
          //TODO : remove get
          val (groupToUsersMap, lineNumberToErrors) = csv.extractFromMapValidInputAndErrors(csvMap.get, request.currentUser.id, area)
          if (groupToUsersMap.isEmpty) {
            eventService.warn(code = "INVALID_CSV", description = "Aucun utilisateur n'a pu être trouver dans le CSV")
            BadRequest(views.html.importUsersCSV(request.currentUser)(csvImportContentForm))
          } else {
            // Remove already existing users that also already belongs to the group.
            val groupToNewUsersMap = groupToUsersMap.map({ case (group, users) =>
              group -> users.filterNot(user => userService.byEmail(user.email).exists(_.areas.contains(group.area)))
            })

            val existingUsers: List[User] = groupToUsersMap.map({ case (group, users) =>
              val newAndOldUser = users.map(user => user -> userService.byEmail(user.email))
              group -> newAndOldUser.filter(tuple => tuple._2.exists(_.areas.contains(group.area))).flatMap(_._2)
            }).flatMap(_._2)

            val errors: List[(String, String)] = lineNumberToErrors.map({ case (lineNumber, (errors, completeLine)) => "Ligne %d : %s".format(lineNumber, errors.map(e => s"${e.key} ${e.message}").mkString(", ")) -> completeLine })
            val filledForm = csv.sectionsForm(request.currentUser.id)
              .fill(groupToNewUsersMap.map({ case (group, users) => Section(group, users) }))
              .withGlobalError("Il y a des erreurs", errors: _*)
            Ok(views.html.reviewUsersImport(request.currentUser)(filledForm, existingUsers))
          }
        })    */
      }
    }
  }

  def importUsersReviewPost: Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      "IMPORT_USERS_UNAUTHORIZED" -> "Accès non autorisé pour importer les utilisateurs"
    } { () =>
      importUsersReviewFrom.bindFromRequest.fold({ importUsersReviewFromWithError =>
        BadRequest
      }, {  userGroupDataForm: List[UserGroupFormData] =>
        val augmentedUserGroupInformation: List[UserGroupFormData] = userGroupDataForm.map(augmentUserGroupInformation)

        val groupsToInsert = augmentedUserGroupInformation.filter(_.alreadyExistingGroup.isEmpty).map(_.group)
        if(!groupService.add(groupsToInsert)) {
          //TODO : catch exception : groupe name already exist
          val description = s"Impossible d'importer les groupes"
          eventService.error("IMPORT_USER_ERROR", description)
          val formWithError = importUsersReviewFrom.fill(augmentedUserGroupInformation).withGlobalError(description)
          InternalServerError(views.html.reviewUsersImport(request.currentUser)(formWithError))
        } else {
          val usersToInsert = augmentedUserGroupInformation.flatMap(_.users).filter(_.alreadyExistingUser.isEmpty).map(_.user)
          if(!userService.add(usersToInsert)) {
            //TODO : catch exception : email already exist
            val description = s"Impossible de mettre à des utilisateurs à l'importation."
            eventService.error("IMPORT_USER_ERROR", description)
            val formWithError = importUsersReviewFrom.fill(augmentedUserGroupInformation).withGlobalError(description)
            InternalServerError(views.html.reviewUsersImport(request.currentUser)(formWithError))
          } else {
            usersToInsert.foreach { user =>
              notificationsService.newUser(user)
              eventService.info("ADD_USER_DONE", s"Ajout de l'utilisateur ${user.name} ${user.email}", user = Some(user))
            }
            eventService.info("IMPORT_USERS_DONE", "Utilisateurs ajoutés par l'importation")
            Redirect(routes.UserController.all(request.currentArea.id)).flashing("success" -> "Utilisateurs importés.")
          }
        }
      }



      NotImplemented
     /* csv.sectionsForm(request.currentUser.id).bindFromRequest.fold({ missFilledForm =>
        val cleanedForm = missFilledForm.copy(data = missFilledForm.data.filter({ case (_, v) => v.nonEmpty }))
        eventService.info("IMPORT_USERS_ERROR", s"Erreur dans le formulaire importation utilisateur")
        BadRequest(views.html.reviewUsersImport(request.currentUser)(cleanedForm, Nil))
      }, { case sections =>
        if (sections.isEmpty) {
          val form = csv.sectionsForm(request.currentUser.id).fill(sections).withGlobalError("Action impossible, il n'y a aucun utilisateur à ajouter.")
          eventService.info("IMPORT_USERS_ERROR", s"Erreur d'importation utilisateur : aucun utilisateur à ajouter")
          BadRequest(views.html.reviewUsersImport(request.currentUser)(form, Nil))
        } else {
          // Groups creation
          val groupsToUsers = sections.map({ section =>
            val groups: List[UserGroup] = section.group.inseeCode.map({ areaId =>
              section.group.copy(id = UUID.randomUUID())
            })
            groups -> section.users
          })

          // Groups to insert
          val groupsToInsert: List[UserGroup] = groupsToUsers.flatMap(_._1)
            .filterNot({ group => groupService.groupByName(group.name).isDefined })

          // associate user to groups they belong
          val userToGroups: List[(User, List[UserGroup])] = groupsToUsers.flatMap({ case (groups, users) =>
            users.map(user => user -> groups)
          })
          // retrieval of database status
          val existingUserAndGroupPairStatus: List[((User, Option[User]), (List[UserGroup], List[UserGroup]))] = userToGroups.map({ case (user, groups) =>
            val dbUser = userService.byEmail(user.email)
            (user, dbUser) -> groups.partition(group => dbUser.exists(_.areas.contains(group.area)))
          })
          // user/group pair already existing
          val alreadyExistingPair = existingUserAndGroupPairStatus.map({ case ((user, dbUser), (existing, _)) =>
            (user, dbUser) -> existing
          }).filter(_._2.nonEmpty).map(_._1._2.get)

          if (not(groupService.add(groupsToInsert))) {
            val code = "ADD_GROUP_ERROR"
            val description = s"Impossible d'ajouter les groupes ${groupsToInsert.map(_.name).mkString(", ")} dans la BDD à l'importation."
            eventService.error(code, description)
            val form = csv.sectionsForm(request.currentUser.id).fill(sections).withGlobalError(description)
            InternalServerError(views.html.reviewUsersImport(request.currentUser)(form, alreadyExistingPair))
          } else {
            // user/group pair that doesn't exists
            val notExistingPair = existingUserAndGroupPairStatus.map({ case ((user, dbUser), (_, inexisting)) =>
              (user, dbUser) -> inexisting
            }).filter(_._2.nonEmpty)

            val usersToInsert: List[User] = notExistingPair.filter(_._1._2.isEmpty).map({ case ((user, _), inexisting) =>
              user.copy(groupIds = inexisting.map(_.id).distinct, areas = inexisting.map(_.area).distinct)
            })

            val usersToUpdate: List[User] = notExistingPair.filter(_._1._2.isDefined).map({ case ((user, dbUser), inexisting) =>
              user.copy(groupIds = (dbUser.map(_.groupIds).getOrElse(Nil) ++ inexisting.map(_.id)).distinct,
                areas = (dbUser.map(_.areas).getOrElse(Nil) ++ inexisting.map(_.area)).distinct)
            })

            if (not(userService.add(usersToInsert))) {
              val description = s"Impossible d'ajouter des utilisateurs dans la BDD à l'importation."
              eventService.error("ADD_USER_ERROR", description)
              val form = csv.sectionsForm(request.currentUser.id).fill(sections -> defaultAreaId).withGlobalError(description)
              InternalServerError(views.html.reviewUsersImport(request.currentUser)(form, alreadyExistingPair))
            } else {
              if (not(userService.update(usersToUpdate))) {
                val description = s"Impossible de mettre à des utilisateurs à l'importation."
                eventService.error("UPDATE_USER_ERROR", description)
                val form = csv.sectionsForm(request.currentUser.id).fill(sections -> defaultAreaId).withGlobalError(description)
                InternalServerError(views.html.reviewUsersImport(request.currentUser)(form, alreadyExistingPair))
              } else {
                usersToInsert.foreach { user =>
                  notificationsService.newUser(user)
                  eventService.info("ADD_USER_DONE", s"Ajout de l'utilisateur ${user.name} ${user.email}", user = Some(user))
                }
                eventService.info("IMPORT_USERS_DONE", "Utilisateurs ajoutés par l'importation")
                Redirect(routes.UserController.all(request.currentArea.id)).flashing("success" -> "Utilisateurs importés.")
              }
            }
          }
        }
      })           */
    }
  }
}
