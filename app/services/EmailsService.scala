package services

import cats.data.NonEmptyList
import cats.syntax.all._
import helper.MiscHelpers
import javax.inject.{Inject, Singleton}
import models.EmailPriority
import modules.AppConfig
import org.apache.pekko.stream.scaladsl.{RestartSource, Sink, Source}
import org.apache.pekko.stream.{Materializer, RestartSettings}
import play.api.Logger
import play.api.libs.concurrent.MaterializerProvider
import play.api.libs.mailer.{Email, SMTPConfiguration, SMTPMailer}
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters.asScala
import scala.util.Try

object EmailsService {

  case class WeightedSMTP(
      weight: Double,
      config: SMTPConfiguration,
      extraHeaders: Map[String, String]
  ) {
    val mailer = new SMTPMailer(config)
  }

  case class SMTPPickers(urgent: SMTPPicker, normal: SMTPPicker)

  case class SMTPPicker(smtpList: NonEmptyList[WeightedSMTP]) {

    def choose(): WeightedSMTP =
      MiscHelpers
        .chooseByFrequency[WeightedSMTP](smtpList.map(smtp => (smtp.weight, smtp)))

  }

}

/** Play-Mailer Documentation: https://github.com/playframework/play-mailer
  *
  * List of SMTP services with weights.
  *
  * Randomly chooses a service with probability proportional to their weights.
  *
  * Config `play.mailer` is used as "base" config (default parameters are taken from it)
  *
  * Example config (HOCON): { urgent:[ {weight:1,smtpConfig:{port:1111,mock:true}},
  * {weight:5,smtpConfig:{port:1111,mock:true}} ], normal:[ {weight:1,
  * smtpConfig:{port:1111,mock:true}, extraHeaders:{ "X-MJ-MonitoringCategory":"aplus",
  * "X-Mailjet-TrackClick":"0", "X-MAILJET-TRACKOPEN":"0" } }, {weight:3,
  * smtpConfig:{port:1111,mock:true}, extraHeaders:{ "TEST-HEADER":"toto" } } ] }
  */
@Singleton
class EmailsService @Inject() (
    config: AppConfig,
    dependencies: ServicesDependencies,
    materializerProvider: MaterializerProvider,
) {
  import EmailsService._

  implicit val materializer: Materializer = materializerProvider.get

  private val log = Logger(classOf[EmailsService])

  private def emailIsBlacklisted(email: Email): Boolean =
    config.notificationEmailBlacklist.exists(black => email.to.exists(_.contains(black)))

  private val defaultSMTPConfig = SMTPConfiguration(config.defaultMailerConfig)
  private val defaultSMTP = new SMTPMailer(defaultSMTPConfig)

  private val defaultHeaders = List[(String, String)](
    "X-MJ-MonitoringCategory" -> "aplus",
    "X-Mailjet-TrackClick" -> "0",
    "X-MAILJET-TRACKOPEN" -> "0"
  )

  private val pickers: Option[SMTPPickers] =
    config.emailPickersConfig
      .map { rootConfig =>
        def readWeightedConfig(key: String) =
          SMTPPicker(
            NonEmptyList.fromListUnsafe(asScala(rootConfig.getObjectList(key)).toList).map { obj =>
              val topConfig = obj.toConfig
              val weight = topConfig.getDouble("weight")
              val smtpConfig = SMTPConfiguration(
                topConfig.getObject("smtpConfig").toConfig.withFallback(config.defaultMailerConfig)
              )
              val extraHeaders = Try(topConfig.getObject("extraHeaders")).toOption
                .map { obj =>
                  val conf = obj.toConfig
                  val keys = asScala(obj.keySet).toList
                  keys.map(key => (key, conf.getString(key))).toMap
                }
                .getOrElse(Map.empty)
              WeightedSMTP(weight, smtpConfig, extraHeaders)
            }
          )
        SMTPPickers(readWeightedConfig("urgent"), readWeightedConfig("normal"))
      }

  // https://github.com/playframework/play-mailer/blob/7.0.x/play-mailer/src/main/scala/play/api/libs/mailer/MailerClient.scala#L15
  // Sends back the `host` of the SMTP used
  def sendBlocking(email: Email, priority: EmailPriority): Option[String] =
    if (emailIsBlacklisted(email) && (email.subject =!= views.emails.common.magicLinkSubject)) {
      log.info(s"Did not send email to ${email.to.mkString(", ")} because it is in the blacklist")
      none
    } else {
      val emailWithText = email.copy(bodyText = email.bodyHtml.map(_.replaceAll("<[^>]*>", "")))
      val host = pickers match {
        case None =>
          val finalEmail = emailWithText.copy(headers = email.headers ++ defaultHeaders)
          defaultSMTP.send(finalEmail)
          defaultSMTPConfig.host
        case Some(pickers) =>
          val picker = priority match {
            case EmailPriority.Normal => pickers.normal
            case EmailPriority.Urgent => pickers.urgent
          }
          val smtp = picker.choose()
          val finalEmail = emailWithText.copy(headers = email.headers ++ smtp.extraHeaders)
          smtp.mailer.send(finalEmail)
          smtp.config.host
      }
      log.info(s"Email sent to ${email.to.mkString(", ")} via $host")
      host.some
    }

  // Non blocking, will apply a backoff if the `Future` is `Failed`
  // (ie if `mailerClient.send` throws)
  //
  // Doc for the exponential backoff
  // https://doc.akka.io/docs/akka/current/stream/stream-error.html#delayed-restarts-with-a-backoff-operator
  //
  // Note: we might want to use a queue as the inner source, and enqueue emails in it.
  def sendNonBlocking(email: Email, priority: EmailPriority): Future[Option[String]] =
    RestartSource
      .onFailuresWithBackoff(
        RestartSettings(
          minBackoff = 10.seconds,
          maxBackoff = 40.seconds,
          randomFactor = 0.2
        )
          .withMaxRestarts(count = 3, within = 10.seconds)
      ) { () =>
        Source.future {
          // `sendBlocking` is executed on the `dependencies.mailerExecutionContext` thread pool
          Future(sendBlocking(email, priority))(dependencies.mailerExecutionContext)
        }
      }
      .runWith(Sink.last)

}
