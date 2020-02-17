package serializers

import java.util.UUID

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import models.formModels.{UserFormData, UserGroupFormData}
import helper.{PlayFormHelper, UUIDHelper}
import helper.StringHelper._
import models.{Area, Organisation, User, UserGroup}
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.data.Mapping

import scala.io.Source

object UserAndGroupCsvSerializer {

  case class Header(key: String, prefixes: List[String]) {
    val lowerPrefixes = prefixes.map(_.toLowerCase())
  }

  val USER_NAME = Header("user.name", List("Nom", "PRENOM NOM"))
  val USER_FIRST_NAME = Header("user.firstname", List("Prénom", "Prenom"))

  val USER_EMAIL =
    Header("user.email", List("Email", "Adresse e-mail", "Contact mail Agent", "MAIL"))
  val USER_INSTRUCTOR = Header("user.instructor", List("Instructeur"))
  val USER_GROUP_MANAGER = Header("user.admin-group", List("Responsable"))
  val USER_QUALITY = Header("user.quality", List("Qualité"))
  val USER_PHONE_NUMBER = Header("user.phone-number", List("Numéro de téléphone", "téléphone"))

  val GROUP_AREAS_IDS = Header("group.area-ids", List("Territoire", "DEPARTEMENTS"))
  val GROUP_ORGANISATION = Header("group.organisation", List("Organisation"))
  val GROUP_NAME = Header("group.name", List("Groupe", "Opérateur partenaire")) // "Nom de la structure labellisable"
  val GROUP_EMAIL = Header("group.email", List("Bal", "adresse mail générique"))

  val SEPARATOR = ";"

  val USER_HEADERS = List(
    USER_PHONE_NUMBER,
    USER_FIRST_NAME,
    USER_NAME,
    USER_EMAIL,
    USER_INSTRUCTOR,
    USER_GROUP_MANAGER
  )
  val USER_HEADER = USER_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  val GROUP_HEADERS = List(GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL, GROUP_AREAS_IDS)
  val GROUP_HEADER = GROUP_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  type UUIDGenerator = () => UUID

  type LineNumber = Int
  type CSVMap = Map[String, String]
  type RawCSVLine = String
  type CSVExtractResult = List[(LineNumber, CSVMap, RawCSVLine)]

  private val expectedUserHeaders: List[Header] = List(
    USER_PHONE_NUMBER,
    USER_FIRST_NAME,
    USER_NAME,
    USER_EMAIL,
    USER_INSTRUCTOR,
    USER_GROUP_MANAGER,
    USER_QUALITY
  )

  private val expectedGroupHeaders: List[Header] =
    List(GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL, GROUP_AREAS_IDS)

  private def filterAlreadyExistingUsersAndGenerateErrors(
      groups: List[UserGroupFormData]
  ): (List[String], List[UserGroupFormData]) = {
    def filterAlreadyExistingUsersAndGenerateErrors(
        accu: (List[String], List[UserGroupFormData]),
        group: UserGroupFormData
    ): (List[String], List[UserGroupFormData]) = {
      val (newUsers, existingUsers) = group.users.partition(_.alreadyExistingUser.isEmpty)
      val errors = existingUsers.map { (existingUser: UserFormData) =>
        s"${existingUser.user.name} (${existingUser.user.email}) existe déjà."
      }
      val newGroup = group.copy(users = newUsers)
      (errors ++ accu._1) -> (newGroup :: accu._2)
    }

    val (newErrors, newGroups) =
      groups.foldLeft((List.empty[String], List.empty[UserGroupFormData])) {
        filterAlreadyExistingUsersAndGenerateErrors
      }
    newErrors.reverse -> newGroups.reverse
  }

  private def userGroupDataListToUserGroupData(
      userGroupFormData: List[UserGroupFormData]
  ): List[UserGroupFormData] =
    userGroupFormData
      .groupBy(_.group.name.stripSpecialChars)
      .mapValues({
        case sameGroupNameList: List[UserGroupFormData] =>
          val group = sameGroupNameList.head
          val usersFormData = sameGroupNameList.flatMap(_.users)
          group.copy(users = usersFormData)
      })
      .values
      .toList

  private def extractFromCSVToMap(
      separator: Char
  )(csvText: String): Either[String, CSVExtractResult] =
    try {
      implicit object SemiConFormat extends DefaultCSVFormat {
        override val delimiter: Char = separator
      }
      def recreateRawCSVLine(line: List[String]): RawCSVLine =
        line.mkString(SemiConFormat.delimiter.toString)

      val csvReader = CSVReader.open(Source.fromString(csvText))
      val headers = csvReader.readNext()
      val result = headers
        .map { headers =>
          val lines = csvReader.all().filter(_.reduce(_ + _).nonEmpty)
          lines.map(line => headers.zip(line).toMap -> recreateRawCSVLine(line))
        }
        .getOrElse(Nil)
        .zipWithIndex
        .map {
          case ((csvMap: CSVMap, rawCSVLine: RawCSVLine), lineNumber: Int) =>
            (lineNumber, csvMap, rawCSVLine)
        }
      Right(result)
    } catch {
      case ex: com.github.tototoshi.csv.MalformedCSVException =>
        Left(s"Erreur lors de l'extraction du csv ${ex.getMessage}")
    }

