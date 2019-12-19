package controllers

import java.util.UUID

import csv.{Header, GROUP_AREAS, GROUP_EMAIL,GROUP_HEADER,GROUP_HEADERS,GROUP_MANAGER,GROUP_NAME,GROUP_ORGANISATION}
import actions.LoginAction
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.Operators
import extentions.UUIDHelper
import extentions.Operators.{GroupOperators, UserOperators, not}
import forms.Models.{CSVImportData, UserGroupFormData}
import javax.inject.Inject
import models.{Area, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, InjectedController}
import services.{EventService, NotificationService, UserGroupService, UserService}
import org.joda.time.DateTime

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
    "separator" -> nonEmptyText.verifying(value => value.equals(";") || value.equals(","))
  )(CSVImportData.apply)(CSVImportData.unapply))

  def importUsersFromCSV: Action[AnyContent] = loginAction { implicit request =>
    asAdmin { () =>
      "IMPORT_USER_UNAUTHORIZED" -> "Accès non autorisé pour importer les utilisateurs"
    } { () =>
      Ok(views.html.importUsersCSV(request.currentUser)(csvImportContentForm))
    }
  }



  def csvLinesToMap(csvLines: String): Either[String, List[Either[Map[String, String], String]]]


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
       Right(result)
    } catch {
        case ex: com.github.tototoshi.csv.MalformedCSVException =>
        Left(s"Erreur lors de l'extraction du csv ${ex.message}")
    }

  def csvCleanHeadersWithExpectedHeaders(expectedGroupHeaders: List[Header],
                                         expectedUserHeaders: List[Header])(csvMap: CSVMap): CSVMap = {
    def convertToPrefixForm(values: CSVMap, expectedHeaders: List[Header], formPrefix: String): CSVMap = {
      values.map({ case (key, value) =>
        val lowerKey = key.trim.toLowerCase
        expectedHeaders.find(expectedHeader => expectedHeader.lowerPrefixes.exists(lowerKey.startsWith))
          .map(expectedHeader => formPrefix + expectedHeader.key -> value.trim)
      }).flatten.toMap
    }

    convertToPrefixForm(csvMap, expectedGroupHeaders, "group.") ++ convertToPrefixForm(csvMap, expectedUserHeaders, "user.")
  }

  def csvIncludeFirstnameInLastName(csvMap: CSVMap): CSVMap = ???

  def csvMapToUserGroupData(csvMap: CSVMap, line: LineNumber): Either[String, UserGroupFormData] = ???

  def userGroupDataListToUserGroupData(userGroupFormData: List[UserGroupFormData]): List[UserGroupFormData] = ???

  def augmentUserGroupInformation(userGroupFormData: UserGroupFormData): Either[String, UserGroupFormData] = ???
  
/* 
  type CSVExtractResult = List[(/* result */Either[String, CSVMap],/* line */LineNumber, CSVMap, RawCSVLine)]

    val (csvLinesErrorPart: CSVExtractResult, csvMapList: CSVExtractResult) = csvExtractResult.partition(_._1.isLeft)
            val csvLinesError
            for( csvMap 

*/

  def includeAreaNameInGroupName() = ???
  
  def csvLinesToUserGroupData(separator: Char)(csvLines: String): Either[String, (List[String], List[UserGroupFormData])] = {
    def partition(list: List[Either[String, UserGroupFormData]]): (List[String], List[UserGroupFormData]) = ???
    
    val x: Either[String, (List[String], List[UserGroupFormData])] = extractFromCSVToMap(separator)(csvLines)
       .flatMap{
          val result: List[Either[String, UserGroupFormData]] = _.flatMap { 
            case (lineNumber: LineNumber, csvMap: CSVMap, rawCSVLine: RawCSVLine) =>
            val newCsvMap = csvCleanHeadersWithExpectedHeaders(csvIncludeFirstnameInLastName(csvMap))
            csvMapToUserGroupData(newCsvMap, lineNumber).left.map { error =>
               s"Ligne $lineNumber : error $error ( $rawCSVLine )"
            }
          }
          partition(result)
       }

       x.flatMap {
          case (linesErrorList: List[String], userGroupFormDataList: List[UserGroupFormData]) =>
          (linesErrorList, userGroupDataListToUserGroupData(userGroupFormDataList))
       }
    }
  
  def importUsersReview: Action[AnyContent] = {
    loginAction { implicit request =>
      asAdmin { () =>
        "IMPORT_GROUP_UNAUTHORIZED" -> "Accès non autorisé pour importer les utilisateurs"
      } { () =>
        csvImportContentForm.fold({ _ =>
          eventService.warn(code = "CSV_IMPORT_INPUT_EMPTY", description = "Le champ d'import de CSV est vide ou le séparateur n'est pas défini.")
          BadRequest(views.html.importUsersCSV(request.currentUser)(csvImportContentForm))
        }, { csvImportData =>

          csvLinesToUserGroupData
          /*
          csvLinesToMap(csvImportData.csvLines)
             .flatMap(csvCleanHeadersWithExpextedHeaders(csvMap))
             .flatMap(csvCleanName(csvMapWithCleanHeaders))
             .flatMap(csvMapToUserGroupDats(userGroupDatas)).fold {
                  case Error(message) =>
                    BadRequest
                  case (userGroupData: UserGroupFormData, userNotImported: List(String)) =>
                     augmentUserGroupInformation(userGroupDatas)

                     val form = importUsersReviewFrom.filled(userGroupDatas)
                     Ok
               }
              

                   */
          NotImplemented
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
