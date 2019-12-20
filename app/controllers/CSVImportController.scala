package controllers

import java.util.UUID

import csv._
import actions.LoginAction
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.Operators
import extentions.UUIDHelper
import extentions.Operators.{GroupOperators, UserOperators, not}
import forms.Models.{CSVImportData, UserFormData, UserGroupFormData}
import javax.inject.Inject
import models.{Area, Organisation, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.data.{Form, Mapping}
import play.api.data.Forms.{uuid, _}
import play.api.mvc.{Action, AnyContent, InjectedController}
import services.{EventService, NotificationService, UserGroupService, UserService}
import org.joda.time.{DateTime, DateTimeZone}

import scala.io.Source
import extentions.Time
import play.api.data.validation.Constraints.{maxLength, nonEmpty}

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
    def recreateRawCSVLine(line: List[String]): RawCSVLine = {
      line.mkString(SemiConFormat.delimiter.toString)
    }

    val csvReader = CSVReader.open(Source.fromString(csvText))
    val headers = csvReader.readNext()
    val result = headers.map(headers => {
       val lines = csvReader.all().filter(_.reduce(_+_).nonEmpty)
          lines.map(line => headers.zip(line).toMap -> recreateRawCSVLine(line))
        }).getOrElse(Nil).zipWithIndex.map{
          case ((csvMap: CSVMap, rawCSVLine: RawCSVLine), lineNumber: Int) =>
            (lineNumber, csvMap, rawCSVLine)
        }
     Right(result)
    } catch {
        case ex: com.github.tototoshi.csv.MalformedCSVException =>
        Left(s"Erreur lors de l'extraction du csv ${ex.getMessage}")
    }

  private val expectedUserHeaders: List[Header] = List(USER_PHONE_NUMBER, USER_FIRST_NAME, USER_NAME, USER_EMAIL, USER_INSTRUCTOR, USER_GROUP_MANAGER)
  private val expectedGroupHeaders: List[Header] = List(GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL, GROUP_AREAS_IDS)

  implicit class CSVMapPreprocessing(csvMap: CSVMap) {
    def csvCleanHeadersWithExpectedHeaders(): CSVMap = {
      def convertToPrefixForm(values: CSVMap, expectedHeaders: List[Header], formPrefix: String): CSVMap = {
        values.map({ case (key, value) =>
          val lowerKey = key.trim.toLowerCase
          expectedHeaders.find(expectedHeader => expectedHeader.lowerPrefixes.exists(lowerKey.startsWith))
            .map(expectedHeader => formPrefix + expectedHeader.key -> value)
        }).flatten.toMap
      }

      convertToPrefixForm(csvMap, expectedGroupHeaders, "group.") ++ convertToPrefixForm(csvMap, expectedUserHeaders, "user.")
    }

    def convertAreasNameToAreaUUID(defaultAreas: Seq[Area]): CSVMap = {
      val newAreas: Seq[Area] = csvMap.get(csv.GROUP_AREAS_IDS.key) match {
        case Some(areas) =>
          areas.split(",").flatMap(_.split(";")).flatMap(_.split("-")).flatMap(Area.searchFromName)
        case None =>
          defaultAreas
      }
      csvMap + (csv.GROUP_AREAS_IDS.key -> newAreas.map(_.id.toString).mkString(","))
    }

    def includeAreasNameInGroupName(): CSVMap = {
      val optionalAreaNames: Option[List[String]] = csvMap.get(csv.GROUP_AREAS_IDS.key).map({ ids: String =>
        ids.split(",").flatMap({ uuid: String =>
          Area.fromId(UUID.fromString(uuid))
        }).toList.map(_.name)
      })
      // TODO: Only if the groupName dont include the area
      (optionalAreaNames -> csvMap.get(csv.GROUP_NAME.key)) match {
        case (Some(areaNames), Some(initialGroupName)) =>
          csvMap + (csv.GROUP_NAME.key -> s"$initialGroupName - ${areaNames.mkString("/")}")
        case _ =>
          csvMap
      }
    }

    def fromCsvFieldNameToHtmlFieldName: CSVMap = {
      csvMap.get(csv.GROUP_AREAS_IDS.key).fold({
        csvMap
      })({ areasValue =>
        val newTuples: Array[(String, String)] = areasValue.split(",").zipWithIndex.map({ case (areaUuid,index) =>
          s"${csv.GROUP_AREAS_IDS.key}[$index]" -> areaUuid
        })
        (csvMap - csv.GROUP_AREAS_IDS.key) ++ newTuples
      })
    }

    def includeFirstnameInLastName(): CSVMap = {
      (csvMap.get(csv.USER_FIRST_NAME.key), csvMap.get(csv.USER_NAME.key)) match {
        case (Some(firstName), Some(lastName)) =>
          csvMap + (csv.USER_NAME.key -> s"${lastName} ${firstName}")
        case _ =>
          csvMap
      }
    }

    def trimValues(): CSVMap = csvMap.mapValues(_.trim)

    def toUserGroupData(lineNumber: LineNumber, currentDate: DateTime): Either[String, UserGroupFormData] = {
      groupCSVMapping(currentDate).bind(csvMap).fold({ errors =>
        Left(errors.mkString(", "))
      }, { group =>
        userCSVMapping(currentDate).bind(csvMap).fold({ errors =>
          Left(errors.mkString(", "))
        }, { user =>
          Right(UserGroupFormData(group, List(UserFormData(user, lineNumber))))
        })
      })
    }
  }
  
  private def userCSVMapping(currentDate: DateTime): Mapping[User] = mapping(
    "id" -> optional(uuid).transform[UUID]({
      case None => UUID.randomUUID()
      case Some(id) => id
    }, {
      Some(_)
    }),
    "key" -> ignored("key"),
    "name" -> nonEmptyText,
    "qualite" -> nonEmptyText,
    "email" -> nonEmptyText,
    "helper" -> ignored(true),
    "instructor" -> boolean,
    "admin" -> ignored(false),
    "area-ids" -> ignored(List.empty[UUID]),
    "creationDate" -> ignored(currentDate),
    "hasAcceptedCharte" -> ignored(true),
    "communeCode" -> ignored("0"),
    "admin-group" -> boolean,
    "disabled" -> ignored(false),
    "expert" -> ignored(false),
    "groupIds" -> default(list(uuid), List()),
    "delegations" -> ignored(Map.empty[String, String]),
    "cguAcceptationDate" -> ignored(Option.empty[DateTime]),
    "newsletterAcceptationDate" -> ignored(Option.empty[DateTime]),
    "phone-number" -> optional(text),
  )(User.apply)(User.unapply)

  private def groupCSVMapping(currentDate: DateTime): Mapping[UserGroup] = single( 
    "group" ->
      groupImportMapping(currentDate)
  )

  def userGroupDataListToUserGroupData(userGroupFormData: List[UserGroupFormData]): List[UserGroupFormData] = {
    userGroupFormData
      .groupBy(_.group.name)
      .mapValues({ case sameGroupNameList: List[UserGroupFormData] =>
        val group = sameGroupNameList.head
        val groupId = group.group.id
        val areasId = group.group.areaIds
        val usersFormData = sameGroupNameList.flatMap(_.users).map({ userFormData => 
          val newUser = userFormData.user.copy(groupIds = (groupId :: userFormData.user.groupIds).distinct,
            areas = (areasId ++ userFormData.user.areas).distinct)
          userFormData.copy(user = newUser)
        })
        group.copy(users = usersFormData)
      }).values.toList
  }

  def augmentUserGroupInformation(userGroupFormData: UserGroupFormData): UserGroupFormData = {
    val userEmails = userGroupFormData.users.map(_.user.email)
    val alreadyExistingUsers = userService.byEmails(userEmails)
    val newUsersFormDataList = userGroupFormData.users.map { userDataForm =>
      alreadyExistingUsers.find(_.email == userDataForm.user.email).fold {
        userDataForm
      } { alreadyExistingUser =>
        userDataForm.copy(user = userDataForm.user.copy(id = alreadyExistingUser.id), alreadyExistingUser = Some(alreadyExistingUser))
      }
    }
    groupService.groupByName(userGroupFormData.group.name).fold {
      userGroupFormData
    } { alreadyExistingGroup =>
      userGroupFormData.copy(
        group = userGroupFormData.group.copy(id = alreadyExistingGroup.id), 
        alreadyExistingGroup = Some(alreadyExistingGroup)
      )
    }.copy(users = newUsersFormDataList) 
  }

  def filterAlreadyExistingUsersAndGenerateErrors(groups: List[UserGroupFormData]): (List[String], List[UserGroupFormData]) = {
    val (newErrors, newGroups) = groups.foldLeft((List.empty[String], List.empty[UserGroupFormData])) { filterAlreadyExistingUsersAndGenerateErrors }
    newErrors.reverse -> newGroups.reverse
  }

  def filterAlreadyExistingUsersAndGenerateErrors(accu: (List[String], List[UserGroupFormData]), group: UserGroupFormData): (List[String], List[UserGroupFormData]) = {
    val (newUsers, existingUsers) = group.users.partition(_.alreadyExistingUser.isEmpty)
    val errors = existingUsers.map { (existingUser: UserFormData) =>
      s"${existingUser.user.name} (${existingUser.user.email}) existe déjà."
    }
    val newGroup = group.copy(users = newUsers)
    (errors ++ accu._1) -> (newGroup :: accu._2)
  }
  


  def csvLinesToUserGroupData(separator: Char, defaultAreas: Seq[Area], currentDate: DateTime)(csvLines: String): Either[String, (List[String], List[UserGroupFormData])] = {
    def partition(list: List[Either[String, UserGroupFormData]]): (List[String], List[UserGroupFormData]) = {
      val (errors, successes) = list.partition(_.isLeft)
      errors.map(_.left.get) -> successes.map(_.right.get)
    }
    
    extractFromCSVToMap(separator)(csvLines)
       .map { csvExtractResult: CSVExtractResult =>
          val result: List[Either[String, UserGroupFormData]] = csvExtractResult.map {
            case (lineNumber: LineNumber, csvMap: CSVMap, rawCSVLine: RawCSVLine) =>
              csvMap.trimValues
                  .csvCleanHeadersWithExpectedHeaders
                  .convertAreasNameToAreaUUID(defaultAreas)
                  .includeAreasNameInGroupName
                  .fromCsvFieldNameToHtmlFieldName
                  .includeFirstnameInLastName()
                  .toUserGroupData(lineNumber, currentDate).left.map { error: String =>
                 s"Ligne $lineNumber : error $error ( $rawCSVLine )"
              }
          }
          partition(result)
       }.map {
          case (linesErrorList: List[String], userGroupFormDataList: List[UserGroupFormData]) =>
          (linesErrorList, userGroupDataListToUserGroupData(userGroupFormDataList))
       }
    }

  def userImportMapping(date: DateTime): Mapping[User] = mapping(
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
    "admin" -> ignored(false),
    "areas" -> list(uuid).verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
    "creationDate" -> ignored(date),
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
    "phone-number" -> optional(text),
  )(User.apply)(User.unapply)

  private def groupImportMapping(date: DateTime): Mapping[UserGroup] = mapping(
    "id" -> ignored(UUID.randomUUID()),
    "name" -> text(maxLength = 60),
    "description" -> optional(text),
    "insee-code" -> list(text),
    "creationDate" -> ignored(date),
    "create-by-user-id" -> ignored(UUIDHelper.namedFrom("deprecated")),
    "area-ids" -> list(uuid).verifying("Vous devez sélectionner au moins 1 territoire", _.nonEmpty),
    "organisation" -> optional(text),
    "email" -> optional(email)
  )(UserGroup.apply)(UserGroup.unapply)

  def importUsersReviewFrom(date: DateTime): Form[List[UserGroupFormData]] = Form(
    single(
    "groups" -> list(
            mapping(
              "group" -> groupImportMapping(date),
              "users" -> list(
                  mapping(
                    "user" -> userImportMapping(date),
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
          csvLinesToUserGroupData(csvImportData.separator, defaultAreas, Time.now())(csvImportData.csvLines).fold({
            error: String =>
              val csvImportContentFormWithError = csvImportContentForm.fill(csvImportData).withGlobalError(error)
              BadRequest(views.html.importUsersCSV(request.currentUser)(csvImportContentFormWithError))
          }, {
            case (userNotImported: List[String], userGroupDataForm: List[UserGroupFormData]) =>
              val augmentedUserGroupInformation: List[UserGroupFormData] = userGroupDataForm.map(augmentUserGroupInformation)
              val (alreadyExistingUsersErrors, filteredUserGroupInformation) = filterAlreadyExistingUsersAndGenerateErrors(augmentedUserGroupInformation)

              
              val currentDate = Time.now()
              val formWithError = importUsersReviewFrom(currentDate).fillAndValidate(filteredUserGroupInformation)

              formWithError.withGlobalError("Certaines lignes du CSV n'ont pas pu être importé", userNotImported ++ alreadyExistingUsersErrors)
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
      val currentDate = Time.now()
      importUsersReviewFrom(currentDate).bindFromRequest.fold({ importUsersReviewFromWithError =>
        BadRequest
      }, { userGroupDataForm: List[UserGroupFormData] =>
        val augmentedUserGroupInformation: List[UserGroupFormData] = userGroupDataForm.map(augmentUserGroupInformation)

        val groupsToInsert = augmentedUserGroupInformation.filter(_.alreadyExistingGroup.isEmpty).map(_.group)
        if (!groupService.add(groupsToInsert)) {
          //TODO : catch exception : groupe name already exist
          val description = s"Impossible d'importer les groupes"
          eventService.error("IMPORT_USER_ERROR", description)
          val formWithError = importUsersReviewFrom(currentDate).fill(augmentedUserGroupInformation).withGlobalError(description)
          InternalServerError(views.html.reviewUsersImport(request.currentUser)(formWithError))
        } else {
          val usersToInsert = augmentedUserGroupInformation.flatMap(_.users).filter(_.alreadyExistingUser.isEmpty).map(_.user)
          if (!userService.add(usersToInsert)) {
            //TODO : catch exception : email already exist
            val description = s"Impossible de mettre à des utilisateurs à l'importation."
            eventService.error("IMPORT_USER_ERROR", description)
            val formWithError = importUsersReviewFrom(currentDate).fill(augmentedUserGroupInformation).withGlobalError(description)
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
      })
    }
  }
}


 //     NotImplemented
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