  /** Only public method.
    * Returns a Right[(List[String], List[UserGroupFormData]))]
    * where List[String] is a list of errors on the lines.
    */
  def csvLinesToUserGroupData(separator: Char, defaultAreas: Seq[Area], currentDate: DateTime)(
      csvLines: String
  ): Either[String, (List[String], List[UserGroupFormData])] = {
    def partition(
        list: List[Either[String, UserGroupFormData]]
    ): (List[String], List[UserGroupFormData]) = {
      val (errors, successes) = list.partition(_.isLeft)
      errors.map(_.left.get) -> successes.map(_.right.get)
    }

    extractFromCSVToMap(separator)(csvLines)
      .map { csvExtractResult: CSVExtractResult =>
        val result: List[Either[String, UserGroupFormData]] = csvExtractResult.map {
          case (lineNumber: LineNumber, csvMap: CSVMap, rawCSVLine: RawCSVLine) =>
            csvMap.trimValues.csvCleanHeadersWithExpectedHeaders
              .convertAreasNameToAreaUUID(defaultAreas)
              .convertBooleanValue(UserAndGroupCsvSerializer.USER_GROUP_MANAGER.key, "Responsable")
              .convertBooleanValue(UserAndGroupCsvSerializer.USER_INSTRUCTOR.key, "Instructeur")
              .includeAreasNameInGroupName
              .fromCsvFieldNameToHtmlFieldName
              .includeFirstnameInLastName()
              .setDefaultQualityIfNeeded()
              .toUserGroupData(lineNumber, currentDate)
              .left
              .map { error: String =>
                s"Il y au moins une erreur à la ligne $lineNumber : $error (ligne initale : $rawCSVLine )"
              }
        }
        partition(result)
      }
      .map {
        case (linesErrorList: List[String], userGroupFormDataList: List[UserGroupFormData]) =>
          (linesErrorList, userGroupDataListToUserGroupData(userGroupFormDataList))
      }
  }

