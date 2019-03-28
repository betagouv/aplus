package services

import anorm._
import javax.inject.Inject
import models.LoginToken
import play.api.db.Database
import anorm.JodaParameterMetaData._
import play.api.UnexpectedException

@javax.inject.Singleton
class TokenService @Inject()(configuration: play.api.Configuration, db: Database) {

  import extentions.Anorm._

  private val simpleLoginToken: RowParser[LoginToken] = Macro.parser[LoginToken](
    "token",
    "user_id",
    "creation_date",
    "expiration_date",
    "ip_address"
  )

  def create(loginToken: LoginToken) = db.withConnection { implicit connection =>
    SQL"""
      INSERT INTO login_token VALUES (
         ${loginToken.token},
         ${loginToken.userId}::uuid,
         ${loginToken.creationDate},
         ${loginToken.expirationDate},
         ${loginToken.ipAddress}::inet)
      """.executeUpdate() == 1
  }

  def byToken(token: String) = db.withTransaction { implicit connection =>
    val result = SQL"""SELECT token, user_id, creation_date, expiration_date, host(ip_address) AS ip_address FROM login_token WHERE token = $token""".as(simpleLoginToken.singleOpt)
    // To be sure the token is used only once, we remove it from the database
    if(result.nonEmpty && SQL"""DELETE FROM login_token WHERE token = $token""".executeUpdate() != 1) {
      throw UnexpectedException(Some(s"loginToken $token can't be removed on byToken"))
    }
    result
  }
}