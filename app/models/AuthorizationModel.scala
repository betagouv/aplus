package models

case class AuthorizationModel(helpedFirstName: Option[String],
                              helpedLastName: String,
                              helpedAddress: String,
                              helpedPhoneNumber: String,
                              helperFirstName: Option[String],
                              helperLastName: String,
                              helpedPersonalData: String,
                              helperPhoneNumber: String,
                              helperFunction: String,
                              helperStructure: String,
                              helperTasks: String)