  implicit class CSVMapPreprocessing(csvMap: CSVMap) {

    def csvCleanHeadersWithExpectedHeaders(): CSVMap = {
      def convertToPrefixForm(
          values: CSVMap,
          expectedHeaders: List[Header],
          formPrefix: String
      ): CSVMap =
        values
          .map({
            case (key, value) =>
              val lowerKey = key.trim.toLowerCase
              expectedHeaders
                .find(expectedHeader => expectedHeader.lowerPrefixes.exists(lowerKey.startsWith))
                .map(expectedHeader => expectedHeader.key -> value)
          })
          .flatten
          .toMap

      convertToPrefixForm(csvMap, expectedGroupHeaders, "group.") ++ convertToPrefixForm(
        csvMap,
        expectedUserHeaders,
        "user."
      )
    }

    def stringToInseeCodeList(string: String): List[String] = {
      val inseeCodeRegex = "[0-9AB]{2,3}".r
      inseeCodeRegex.findAllIn(string).toList
    }

    def convertAreasNameToAreaUUID(defaultAreas: Seq[Area]): CSVMap = {
      val newAreas: Seq[Area] = csvMap.get(UserAndGroupCsvSerializer.GROUP_AREAS_IDS.key) match {
        case Some(areas) =>
          val inseeCodes = stringToInseeCodeList(areas)

          val detectedAreas: Seq[Area] = if (inseeCodes.nonEmpty) {
            inseeCodes.flatMap(Area.fromInseeCode).toList
          } else {
            areas.split(",").flatMap(_.split(";")).flatMap(Area.searchFromName)
          }.distinct

          if (detectedAreas.isEmpty)
            defaultAreas
          else
            detectedAreas
        case None =>
          defaultAreas
      }
      csvMap + (UserAndGroupCsvSerializer.GROUP_AREAS_IDS.key -> newAreas
        .map(_.id.toString)
        .mkString(","))
    }

    def convertBooleanValue(key: String, trueValue: String): CSVMap =
      csvMap
        .get(key)
        .fold {
          csvMap
        } { value =>
          csvMap + (key -> (value.toLowerCase().contains(trueValue.toLowerCase())).toString)
        }

    def includeAreasNameInGroupName(): CSVMap = {
      val optionalAreaNames: Option[List[String]] = csvMap
        .get(UserAndGroupCsvSerializer.GROUP_AREAS_IDS.key)
        .map({ ids: String =>
          ids.split(",").flatMap(UUIDHelper.fromString).flatMap(Area.fromId).toList.map(_.name)
        })
      // TODO: Only if the groupName dont include the area
      (optionalAreaNames -> csvMap.get(UserAndGroupCsvSerializer.GROUP_NAME.key)) match {
        case (Some(areaNames), Some(initialGroupName)) =>
          csvMap + (UserAndGroupCsvSerializer.GROUP_NAME.key -> s"$initialGroupName - ${areaNames.mkString("/")}")
        case _ =>
          csvMap
      }
    }

    def fromCsvFieldNameToHtmlFieldName: CSVMap =
      csvMap
        .get(UserAndGroupCsvSerializer.GROUP_AREAS_IDS.key)
        .fold({
          csvMap
        })({ areasValue =>
          val newTuples: Array[(String, String)] = areasValue
            .split(",")
            .zipWithIndex
            .map({
              case (areaUuid, index) =>
                s"${GROUP_AREAS_IDS.key}[$index]" -> areaUuid
            })
          (csvMap - GROUP_AREAS_IDS.key) ++ newTuples
        })

    def includeFirstnameInLastName(): CSVMap =
      (csvMap.get(USER_FIRST_NAME.key), csvMap.get(USER_NAME.key)) match {
        case (Some(firstName), Some(lastName)) =>
          csvMap + (USER_NAME.key -> s"${lastName} ${firstName}")
        case _ =>
          csvMap
      }

    def setDefaultQualityIfNeeded(): CSVMap = {
      val defaultUserQuality = csvMap.getOrElse(GROUP_NAME.key, "")
      csvMap
        .get(USER_QUALITY.key)
        .fold {
          csvMap + (USER_QUALITY.key -> defaultUserQuality)
        } { quality =>
          if (quality.isEmpty)
            csvMap + (USER_QUALITY.key -> defaultUserQuality)
          else
            csvMap
        }
    }

    def trimValues(): CSVMap = csvMap.mapValues(_.trim)

    def toUserGroupData(
        lineNumber: LineNumber,
        currentDate: DateTime
    ): Either[String, UserGroupFormData] =
      groupCSVMapping(currentDate)
        .bind(csvMap)
        .fold(
          { errors =>
            Left(errors.map(PlayFormHelper.prettifyFormError).mkString(", "))
          }, { group =>
            userCSVMapping(currentDate)
              .bind(csvMap)
              .fold(
                { errors =>
                  Left(errors.map(PlayFormHelper.prettifyFormError).mkString(", "))
                }, { user =>
                  Right(
                    UserGroupFormData(
                      group,
                      List(UserFormData(user, lineNumber, alreadyExists = false)),
                      alreadyExistsOrAllUsersAlreadyExist = false,
                      doNotInsert = false
                    )
                  )
                }
              )
          }
        )
  }

  private def userCSVMapping(currentDate: DateTime): Mapping[User] = single(
    "user" -> mapping(
      "id" -> optional(uuid).transform[UUID]({
        case None     => UUID.randomUUID()
        case Some(id) => id
      }, {
        Some(_)
      }),
      "key" -> ignored("key"),
      "name" -> nonEmptyText,
      "quality" -> default(text, ""),
      "email" -> nonEmptyText,
      "helper" -> ignored(true),
      "instructor" -> boolean,
      "admin" -> ignored(false),
      "area-ids" -> ignored(List.empty[UUID]),
      "creationDate" -> ignored(currentDate),
      "communeCode" -> ignored("0"),
      "admin-group" -> boolean,
      "disabled" -> ignored(false),
      "expert" -> ignored(false),
      "groupIds" -> default(list(uuid), Nil),
      "cguAcceptationDate" -> ignored(Option.empty[DateTime]),
      "newsletterAcceptationDate" -> ignored(Option.empty[DateTime]),
      "phone-number" -> optional(text)
    )(User.apply)(User.unapply)
  )

  private def groupCSVMapping(currentDate: DateTime): Mapping[UserGroup] = single(
    "group" ->
      mapping(
        "id" -> optional(uuid).transform[UUID]({
          case None     => UUID.randomUUID()
          case Some(id) => id
        }, {
          Some(_)
        }),
        "name" -> text,
        "description" -> optional(text),
        "insee-code" -> list(text),
        "creationDate" -> ignored(currentDate),
        "area-ids" -> list(uuid),
        "organisation" -> optional(of[Organisation.Id]),
        "email" -> optional(email)
      )(UserGroup.apply)(UserGroup.unapply)
  )
}
