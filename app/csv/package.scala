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
  val USER_QUALITY = Header("user_quality", List("Qualité"))
  val USER_EMAIL = Header("user_email", List("Email", "Adresse e-mail", "Contact mail Agent"))
  val INSTRUCTOR = Header("user_instructor", List("Instructeur"))
  val GROUP_MANAGER = Header("user_group_manager", List("Responsable"))
  val USER_PHONE_NUMBER = Header("user_phone_number", List("Numéro de téléphone", "téléphone"))

  val GROUP_AREA = Header("group_area", List("Territoire"))
  val GROUP_ORGANISATION = Header("group_organisation", List("Organisation"))
  val GROUP_NAME = Header("group_name", List("Groupe", "Opérateur partenaire", "Nom de la structure labellisable"))
  val GROUP_EMAIL = Header("group_email", List("Bal", "adresse mail générique"))

  val SEPARATOR = ";"

  val USER_HEADERS = List(USER_PHONE_NUMBER, USER_FIRST_NAME, USER_LAST_NAME, USER_QUALITY, USER_EMAIL, INSTRUCTOR, GROUP_MANAGER)
  val USER_HEADER = USER_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  val GROUP_HEADERS = List(GROUP_AREA, GROUP_NAME, GROUP_ORGANISATION, GROUP_EMAIL)
  val GROUP_HEADER = GROUP_HEADERS.map(_.prefixes(0)).mkString(SEPARATOR)

  type UUIDGenerator = () => UUID

  // CSV import mapping
  def groupMappingForCSVImport(uuidGenerator: UUIDGenerator)(creatorId: UUID)(currentDate: DateTime): Mapping[UserGroup] =
    mapping(
      "id" -> optional(uuid).transform[UUID](uuid => uuid.getOrElse(uuidGenerator()),
        uuid => if (uuid == null) Some(uuidGenerator()) else Some(uuid)),
      GROUP_NAME.key -> nonEmptyText.verifying(maxLength(100)),
      "description" -> ignored(Option.empty[String]),
      "inseeCode" -> ignored(List.empty[String]),
      "creationDate" -> ignored(currentDate),
      "createByUserId" -> optional(uuid).transform[UUID](uuid => uuid.getOrElse(creatorId),
        uuid => if (uuid == null) Some(creatorId) else Some(uuid)),
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
    "id" -> optional(uuid).transform[UUID](uuid => uuid.getOrElse(userId()), uuid => if (uuid == null) Some(userId()) else Some(uuid)),
    "key" -> default(nonEmptyText, "key"),
    USER_LAST_NAME.key -> nonEmptyText.verifying(maxLength(100)),
    USER_FIRST_NAME.key -> optional(text),
    USER_PHONE_NUMBER.key -> optional(text),
    USER_QUALITY.key -> default(text, "").verifying(maxLength(100)),
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

  def apply(id: UUID, key: String, lastName: String, firstName: Option[String], phoneNumber: Option[String], qualite: String, email: String,
            helper: Boolean, instructor: Boolean, admin: Boolean, areas: List[UUID], creationDate: DateTime,
            hasAcceptedCharte: Boolean, communeCode: String, groupAdmin: Boolean, disabled: Boolean, expert: Boolean,
            groupIds: List[UUID], delegations: Map[String, String], cguAcceptationDate: Option[DateTime],
            newsLetterAcceptationDate: Option[DateTime]): User = User.apply(id, key,
    firstName.map(_ + " " + lastName).getOrElse(lastName), qualite, email, helper, instructor, admin, areas,
    creationDate, hasAcceptedCharte, communeCode, groupAdmin, disabled, expert, groupIds, delegations,
    cguAcceptationDate, newsLetterAcceptationDate, phoneNumber)

  def unapply(user: User): Option[(UUID, String, String, Option[String], Option[String], String, String, Boolean, Boolean, Boolean, List[UUID],
    DateTime, Boolean, String, Boolean, Boolean, Boolean, List[UUID], Map[String, String], Option[DateTime],
    Option[DateTime])] = Some((user.id, user.key, user.name, None, user.phoneNumber, user.qualite, user.email, user.helper,
    user.instructor, user.admin, user.areas, user.creationDate, user.hasAcceptedCharte, user.communeCode,
    user.groupAdmin, user.disabled, user.expert, user.groupIds, user.delegations, user.cguAcceptationDate,
    user.newsletterAcceptationDate))

  def canonizeArea(area: String): String = area.toLowerCase().replaceAll("[-'’]", "")

  private def convertToPrefixForm(values: Map[String, String], headers: List[Header], formPrefix: String): Map[String, String] = {
    values.map({ case (key, value) =>
      val lowerKey = key.toLowerCase
      headers.find(header => header.lowerPrefixes
        .exists(lowerKey.startsWith))
        .map(header => formPrefix + header.key -> value)
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
                  userId: UUIDGenerator, creatorId: UUID, dateTime: DateTime): Either[List[FormError], (UserGroup, User)] = {
    val form = tupleForm(groupId)(userId)(creatorId)(dateTime).bind(convertToPrefixForm(values, groupHeaders, "group.") ++ convertToPrefixForm(values, userHeaders, "user."))
    if (form.hasErrors)
      Left(form.errors.toList)
    else
      Right(form.value.get)
  }

  private def sectionsMapping(groupId: UUIDGenerator, userId: UUIDGenerator, creatorId: UUID, dateTime: DateTime): Mapping[(List[Section], UUID)] =
    mapping("sections" -> list(csv.sectionMapping(groupId, userId, creatorId, dateTime)),
      "area-selector" -> uuid
    )({ case (sections, area) => sections -> area })({ case (section, area) => Some(section -> area) })

  private def sectionsForm(groupId: UUIDGenerator, userId: UUIDGenerator, creatorId: UUID, dateTime: DateTime): Form[(List[Section], UUID)] =
    Form(sectionsMapping(groupId, userId, creatorId, dateTime))

  def sectionsForm(creatorId: UUID): Form[(List[Section], UUID)] =
    sectionsForm(UUID.randomUUID, UUID.randomUUID, creatorId, DateTime.now(Time.dateTimeZone))

  private def extractFromCSVToMap(csvText: String, separator: Char): List[(Map[String, String], Int)] = try {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter: Char = separator
    }
    val reader = CSVReader.open(Source.fromString(csvText))
    reader.allWithHeaders().zipWithIndex
  } catch {
    case _: com.github.tototoshi.csv.MalformedCSVException =>
      Nil
  }

  private def extractFromCSV(csvText: String, separator: Char, creatorId: UUID): List[Either[(Int, List[FormError]), (UserGroup, User)]] = {
    val dateTime = Time.now()
    extractFromCSVToMap(csvText, separator).map { case (data: Map[String, String], lineNumber: Int) =>
      csv.fromCSVLine(data, GROUP_HEADERS, USER_HEADERS, UUID.randomUUID, UUID.randomUUID, creatorId, dateTime).left.map(lineNumber -> _)
    }
  }

  def extractValidInputAndErrors(csvImportContent: String, separator: Char, creatorId: UUID): (Map[UserGroup, List[User]], Map[Int, List[FormError]]) = {
    val forms: List[Either[(Int, List[FormError]), (UserGroup, User)]] = extractFromCSV(csvImportContent, separator, creatorId)
    val lineNumberToErrors: Map[Int, List[FormError]] = forms.filter(_.isLeft).map(_.left.get).toMap

    val deduplicatedEmail = forms.filter(_.isRight).map(_.right.get).groupBy(_._2.email).mapValues(_.head).values.toList
    val groupToUsersMap = deduplicatedEmail.groupBy(_._1.name).map({ case (_, tuple) => tuple.head._1 -> tuple.map(_._2) }) // Group by group name
    groupToUsersMap -> lineNumberToErrors
  }

  private def prepareGroup(group: UserGroup, creator: User, area: Area): UserGroup = {
    val replaceCreateBy = { group: UserGroup =>
      if (group.createByUserId == null)
        group.copy(createByUserId = creator.id)
      else group
    }
    replaceCreateBy(group.copy(name = area.name + ":" + group.name, area = area.id))
  }

  private def prepareUsers(users: List[User], group: UserGroup): List[User] = {
    val setGroup = { user: User =>
      user.copy(groupIds = (group.id :: user.groupIds).distinct)
    }
    val setAreas = { user: User =>
      user.copy(areas = (group.area :: user.areas).distinct)
    }
    users.map(setGroup.compose(setAreas).apply)
  }

  def prepareSection(group: UserGroup, users: List[User], creator: User, area: Area): (UserGroup, List[User]) = {
    val finalGroup = prepareGroup(group, creator, area)
    val finalUsers = prepareUsers(users, finalGroup)
    finalGroup -> finalUsers
  }

  val csvImportContentForm: Form[(String, String)] = Form(mapping(
    "csv-import-content" -> play.api.data.Forms.nonEmptyText,
    "separator" -> play.api.data.Forms.nonEmptyText.verifying(value => value.equals(";") || value.equals(",")))
  ({ (content, separator) => content -> separator })(tuple => Some(tuple._1 -> tuple._2)))
}