package modules

import com.typesafe.config.{Config, ConfigFactory}
import helper.{Crypto, UUIDHelper}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import scala.concurrent.duration._

// Wraps `configuration: play.api.Configuration`.
// We want `configuration` to be private.
@Singleton
class AppConfig @Inject() (configuration: Configuration) {

  val tokenExpirationInMinutes: Int =
    configuration.get[Int]("app.tokenExpirationInMinutes")

  val fieldEncryptionKeys: Crypto.KeySet = {
    val key = Crypto.decodeKeyBase64(configuration.get[String]("app.fieldsEncryption.key"))
    val oldKeys = configuration
      .getOptional[String]("app.fieldsEncryption.oldKeys")
      .toList
      .flatMap(_.split(',').filter(_.nonEmpty).map(Crypto.decodeKeyBase64))
    Crypto.KeySet(key, oldKeys)
  }

  val fieldEncryptionKeyRotationExecute: Boolean = configuration
    .getOptional[Boolean]("app.fieldsEncryption.keyRotation.execute")
    .getOrElse(false)

  val fieldEncryptionKeyRotationEncryptIfPlainText: Boolean = configuration
    .getOptional[Boolean]("app.fieldsEncryption.keyRotation.encryptIfPlainText")
    .getOrElse(false)

  val featureMandatSms: Boolean = configuration.get[Boolean]("app.features.smsMandat")

  val useLiveSmsApi: Boolean = configuration
    .getOptional[Boolean]("app.smsUseLiveApi")
    .getOrElse(false)

  val featureCanSendApplicationsAnywhere: Boolean =
    configuration.get[Boolean]("app.features.canSendApplicationsAnywhere")

  val filesPath: String = configuration.get[String]("app.filesPath")

  val filesDirectory: Path = {
    val dir = Paths.get(filesPath)
    if (!Files.isDirectory(dir)) {
      Files.createDirectories(dir)
    }
    dir
  }

  // This is a feature that is temporary and should be activated
  // for short period of time during migrations for smooth handling of files.
  // Just remove the env variable FILES_SECOND_INSTANCE_HOST to deactivate.
  val filesSecondInstanceHost: Option[String] =
    configuration.getOptional[String]("app.filesSecondInstanceHost")

  val filesExpirationInDays: Int = configuration.get[Int]("app.filesExpirationInDays")

  val topHeaderWarningMessage: Option[String] =
    configuration.getOptional[String]("app.topHeaderWarningMessage")

  val areasWithLoginByKey: List[UUID] = configuration
    .get[String]("app.areasWithLoginByKey")
    .split(",")
    .flatMap(UUIDHelper.fromString)
    .toList

  val clamAvIsEnabled: Boolean = configuration.get[Boolean]("app.clamav.enabled")

  val clamAvHost: String = configuration.get[String]("app.clamav.host")
  val clamAvPort: Int = configuration.get[Int]("app.clamav.port")
  val clamAvTimeout: FiniteDuration = configuration.get[Int]("app.clamav.timeoutInSeconds").seconds

  // This blacklist if mainly for experts who do not need emails
  // Note: be careful with the empty string
  val notificationEmailBlacklist: Set[String] =
    configuration
      .get[String]("app.notificationEmailBlacklist")
      .split(",")
      .map(_.trim)
      .filterNot(_.isEmpty)
      .toSet

  val defaultMailerConfig: Config = configuration.underlying.getObject("play.mailer").toConfig

  val emailPickersConfig: Option[Config] =
    configuration
      .getOptional[String]("app.mailer.pickersConfig")
      .map { raw =>
        ConfigFactory.parseString(raw)
      }

}
