package models

import java.util.UUID

import utils.DemoData

case class User(id: UUID,
                key: String,
                name: String,
                qualite: String,
                email: String,
                helper: Boolean,
                instructor: Boolean,
                areas: List[UUID])

object User {
  def get(id: String): Option[User] = DemoData.users.find(_.id == id)
}