package models

import java.time.ZonedDateTime
import java.util.UUID

import constants.Constants
import helper.{Hash, UUIDHelper}

final case class UnvalidatedUser(
    id: UUID,
    email: String,
    firstname: Option[String],
    lastname: Option[String],
    phoneNumber: Option[String] = None,
    sharedAccountName: Option[String],
    instructor: Boolean,
    // If true, this person is managing the groups it is in
    // can see all users in its groups, and add new users in its groups
    // cannot modify users, only admin can.
    managerOfGroups: Boolean,
    // TODO: remove usage of areas to more specific AdministratedAreaIds
    // Note: `areas` is used to give "full access" to someone to multiple areas
    areas: List[UUID],
    creationDate: ZonedDateTime,
    groupIds: List[UUID] = Nil,
    // If this field is non empty, then the User
    // is considered to be an observer:
    // * can see stats+deployment of all areas,
    // * can see all users,
    // * can see one user but not edit it
    observableOrganisationIds: List[Organisation.Id] = Nil
)
