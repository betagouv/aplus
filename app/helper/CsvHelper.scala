package helper

import csv._
import forms.Models.{UserFormData, UserGroupFormData}
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}

import scala.io.Source
import org.joda.time.DateTime
import models.{Area, User, UserGroup}
import extentions.UUIDHelper
import play.api.data.{Form, FormError, Mapping}
import play.api.data.Forms.{uuid, _}
import java.util.UUID

object CsvHelper {

  type LineNumber = Int
  type CSVMap = Map[String, String]
  type RawCSVLine = String
  type CSVExtractResult = List[(LineNumber, CSVMap, RawCSVLine)]


  private val expectedUserHeaders: List[Header] = List(USER_PHONE_NUMBER, USER_FIRST_NAME, USER_NAME, USER_EMAIL, USER_INSTRUCTOR, USER_GROUP_MANAGER)
  private val expectedGroupHeaders: List[Header] = List(GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL, GROUP_AREAS_IDS)

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
              .convertBooleanValue(csv.USER_GROUP_MANAGER.key, "Responsable")
              .convertBooleanValue(csv.USER_INSTRUCTOR.key, "Instructeur")
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

    implicit class CSVMapPreprocessing(csvMap: CSVMap) {
      def csvCleanHeadersWithExpectedHeaders(): CSVMap = {
        def convertToPrefixForm(values: CSVMap, expectedHeaders: List[Header], formPrefix: String): CSVMap = {
          values.map({ case (key, value) =>
            val lowerKey = key.trim.toLowerCase
            expectedHeaders.find(expectedHeader => expectedHeader.lowerPrefixes.exists(lowerKey.startsWith))
              .map(expectedHeader => expectedHeader.key -> value)
          }).flatten.toMap
        }
  
        convertToPrefixForm(csvMap, expectedGroupHeaders, "group.") ++ convertToPrefixForm(csvMap, expectedUserHeaders, "user.")
      }

      def convertAreasNameToAreaUUID(defaultAreas: Seq[Area]): CSVMap = {
        val newAreas: Seq[Area] = csvMap.get(csv.GROUP_AREAS_IDS.key) match {
          case Some(areas) =>
            val detectedAreas = areas.split(",").flatMap(_.split(";")).flatMap(_.split("-")).flatMap(Area.searchFromName).distinct
            if (detectedAreas.isEmpty)
              defaultAreas
            else
              detectedAreas
          case None =>
            defaultAreas
        }
        csvMap + (csv.GROUP_AREAS_IDS.key -> newAreas.map(_.id.toString).mkString(","))
      }
  
      def convertBooleanValue(key: String, trueValue: String): CSVMap = {
        csvMap.get(key).fold {
          csvMap
        } {  value =>
          csvMap + (key -> (value.toLowerCase().contains(trueValue.toLowerCase())).toString)
        }
      }
  
      def includeAreasNameInGroupName(): CSVMap = {
        val optionalAreaNames: Option[List[String]] = csvMap.get(csv.GROUP_AREAS_IDS.key).map({ ids: String =>
          ids.split(",").flatMap(UUIDHelper.fromString).flatMap(Area.fromId).toList.map(_.name)
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
          Left(errors.map(prettifyFormError).mkString(", "))
        }, { group =>
          userCSVMapping(currentDate).bind(csvMap).fold({ errors =>
            Left(errors.map(prettifyFormError).mkString(", "))
          }, { user =>
            Right(UserGroupFormData(group, List(UserFormData(user, lineNumber))))
          })
        })
      }
    }

  private def prettifyFormError(formError: FormError): String = {
    val prettyKey = formError.key.split(".").last
    val prettyMessages = formError.messages.map(_.split(".").last).mkString(", ")
    s"$prettyKey : $prettyMessages"
  }

  private def userCSVMapping(currentDate: DateTime): Mapping[User] = single(
      "user" -> mapping(
            "id" -> optional(uuid).transform[UUID]({
              case None => UUID.randomUUID()
              case Some(id) => id
            }, {
              Some(_)
            }),
            "key" -> ignored("key"),
            "name" -> nonEmptyText,
            "qualite" -> default(text, ""),
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
    )
  
    private def groupCSVMapping(currentDate: DateTime): Mapping[UserGroup] = single(
      "group" ->
        mapping(
          "id" -> ignored(UUID.randomUUID()),
          "name" -> text(maxLength = 60),
          "description" -> optional(text),
          "insee-code" -> list(text),
          "creationDate" -> ignored(currentDate),
          "create-by-user-id" -> ignored(UUIDHelper.namedFrom("deprecated")),
          "area-ids" -> list(uuid),
          "organisation" -> optional(text),
          "email" -> optional(email)
        )(UserGroup.apply)(UserGroup.unapply)
    )
}
