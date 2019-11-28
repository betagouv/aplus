import java.util.UUID

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import extentions.Time
import models.{User, UserGroup}
import org.joda.time.DateTime
import play.api.data.Forms.{list, mapping, optional, single, text, tuple}
import play.api.data.{Form, Mapping}
import services.{UserGroupService, UserService}

import scala.io.Source

package object csv {

  val USER_NAME_HEADER_PREFIX = "Nom"
  val USER_QUALITY_HEADER_PREFIX = "Qualité"
  val USER_EMAIL_HEADER_PREFIX = "Email"
  val INSTRUCTOR_HEADER_PREFIX = "Instructeur"
  val GROUP_MANAGER_HEADER_PREFIX = "Responsable"

  val TERRITORY_HEADER_PREFIX = "Territoire"
  val GROUP_ORGANISATION_HEADER_PREFIX = "Organisation"
  val GROUP_NAME_HEADER_PREFIX = "Groupe"
  val GROUP_EMAIL_HEADER_PREFIX = "Bal"

  val SEPARATOR = ";"

  def convertToPrefixForm(values: Map[String, String], headers: List[String], prefix: String): Map[String, String] = {
    values.map({ case (key, value) =>
      headers.find(key.startsWith).map(prefix + _ -> value)
    }).flatten.toMap
  }

  val sectionMapping: (() => UUID) => (() => UUID) => (() => UUID) => DateTime => Mapping[Section] =
    (groupId: () => UUID) => (userId: () => UUID) => (creatorId: () => UUID) => (dateTime: DateTime) => mapping(
      "group" -> GroupImport.groupMappingForCSVImport(groupId)(creatorId)(dateTime),
      "users" -> list(UserImport.userMappingForCVSImport(userId)(dateTime))
    )(Section.apply)(Section.unapply)

  private val tupleMapping: (() => UUID) => (() => UUID) => (() => UUID) => DateTime => Mapping[(UserGroup, User)] =
    (groupId: () => UUID) => (userId: () => UUID) => (creatorId: () => UUID) => (dateTime: DateTime) => mapping(
      "group" -> GroupImport.groupMappingForCSVImport(groupId)(creatorId)(dateTime),
      "user" -> UserImport.userMappingForCVSImport(userId)(dateTime)
    )((g: UserGroup, u: User) => g -> u)(tuple => Option(tuple._1 -> tuple._2))

  val tupleForm: (() => UUID) => (() => UUID) => (() => UUID) => DateTime => Form[(UserGroup, User)] =
    (groupId: () => UUID) => (userId: () => UUID) => (creatorId: () => UUID) => (dateTime: DateTime) =>
      Form.apply(tupleMapping(groupId)(userId)(creatorId)(dateTime))

  case class Section(group: UserGroup, users: List[User])

  def fromCSVLine(values: Map[String, String], groupHeaders: List[String], userHeaders: List[String], groupId: () => UUID,
                  userId: () => UUID, creatorId: () => UUID, dateTime: DateTime): Form[(UserGroup, User)] = {
    tupleForm(groupId)(userId)(creatorId)(dateTime).bind(convertToPrefixForm(values, groupHeaders, "group.") ++ convertToPrefixForm(values, userHeaders, "user."))
  }

  val sectionsMapping: (() => UUID) => (() => UUID) => (() => UUID) => DateTime => Mapping[List[csv.Section]] =
    (groupId: () => UUID) => (userId: () => UUID) => (creatorId: () => UUID) => (dateTime: DateTime) =>
      single("sections" -> list(csv.sectionMapping(groupId)(userId)(creatorId)(dateTime)))

  def sectionsForm(groupId: () => UUID, userId: () => UUID, creatorId: () => UUID, dateTime: DateTime): Form[List[csv.Section]] =
    Form(sectionsMapping(groupId)(userId)(creatorId)(dateTime))

  def sectionsForm: Form[List[csv.Section]] =
    sectionsForm(() => UUID.randomUUID(), () => UUID.randomUUID(), () => UUID.randomUUID(), DateTime.now(Time.dateTimeZone))

  def extractFromCSV(csvText: String): List[Form[(UserGroup, User)]] = {
    implicit object SemiConFormat extends DefaultCSVFormat {
      override val delimiter = ';'
    }
    val reader = CSVReader.open(Source.fromString(csvText))
    val dateTime = DateTime.now(Time.dateTimeZone)
    reader.allWithHeaders()
      .map({ data =>
        val groupId = () => UUID.randomUUID()
        val userId = () => UUID.randomUUID()
        val creatorId = () => UUID.randomUUID()
        csv.fromCSVLine(data, GroupImport.HEADERS, UserImport.HEADERS, groupId, userId, creatorId, dateTime)
      })
  }

  def extractAndConvertFormNames(forms: List[Form[(UserGroup, User)]], id: Int)(userService: UserService, groupService: UserGroupService): Map[String, String] = {
    def prefixBySection(key: String, id: Int): String = "sections[" + id + "]." + key

    forms.zipWithIndex.flatMap({ case (form, userId) =>
      val remappedGroups = form.data.filter(_._1.startsWith("group."))
        .map({ case (key, value) => prefixBySection(key, id) -> value })
      val remappedUsers = form.data.filter(_._1.startsWith("user."))
        .map(t => t._1.replace("user.", "users[" + userId + "].") -> t._2)
        .map({ case (key, value) => prefixBySection(key, id) -> value })
      val emailKey = prefixBySection("users[" + userId + "]." + csv.USER_EMAIL_HEADER_PREFIX, id)

      val existingUserId = remappedUsers.get(emailKey).flatMap(userService.byEmail).fold(
        Map.empty[String, String]
      )({ user: User =>
        val idKey = prefixBySection("users[" + userId + "].id", id)
        val readOnlyKey = prefixBySection("users[" + userId + "].readonly", id)
        Map(idKey -> user.id.toString, readOnlyKey -> "true")
      })

      val groupNameKey = prefixBySection("group." + csv.GROUP_NAME_HEADER_PREFIX, id)
      val existingGroupId = remappedGroups.get(groupNameKey).flatMap(groupService.groupByName).fold(
        Map.empty[String, String]
      )({ group: UserGroup =>
        val idKey = prefixBySection("group.id", id)
        val readOnlyKey = prefixBySection("group.readonly", id)
        Map(idKey -> group.id.toString, readOnlyKey -> "true")
      })

      remappedGroups ++ remappedUsers ++ existingUserId ++ existingGroupId
    }).toMap
  }

  def extractDataFromCSVAndMapToTreeStructure(csvImportContent: String)(userService: UserService, groupService: UserGroupService): Map[String, String] = {
    val forms: List[Form[(UserGroup, User)]] = extractFromCSV(csvImportContent)
    // If the name of the group is not defined, the line is discarded.
    val groupNameToForms = forms.groupBy(_.data.get("group.Groupe"))
      .filter(a => a._1.isDefined && a._1.get.nonEmpty)
      .map(t => t._1.get -> t._2)
    groupNameToForms
      .toList
      .zipWithIndex
      .flatMap(t => extractAndConvertFormNames(t._1._2, t._2)(userService, groupService)).toMap
  }

  def prepareGroup(group: UserGroup, creator: User): UserGroup = {
    val replaceCreateBy = { group: UserGroup =>
      if (group.createByUserId == null)
        group.copy(createByUserId = creator.id)
      else group
    }
    replaceCreateBy(group)
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

  def prepareSection(section: Section, creator: User): (UserGroup, List[User]) = {
    val group = prepareGroup(section.group, creator)
    val users = prepareUsers(section.users, group)
    group -> users
  }

  val csvImportContentForm = Form("csv-import-content" -> play.api.data.Forms.nonEmptyText)
}