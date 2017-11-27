package services

import java.util.UUID
import javax.inject.Inject

import models.User
import utils.{DemoData, Hash}

@javax.inject.Singleton
class UserService @Inject()(configuration: play.api.Configuration) {
  def all() = DemoData.users

  def byId(id: UUID): Option[User] = all.find(_.id == id)

  def byKey(key: String): Option[User] = all.find(_.key == key)

  def byEmail(email: String): Option[User] = all.find(_.email.toLowerCase() == email.toLowerCase())
}