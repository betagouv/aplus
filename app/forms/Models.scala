package forms

import java.util.UUID

import actions.RequestWithUserData
import extentions.Time
import models.{Area, User, UserGroup}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.Constraints.{maxLength, nonEmpty}

object Models {

  case class ApplicationData(subject: String, description: String, infos: Map[String, String], users: List[UUID], organismes: List[String], category: Option[String], selectedSubject: Option[String])

  case class AnswerData(message: String, applicationIsDeclaredIrrelevant: Boolean, infos: Map[String, String], privateToHelpers: Boolean)

  case class InvitationData(message: String, invitedUsers: List[UUID], privateToHelpers: Boolean)

  val validateCGUForm: Form[(Option[String], Boolean, Boolean)] = Form(tuple(
    "redirect" -> optional(text),
    "newsletter" -> boolean,
    "validate" -> boolean
  ))

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
    "cguAcceptationDate" -> optional(ignored(Time.now())),
    "newsletterAcceptationDate" -> optional(ignored(Time.now()))
  )(User.apply)(User.unapply)

  def usersForm(implicit area: Area, timeZone: DateTimeZone): Form[List[User]] = Form(
    single(
      "users" -> list(mapping(
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
        "areas" -> ignored(List(area.id)),
        "creationDate" -> ignored(DateTime.now(timeZone)),
        "hasAcceptedCharte" -> ignored(false),
        "communeCode" -> default(nonEmptyText.verifying(maxLength(5)), "0"),
        "adminGroup" -> ignored(false),
        "disabled" -> ignored(false),
        "expert" -> ignored(false),
        "groupIds" -> default(list(uuid), List()),
        "delegations" -> ignored(Map[String, String]()),
        "cguAcceptationDate" -> optional(ignored(Time.now())),
        "newsletterAcceptationDate" -> optional(ignored(Time.now()))
      )(User.apply)(User.unapply))
    )
  )

  def userForm(implicit timeZone: DateTimeZone): Form[User] = Form(userMapping)

  def addGroupForm[A](implicit request: RequestWithUserData[A], timeZone: DateTimeZone) = Form(
    mapping(
      "id" -> ignored(UUID.randomUUID()),
      "name" -> text(maxLength = 50),
      "description" -> optional(text),
      "insee-code" -> text,
      "creationDate" -> ignored(DateTime.now(timeZone)),
      "create-by-user-id" -> ignored(request.currentUser.id),
      "area" -> uuid.verifying("Vous devez sélectionner un territoire sur lequel vous êtes admin", area => request.currentUser.areas.contains(area)),
      "organisation" -> optional(text),
      "email" -> optional(email)
    )(UserGroup.apply)(UserGroup.unapply)
  )
}
