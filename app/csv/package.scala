import java.util.UUID

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.{Operators, Time}
import models.{Area, Organisation, User, UserGroup}
import org.joda.time.DateTime
import play.api.data.Forms.{boolean, default, email, ignored, list, mapping, nonEmptyText, optional, seq, text, tuple, uuid}
import play.api.data.validation.Constraints.{maxLength, nonEmpty}
import play.api.data.{Form, FormError, Mapping}

import scala.io.Source

package object csv {

  case class Header(key: String, prefixes: List[String]) {
    val lowerPrefixes = prefixes.map(_.toLowerCase())
  }

  val USER_LAST_NAME = Header("user_lastname", List("Nom"))
  val USER_FIRST_NAME = Header("user_firstname", List("Prénom"))
  val USER_EMAIL = Header("user_email", List("Email", "Adresse e-mail", "Contact mail Agent"))
  val INSTRUCTOR = Header("user_instructor", List("Instructeur"))
  val GROUP_MANAGER = Header("user_group_manager", List("Responsable"))
  val USER_PHONE_NUMBER = Header("user_phone_number", List("Numéro de téléphone", "téléphone"))

  val GROUP_AREA = Header("group_area", List("Territoire"))
  val GROUP_ORGANISATION = Header("group_organisation", List("Organisation"))
  val GROUP_NAME = Header("group_name", List("Groupe", "Opérateur partenaire", "Nom de la structure labellisable"))
  val GROUP_EMAIL = Header("group_email", List("Bal", "adresse mail générique"))

  val SEPARATOR = ";"

  val USER_HEADERS = List(USER_PHONE_NUMBER, USER_FIRST_NAME, USER_LAST_NAME, USER_EMAIL, INSTRUCTOR, GROUP_MANAGER)
  val USER_HEADER = USER_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  val GROUP_HEADERS = List(GROUP_AREA, GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL)
  val GROUP_HEADER = GROUP_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  type UUIDGenerator = () => UUID

  def groupNamePreprocessing(groupName: String): String =
    groupName.replaceFirst("Min. Intérieur", "Préfecture")

  // CSV import mapping
  def groupMappingForCSVImport(uuidGenerator: UUIDGenerator)(creatorId: UUID)(currentDate: DateTime): Mapping[UserGroup] =
    mapping(
      "id" -> optional(uuid).transform[UUID](uuid => uuid.getOrElse(uuidGenerator()), uuid => Some(uuid)),
      GROUP_NAME.key -> nonEmptyText.verifying(maxLength(60)).transform[String](groupNamePreprocessing, identity),
      "description" -> ignored(Option.empty[String]),
      "inseeCode" -> ignored(List.empty[String]),
      "creationDate" -> ignored(currentDate),
      "createByUserId" -> optional(uuid).transform[UUID](uuid => uuid.getOrElse(creatorId), uuid => Some(uuid)),
      GROUP_AREA.key -> optional(text).transform[UUID]({ os =>
        os.fold(Area.allArea.id)({ s =>
          s.split(",").map({ t: String =>
            val area = canonizeArea(t.split(" ")(0))
            Area.all.find(a => canonizeArea(a.name.split(" ")(0)) == area)
              .map(_.id)
              .getOrElse(Area.allArea.id)
          }).toList.head
        })
      }, uuid => Some(uuid.toString)),

      GROUP_ORGANISATION.key -> optional(nonEmptyText)
        .transform[Option[String]](_.flatMap(name => {
          val organisation = Organisation.fromShortName(name)
          if (organisation.isDefined) organisation.map(_.shortName)
          else Organisation.fromName(name).map(_.shortName)
        }), identity),
      GROUP_EMAIL.key -> optional(email.verifying(maxLength(200), nonEmpty)),
    )(UserGroup.apply)(UserGroup.unapply)

  def userMappingForCVSImport(userId: UUIDGenerator, dateTime: DateTime): Mapping[User] = mapping(
    "id" -> optional(uuid).transform[UUID](uuid => uuid.getOrElse(userId()), uuid => Some(uuid)),
    "key" -> default(nonEmptyText, "key"),
    USER_LAST_NAME.key -> nonEmptyText.verifying(maxLength(100)),
    USER_FIRST_NAME.key -> optional(text),
    USER_PHONE_NUMBER.key -> optional(text),
    USER_EMAIL.key -> email.verifying(maxLength(200), nonEmpty),
    "Aidant" -> ignored(true),
    INSTRUCTOR.key -> optional(text.verifying(s => INSTRUCTOR.lowerPrefixes.exists(s.toLowerCase.startsWith) || s.toLowerCase() == "false"  || s.toLowerCase() == "true" || s.isEmpty))
      .transform[Boolean](os => os.exists(s => INSTRUCTOR.lowerPrefixes.exists(s.toLowerCase.startsWith) || s.toLowerCase() == "false"  || s.toLowerCase() == "true"), manager => if (manager) Some("true") else Some("false")),


    "admin" -> ignored(false),
    "areas" -> default(list(uuid).verifying("Vous devez sélectionner au moins un territoire", _.nonEmpty),
      List.empty[UUID]),
    "creationDate" -> ignored(dateTime),
    "hasAcceptedCharte" -> default(boolean, false),
    "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),

    GROUP_MANAGER.key -> optional(text.verifying(s => GROUP_MANAGER.lowerPrefixes.exists(s.toLowerCase.startsWith) || s.toLowerCase() == "false"  || s.toLowerCase() == "true" || s.isEmpty))
      .transform[Boolean](os => os.exists(s => GROUP_MANAGER.lowerPrefixes.exists(s.toLowerCase.startsWith) || s.toLowerCase() == "false"  || s.toLowerCase() == "true"), manager => if (manager) Some("true") else Some("false")),

    "disabled" -> ignored(false),
    "expert" -> ignored(false),
    "groupIds" -> default(list(uuid), List()),
    "delegations" -> default(seq(tuple("name" -> nonEmptyText, "email" -> email))
      .transform[Map[String, String]](_.toMap, _.toSeq), Map.empty[String, String]),
    "cguAcceptationDate" -> ignored(Option.empty[DateTime]),
    "newsletterAcceptationDate" -> ignored(Option.empty[DateTime])
  )(apply)(unapply)

  def apply(id: UUID, key: String, lastName: String, firstName: Option[String], phoneNumber: Option[String], email: String,
            helper: Boolean, instructor: Boolean, admin: Boolean, areas: List[UUID], creationDate: DateTime,
            hasAcceptedCharte: Boolean, communeCode: String, groupAdmin: Boolean, disabled: Boolean, expert: Boolean,
            groupIds: List[UUID], delegations: Map[String, String], cguAcceptationDate: Option[DateTime],
            newsLetterAcceptationDate: Option[DateTime]): User = User.apply(id, key,
    firstName.map(lastName + " " + _).getOrElse(lastName), "", email, helper, instructor, admin, areas,
    creationDate, hasAcceptedCharte, communeCode, groupAdmin, disabled, expert, groupIds, delegations,
    cguAcceptationDate, newsLetterAcceptationDate, phoneNumber)

  def unapply(user: User): Option[(UUID, String, String, Option[String], Option[String], String, Boolean, Boolean, Boolean, List[UUID],
    DateTime, Boolean, String, Boolean, Boolean, Boolean, List[UUID], Map[String, String], Option[DateTime],
    Option[DateTime])] = Some((user.id, user.key, user.name, None, user.phoneNumber, user.email, user.helper,
    user.instructor, user.admin, user.areas, user.creationDate, user.hasAcceptedCharte, user.communeCode,
    user.groupAdmin, user.disabled, user.expert, user.groupIds, user.delegations, user.cguAcceptationDate,
    user.newsletterAcceptationDate))

  def canonizeArea(area: String): String = area.toLowerCase().replaceAll("[-'’]", "")

  private def convertToPrefixForm(values: Map[String, String], headers: List[Header], formPrefix: String): Map[String, String] = {
    values.map({ case (key, value) =>
      val lowerKey = key.trim.toLowerCase
      headers.find(header => header.lowerPrefixes
        .exists(lowerKey.startsWith))
        .map(header => formPrefix + header.key -> value.trim)
    }).flatten.toMap
  }

  def sectionMapping(groupId: UUIDGenerator, userId: UUIDGenerator, creatorId: UUID, dateTime: DateTime): Mapping[Section] = mapping(
    "group" -> groupMappingForCSVImport(groupId)(creatorId)(dateTime),
    "users" -> list(userMappingForCVSImport(userId, dateTime))
  )(Section.apply)(Section.unapply)

  private def tupleMapping(groupIdGenerator: UUIDGenerator)(userIdGenerator: UUIDGenerator)(creatorId: UUID)(currentDateTime: DateTime) = mapping(
    "group" -> groupMappingForCSVImport(groupIdGenerator)(creatorId)(currentDateTime),
    "user" -> userMappingForCVSImport(userIdGenerator, currentDateTime)
  )((group: UserGroup, user: User) => group -> user)(tuple => Option(tuple._1 -> tuple._2))

  private def tupleForm(groupId: UUIDGenerator)(userId: UUIDGenerator)(creatorId: UUID)(dateTime: DateTime): Form[(UserGroup, User)] =
    Form.apply(tupleMapping(groupId)(userId)(creatorId)(dateTime))

  case class Section(group: UserGroup, users: List[User])

  def fromCSVLine(values: Map[String, String], groupHeaders: List[Header], userHeaders: List[Header], groupId: UUIDGenerator,
                  userId: UUIDGenerator, creatorId: UUID, dateTime: DateTime, completeLine: String): Either[(List[FormError], String), (UserGroup, User)] = {
    val form = tupleForm(groupId)(userId)(creatorId)(dateTime).bind(convertToPrefixForm(values, groupHeaders, "group.") ++ convertToPrefixForm(values, userHeaders, "user."))
    if (form.hasErrors)
      Left(form.errors.toList -> completeLine)
    else
      Right(form.value.get)
  }

  private def sectionsMapping(groupId: UUIDGenerator, userId: UUIDGenerator, creatorId: UUID, dateTime: DateTime): Mapping[(List[Section], UUID)] =
    mapping("sections" -> list(csv.sectionMapping(groupId, userId, creatorId, dateTime)),
      "area-selector" -> uuid.verifying(area =>
        Operators.not(List(Area.allArea, Area.notApplicable).map(_.id).contains(area))
      )
    )({ case (sections, area) => sections -> area })({ case (section, area) => Some(section -> area) })

  private def sectionsForm(groupId: UUIDGenerator, userId: UUIDGenerator, creatorId: UUID, dateTime: DateTime): Form[(List[Section], UUID)] =
    Form(sectionsMapping(groupId, userId, creatorId, dateTime))

  def sectionsForm(creatorId: UUID): Form[(List[Section], UUID)] =
    sectionsForm(UUID.randomUUID, UUID.randomUUID, creatorId, DateTime.now(Time.dateTimeZone))

  def allWithCompleteLine(csvReader: CSVReader)(implicit format:DefaultCSVFormat): List[(Map[String, String], String)] = {
    val headers = csvReader.readNext()
    headers.map(headers => {
      val lines = csvReader.all().filter(_.reduce(_+_).nonEmpty)
      lines.map(line => headers.zip(line).toMap -> line.mkString(format.delimiter.toString))
    }).getOrElse(Nil)
  }

  private def extractFromCSVToMap(csvText: String, separator: Char): List[((Map[String, String], String), Int)] = try {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = separator
    }
    val reader = CSVReader.open(Source.fromString(csvText))
    allWithCompleteLine(reader).zipWithIndex
  } catch {
    case _: com.github.tototoshi.csv.MalformedCSVException =>
      Nil
  }

  private def extractFromCSV(csvText: String, separator: Char, creatorId: UUID): List[Either[(Int, List[FormError], String), (UserGroup, User)]] = {
    val dateTime = Time.now()
    extractFromCSVToMap(csvText, separator).map { case ((data: Map[String, String], completeLine: String), lineNumber: Int) =>
      csv.fromCSVLine(data, GROUP_HEADERS, USER_HEADERS, UUID.randomUUID, UUID.randomUUID, creatorId, dateTime, completeLine)
        .left.map({ case (errors, completeLine) => (lineNumber + 1, errors, completeLine) })
    }
  }

  def extractValidInputAndErrors(csvImportContent: String, separator: Char, creatorId: UUID): (List[(UserGroup, List[User])], List[(Int, (List[FormError], String))]) = {
    val forms = extractFromCSV(csvImportContent, separator, creatorId)
    val lineNumberToErrors = forms.filter(_.isLeft).map(_.left.get)
      .map({ case (lineNumber, errors, completeLine) =>
        lineNumber -> (errors -> completeLine)
      }).sortBy(_._1)

    val deduplicatedEmail: List[((UserGroup, User), Int)] = forms.filter(_.isRight).map(_.right.get).zipWithIndex
      .groupBy({ case ((_, user), _) => user.email })
      .mapValues(_.head).values.toList.sortBy(_._2)

    // Group by group name and keep csv line order
    val groupToUsersMap: List[(UserGroup, List[User])] = deduplicatedEmail
      .groupBy({ case ((group, _), _) => group.name }) // Group by name
      .map({ case (_,list) => (list.head._1._1 -> list.map(_._2).min) -> list.sortBy(_._2).map(_._1._2) }) // Sort users
      .toList
      .sortBy(_._1._2) // sort groups
      .map({ case (key,value) => key._1 -> value }) // discard index
    groupToUsersMap -> lineNumberToErrors
  }

  def prepareUsers(users: List[User], group: UserGroup): List[User] = {
    val setGroup = { user: User =>
      user.copy(groupIds = (group.id :: user.groupIds).distinct)
    }
    val setAreas = { user: User =>
      user.copy(areas = (group.area :: user.areas).distinct)
    }
    users.map(setGroup.compose(setAreas).apply)
  }

  def prepareSection(group: UserGroup, users: List[User], area: Area): (UserGroup, List[User]) = {
    val finalGroup = group.copy(area = area.id)
    val finalUsers = prepareUsers(users, finalGroup)
    finalGroup -> finalUsers
  }

  val csvImportContentForm: Form[(String, UUID, String)] = Form(mapping(
    "csv-import-content" -> play.api.data.Forms.nonEmptyText,
    "area-selector" -> uuid.verifying(area => Operators.not(List(Area.allArea,Area.notApplicable).contains(area))),
    "separator" -> play.api.data.Forms.nonEmptyText.verifying(value => value.equals(";") || value.equals(",")))
  ({ (content, area, separator) => (content, area, separator) })({ case (content, area, separator) => Some((content, area, separator)) }))
}