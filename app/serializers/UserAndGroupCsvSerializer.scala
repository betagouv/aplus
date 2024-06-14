package serializers

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import helper.{PlayFormHelpers, UUIDHelper}
import helper.StringHelper._
import java.util.UUID
import models.{Area, Organisation}
import models.forms.AddUserFormData
import play.api.data.Forms._
import play.api.data.Mapping
import scala.io.Source

object UserAndGroupCsvSerializer {

  case class ParsedUserGroup(
      name: String,
      description: Option[String],
      areaIds: List[UUID],
      organisationId: Option[Organisation.Id],
      email: Option[String]
  )

  case class ParsedUser(
      userData: AddUserFormData,
      line: Int
  )

  case class UserGroupBlock(
      group: ParsedUserGroup,
      users: List[ParsedUser]
  )

  case class Header(key: String, prefixes: List[String]) {
    val lowerPrefixes: List[String] = prefixes.map(_.toLowerCase().stripSpecialChars)
  }

  val USER_FIRST_NAME: Header = Header("user.firstName", List("Prénom", "Prenom"))
  val USER_LAST_NAME: Header = Header("user.lastName", List("Nom", "Nom"))

  val USER_EMAIL: Header =
    Header("user.email", List("Email", "Adresse e-mail", "Contact mail Agent", "MAIL"))

  val USER_INSTRUCTOR: Header = Header("user.instructor", List("Instructeur"))
  val USER_GROUP_MANAGER: Header = Header("user.groupAdmin", List("Responsable"))
  val USER_QUALITY: Header = Header("user.qualite", List("Qualité"))

  val USER_PHONE_NUMBER: Header =
    Header("user.phoneNumber", List("Numéro de téléphone", "téléphone"))

  val USER_ACCOUNT_IS_SHARED: Header =
    Header("user." + Keys.User.sharedAccount, List("Compte Partagé"))

  val SHARED_ACCOUNT_NAME: Header =
    Header(
      "user." + Keys.User.sharedAccountName,
      List("Compte partagé", "Nom du compte partagé")
    )

  val GROUP_AREAS_IDS: Header = Header("group.area-ids", List("Territoire", "DEPARTEMENTS"))
  val GROUP_ORGANISATION: Header = Header("group.organisation", List("Organisation"))

  val GROUP_NAME: Header =
    Header(
      "group.name",
      List("Groupe", "Opérateur partenaire")
    ) // "Nom de la structure labellisable"
  val GROUP_EMAIL: Header = Header("group.email", List("Bal", "adresse mail générique"))

  val SEPARATOR = ";"

  val USER_HEADERS: List[Header] = List(
    USER_PHONE_NUMBER,
    USER_FIRST_NAME,
    USER_LAST_NAME,
    USER_EMAIL,
    USER_INSTRUCTOR,
    USER_GROUP_MANAGER,
    SHARED_ACCOUNT_NAME
  )

  val USER_HEADER: String = USER_HEADERS.map(_.prefixes.head).mkString(SEPARATOR)

  val GROUP_HEADERS: List[Header] =
    List(GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL, GROUP_AREAS_IDS)

  val GROUP_HEADER: String = GROUP_HEADERS.map(_.prefixes.head).mkString(SEPARATOR)

  type UUIDGenerator = () => UUID

  type LineNumber = Int
  type CSVMap = Map[String, String]
  type RawCSVLine = String
  type CSVExtractResult = List[(LineNumber, CSVMap, RawCSVLine)]

  private val expectedUserHeaders: List[Header] = List(
    USER_PHONE_NUMBER,
    USER_FIRST_NAME,
    USER_LAST_NAME,
    USER_EMAIL,
    USER_INSTRUCTOR,
    USER_GROUP_MANAGER,
    SHARED_ACCOUNT_NAME
  )

  private val expectedGroupHeaders: List[Header] =
    List(GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL, GROUP_AREAS_IDS)

