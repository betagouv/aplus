package services

import java.util.UUID

import constants.Constants
import javax.inject.{Inject, Singleton}
import controllers.routes
import models._
import play.api.Logger
import play.api.libs.mailer.MailerClient
import play.api.libs.mailer.Email
import play.api.libs.ws._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, Reads}
import play.api.mvc.{AnyContent, Request}

import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.util.Try

case class SmsSendRequest(originator: String, body: String, recipients: List[String])

object SmsSendRequest {
  implicit val writes = Json.writes[SmsSendRequest]

//  def create():
}

case class JsonApiError(code: Int, description: String, parameter: Option[String])

object JsonApiError {
  implicit val reads = Json.reads[JsonApiError]
}

case class ApiError(errors: List[JsonApiError])

object ApiError {
  implicit val reads = Json.reads[ApiError]
}

case class SmsId(underlying: String)

object SmsId {
  implicit val reads = implicitly[Reads[String]].map(SmsId.apply)
  implicit val writes = implicitly[Writes[String]].contramap((id: SmsId) => id.underlying)
}

import java.time.ZonedDateTime

// TODO: put in models
// https://developers.messagebird.com/api/sms-messaging/#the-message-object
case class ApiSms(
    id: SmsId,
    originator: String,
    body: String,
    createdDatetime: ZonedDateTime,
    recipients: List[ApiSms.Recipient]
)

object ApiSms {

  case class RecipientItem(
      recipient: Long,
      status: String,
      statusDatetime: ZonedDateTime,
      messagePartCount: Int
  )
  case class Recipient(items: List[RecipientItem])

  implicit val itemFormats = Json.format[RecipientItem]
  implicit val recipientFormats = Json.format[Recipient]
  implicit val reads = Json.reads[ApiSms]
  implicit val writes = Json.writes[ApiSms]

  /** Example: (31612345678: Long) => ("0612345678": String) */
  def internationalToLocalPhone(international: Long): String =
    "0" + (international % 1000000000L)

  /** Example: ("31612345678": String) => ("0612345678": String) */
  def internationalToLocalPhone(international: String): Option[String] =
    Try(international.toLong).toOption.map(internationalToLocalPhone)

  /** Example: ("0612345678": String) => ("31612345678": String)  */
  def localPhoneFranceToInternational(local: String): String =
    "33" + local.drop(1)

}

/** Keep the JsValue for tracability. */
case class CompleteSms(sms: ApiSms, json: JsValue)

/** Various infos:
  * https://support.messagebird.com/hc/en-us/articles/208015009-France
  * "Each Virtual Number has a daily limit of approximately 500 SMS per number, per day."
  */
@Singleton
class SmsService @Inject() (
    configuration: play.api.Configuration,
    eventService: EventService,
    futures: play.api.libs.concurrent.Futures,
    groupService: UserGroupService,
    ws: WSClient
)(implicit ec: ExecutionContext) {

  //private val apiKey = configuration.underlying.getString("app.smsApiKey")
  private val requestTimeout = 2.seconds

  // TODO: handle error cases

  // https://en.wikipedia.org/wiki/GSM_03.38
  // https://developers.messagebird.com/api/#authentication
  /*
  /** https://developers.messagebird.com/api/sms-messaging/#send-outbound-sms
   */
  def sendSms(request: SmsSendRequest): Future[Either[Error, CompleteSms]] = {
      ws.url("https://rest.messagebird.com/messages")
        .addHttpHeaders(
          "Accept" -> "application/json",
          "Authorization" -> s"AccessKey $apiKey")
        .withRequestTimeout(requestTimeout)
        .post(Json.toJson(request))
        .map(response => Right(response.json.as[ApiSms].id))
  }

  // Callback verification:
  // https://developers.messagebird.com/api/#verifying-http-requests
  def smsReceivedCallback(request: Request[AnyContent]): Future[Either[Error, CompleteSms]] = {
    // TODO: verif
    val json = request.body.asJson.get // throws
    val id = (JsPath \ "id").read[String].reads(json).get
    fetchSmsById(SmsId(id))
  }


  // https://developers.messagebird.com/api/sms-messaging/#view-an-sms
  private def fetchSmsById(id: SmsId): Future[Either[Error, CompleteSms]] = {
      ws.url(s"https://rest.messagebird.com/messages/${id.underlying}")
        .addHttpHeaders(
          "Accept" -> "application/json",
          "Authorization" -> s"AccessKey $apiKey")
        .withRequestTimeout(requestTimeout)
        .get()
        .map(response => Right(CompleteSms(response.json.as[ApiSms], response.json)))
  }

  /** https://developers.messagebird.com/api/sms-messaging/#available-http-methods
   */
 def deleteSms(id: SmsId): Future[Either[Error, Unit]] = {
      ws.url(s"https://rest.messagebird.com/messages/${id.underlying}")
        .addHttpHeaders(
          "Accept" -> "application/json",
          "Authorization" -> s"AccessKey $apiKey")
        .withRequestTimeout(requestTimeout)
        .delete()
        .map(response => Right(()))
  }
   */

  def sendSms(request: SmsSendRequest): Future[Either[Error, CompleteSms]] =
    fakeSendSms(request)

  def smsReceivedCallback(request: Request[AnyContent]): Future[Either[Error, CompleteSms]] =
    fakeSmsReceivedCallback(request)

  //
  // Fake API for dev and test
  //

  import helper.Time
  val fakePhoneNumber = "33600000000"

  def fakeSendSms(request: SmsSendRequest): Future[Either[Error, CompleteSms]] = {
    val id = SmsId(scala.util.Random.nextBytes(16).toList.map("%02x".format(_)).mkString)
    val phone = request.recipients.head
    val warn =
      "(ATTENTION ! Ce message n'a pas été envoyé ! Si vous le voyez, un bug c'est produit)"
    val apiSms = ApiSms(
      id = id,
      originator = fakePhoneNumber,
      body = request.body + " " + warn,
      createdDatetime = Time.nowParis(),
      recipients = List(
        ApiSms.Recipient(
          items = List(
            ApiSms.RecipientItem(
              recipient = ApiSms.localPhoneFranceToInternational(phone).toLong,
              status = "sent",
              statusDatetime = Time.nowParis(),
              messagePartCount = 1
            )
          )
        )
      )
    )
    val completeSms = CompleteSms(apiSms, Json.toJson(apiSms))
    // TODO: route from controller routes
    futures
      .delayed(10.seconds)(
        ws.url("http://localhost:9000/mandats/sms/webhook")
          .post(
            Json.toJson(
              ApiSms(
                id = id,
                originator = phone,
                body = "J'accepte " + warn,
                createdDatetime = Time.nowParis(),
                recipients = List(
                  ApiSms.Recipient(
                    items = List(
                      ApiSms.RecipientItem(
                        recipient = fakePhoneNumber.toLong,
                        status = "delivered",
                        statusDatetime = Time.nowParis(),
                        messagePartCount = 1
                      )
                    )
                  )
                )
              )
            )
          )
      )
      .onComplete(x => println("FAKE WEBHOOK POST: " + x)) // TODO: log that
    Future(Right(completeSms))
  }

  def fakeSmsReceivedCallback(request: Request[AnyContent]): Future[Either[Error, CompleteSms]] = {
    val json = request.body.asJson.get // TODO: throws
    Future(Right(CompleteSms(json.as[ApiSms], json)))
  }

}
