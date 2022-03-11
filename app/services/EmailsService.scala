package services

import akka.stream.scaladsl.{RestartSource, Sink, Source}
import akka.stream.{Materializer, RestartSettings}
import cats.data.NonEmptyList
import cats.syntax.all._
import helper.MiscHelpers
import javax.inject.{Inject, Singleton}
import models.EmailPriority
import play.api.{Configuration, Logger}
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
  * List of SMTP services with weights. Randomly chooses a service according proportionally to their
  * weights.
  *
  * Example config (HOCON): { urgent:[ {weight:1,smtpConfig:{port:1111,mock:true}},
  * {weight:5,smtpConfig:{port:1111,mock:true}} ], normal:[ {weight:1,
  * smtpConfig:{port:1111,mock:true}, extraHeaders:{ "X-MJ-MonitoringCategory":"aplus",
  * "X-Mailjet-TrackClick":"0", "X-MAILJET-TRACKOPEN":"0" } }, {weight:3,
  * smtpConfig:{port:1111,mock:true}, extraHeaders:{ "TEST-HEADER":"toto" } } ] }
  */
@Singleton
class EmailsService @Inject() (
    configuration: Configuration,
    dependencies: ServicesDependencies,
    materializerProvider: MaterializerProvider,
) {
  import EmailsService._

  implicit val materializer: Materializer = materializerProvider.get

  private val log = Logger(classOf[EmailsService])

  // This blacklist if mainly for experts who do not need emails
  // Note: be careful with the empty string
  private lazy val notificationEmailBlacklist: Set[String] =
    configuration
      .get[String]("app.notificationEmailBlacklist")
      .split(",")
      .map(_.trim)
      .filterNot(_.isEmpty)
      .toSet

  private def emailIsBlacklisted(email: Email): Boolean =
    notificationEmailBlacklist.exists(black => email.to.exists(_.contains(black)))

  private val defaultConfig = configuration.underlying.getObject("play.mailer").toConfig
  private val defaultSMTP = new SMTPMailer(SMTPConfiguration(defaultConfig))

  private val defaultHeaders = List[(String, String)](
    "X-MJ-MonitoringCategory" -> "aplus",
    "X-Mailjet-TrackClick" -> "0",
    "X-MAILJET-TRACKOPEN" -> "0"
  )

  private val pickers: Option[SMTPPickers] =
    configuration
      .getOptional[String]("app.mailer.pickersConfig")
      .map { raw =>
        val rootConfig = ConfigFactory.parseString(raw)
        def readWeightedConfig(key: String) =
          SMTPPicker(
            NonEmptyList.fromListUnsafe(asScala(rootConfig.getObjectList(key)).toList).map { obj =>
              val topConfig = obj.toConfig
              val weight = topConfig.getDouble("weight")
              val smtpConfig = SMTPConfiguration(
                topConfig.getObject("smtpConfig").toConfig.withFallback(defaultConfig)
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
  def sendBlocking(email: Email, priority: EmailPriority): Unit =
    if (emailIsBlacklisted(email) && (email.subject =!= views.emails.common.magicLinkSubject)) {
      log.info(s"Did not send email to ${email.to.mkString(", ")} because it is in the blacklist")
    } else {
      val emailWithText = email.copy(bodyText = email.bodyHtml.map(_.replaceAll("<[^>]*>", "")))
      pickers match {
        case None =>
          val finalEmail = emailWithText.copy(headers = email.headers ++ defaultHeaders)
          defaultSMTP.send(finalEmail)
        case Some(pickers) =>
          val picker = priority match {
            case EmailPriority.Normal => pickers.normal
            case EmailPriority.Urgent => pickers.urgent
          }
          val smtp = picker.choose()
          val finalEmail = emailWithText.copy(headers = email.headers ++ smtp.extraHeaders)
          defaultSMTP.send(finalEmail)
      }
      log.info(s"Email sent to ${email.to.mkString(", ")}")
    }

  // Non blocking, will apply a backoff if the `Future` is `Failed`
  // (ie if `mailerClient.send` throws)
  //
  // Doc for the exponential backoff
  // https://doc.akka.io/docs/akka/current/stream/stream-error.html#delayed-restarts-with-a-backoff-operator
  //
  // Note: we might want to use a queue as the inner source, and enqueue emails in it.
  def sendNonBlocking(email: Email, priority: EmailPriority): Future[Unit] =
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