  private def mergeBySameGroups(
      userGroupFormData: List[UserGroupBlock]
  ): List[UserGroupBlock] =
    userGroupFormData
      .groupBy(_.group.name.stripSpecialChars)
      .view
      .mapValues { (sameGroupNameList: List[UserGroupBlock]) =>
        val group = sameGroupNameList.head
        val usersFormData = sameGroupNameList.flatMap(_.users)
        group.copy(users = usersFormData)
      }
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
        .map { case ((csvMap: CSVMap, rawCSVLine: RawCSVLine), lineNumber: Int) =>
          (lineNumber, csvMap, rawCSVLine)
        }
      Right(result)
    } catch {
      case ex: com.github.tototoshi.csv.MalformedCSVException =>
        Left(s"Erreur lors de l'extraction du csv ${ex.getMessage}")
    }

  /** Only public method. Returns a Right[(List[String], List[CSVUserGroupFormData]))] where
    * List[String] is a list of errors on the lines.
    */
  def csvLinesToUserGroupData(separator: Char, defaultAreas: Seq[Area])(
      csvLines: String
  ): Either[String, (List[String], List[UserGroupBlock])] = {
    def partition(
        list: List[Either[String, UserGroupBlock]]
    ): (List[String], List[UserGroupBlock]) = {
      val (errors, successes) = list.partition(_.isLeft)
      errors.flatMap(_.swap.toOption) -> successes.flatMap(_.toOption)
    }

    extractFromCSVToMap(separator)(csvLines)
      .map { (csvExtractResult: CSVExtractResult) =>
        val result: List[Either[String, UserGroupBlock]] = csvExtractResult.map {
          case (lineNumber: LineNumber, csvMap: CSVMap, rawCSVLine: RawCSVLine) =>
            csvMap
              .trimValues()
              .csvCleanHeadersWithExpectedHeaders()
              .convertAreasNameToAreaUUID(defaultAreas)
              .convertBooleanValue(UserAndGroupCsvSerializer.USER_GROUP_MANAGER.key, "Responsable")
              .convertBooleanValue(UserAndGroupCsvSerializer.USER_INSTRUCTOR.key, "Instructeur")
              .includeAreasNameInGroupName()
              .matchOrganisationId
              .fromCsvFieldNameToHtmlFieldName
              .toUserGroupData(lineNumber)
              .left
              .map { (error: String) =>
                s"Il y au moins une erreur à la ligne $lineNumber : $error (ligne initale : $rawCSVLine )"
              }
        }
        partition(result)
      }
      .map { case (linesErrorList: List[String], userGroupFormDataList: List[UserGroupBlock]) =>
        (linesErrorList, mergeBySameGroups(userGroupFormDataList))
      }
  }

  implicit class CSVMapPreprocessing(csvMap: CSVMap) {

    def csvCleanHeadersWithExpectedHeaders(): CSVMap = {
      def convertToPrefixForm(
          values: CSVMap,
          expectedHeaders: List[Header]
      ): CSVMap =
        values
          .map { case (key, value) =>
            val lowerKey = key.trim.toLowerCase.stripSpecialChars
            expectedHeaders
              // TODO : Weird bug here to correct (eg : can't use two column named "nom" and "nomducomptepartage" because of the startWith :(
              .find(expectedHeader => expectedHeader.lowerPrefixes.exists(lowerKey.startsWith))
              .map(expectedHeader => expectedHeader.key -> value)
          }
          .flatten
          .toMap

      convertToPrefixForm(csvMap, expectedGroupHeaders) ++ convertToPrefixForm(
        csvMap,
        expectedUserHeaders,
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

          val detectedAreas: List[Area] = (
            if (inseeCodes.nonEmpty) inseeCodes.flatMap(Area.fromInseeCode)
            else areas.split(",").flatMap(_.split(";")).flatMap(Area.searchFromName).toList
          ).distinct

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
          csvMap + (key -> value.toLowerCase.stripSpecialChars
            .contains(trueValue.toLowerCase.stripSpecialChars)
            .toString)
        }

    def includeAreasNameInGroupName(): CSVMap = {
      val optionalAreaNames: Option[List[String]] = csvMap
        .get(UserAndGroupCsvSerializer.GROUP_AREAS_IDS.key)
        .map({ (ids: String) =>
          ids.split(",").flatMap(UUIDHelper.fromString).flatMap(Area.fromId).toList.map(_.name)
        })
      optionalAreaNames -> csvMap.get(UserAndGroupCsvSerializer.GROUP_NAME.key) match {
        case (Some(areaNames), Some(initialGroupName))
            if !initialGroupName.contains(s" ${areaNames.mkString("/")}") =>
          csvMap + (UserAndGroupCsvSerializer.GROUP_NAME.key -> s"$initialGroupName - ${areaNames.mkString("/")}")
        case _ =>
          csvMap
      }
    }

    def matchOrganisationId: CSVMap = {
      val optionOrganisation = csvMap
        .get(GROUP_ORGANISATION.key)
        .orElse(csvMap.get(GROUP_NAME.key))
        .flatMap(Organisation.deductedFromName)
      optionOrganisation match {
        case Some(organisation) =>
          csvMap + (GROUP_ORGANISATION.key -> organisation.id.id)
        case None =>
          csvMap - GROUP_ORGANISATION.key
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
            .map { case (areaUuid, index) =>
              s"${GROUP_AREAS_IDS.key}[$index]" -> areaUuid
            }
          (csvMap - GROUP_AREAS_IDS.key) ++ newTuples
        })

    def trimValues(): CSVMap = csvMap.view.mapValues(_.trim).toMap

    def toUserGroupData(lineNumber: LineNumber): Either[String, UserGroupBlock] =
      groupCSVMapping
        .bind(csvMap)
        .fold(
          errors => Left(errors.map(PlayFormHelpers.prettifyFormError).mkString(", ")),
          group =>
            addUsersForm
              .bind(csvMap)
              .fold(
                errors => Left(errors.map(PlayFormHelpers.prettifyFormError).mkString(", ")),
                user =>
                  Right(
                    UserGroupBlock(
                      group,
                      List(ParsedUser(user, lineNumber)),
                    )
                  )
              )
        )

  }

  private val addUsersForm: Mapping[AddUserFormData] =
    single(
      "user" -> AddUserFormData.formMapping
    )

  private val groupCSVMapping: Mapping[ParsedUserGroup] =
    single(
      "group" ->
        mapping(
          "name" -> text,
          "description" -> optional(text),
          "area-ids" -> list(uuid),
          "organisation" -> optional(of[Organisation.Id]),
          "email" -> optional(email)
        )(ParsedUserGroup.apply)(ParsedUserGroup.unapply)
    )

}
