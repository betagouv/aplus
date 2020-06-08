package services

import helper.{MessageBirdApi, Time}
import java.time.ZonedDateTime
import models.{Error, EventType, Sms, User}
import play.api.Configuration
import play.api.libs.concurrent.Futures
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{PlayBodyParsers, Request}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/** Higher level API that fuses live APIs and fake APIs. */
trait SmsApi {

  def sendSms(body: String, recipient: Sms.PhoneNumber): Future[Either[Error, Sms.Outgoing]]
  def smsReceivedCallback(request: Request[String]): Future[Either[Error, Sms.Incoming]]
  def deleteSms(id: Sms.ApiId): Future[Either[Error, Unit]]

}

object SmsApi {

  final class MessageBirdSmsApi(
      bodyParsers: PlayBodyParsers,
      configuration: Configuration,
      ws: WSClient,
      implicit val executionContext: ExecutionContext
  ) extends SmsApi {
    private val apiKey: String = configuration.get[String]("app.messageBirdApiKey")
    private val signingKey: String = configuration.get[String]("app.messageBirdSigningKey")
    private val aplusPhoneNumber: String = configuration.get[String]("app.messageBirdPhoneNumber")
    private val requestTimeout = 5.seconds

    private val api = new MessageBirdApi(
      bodyParsers,
      ws,
      apiKey,
      signingKey,
      aplusPhoneNumber,
      requestTimeout,
      executionContext
    )

    def sendSms(body: String, recipient: Sms.PhoneNumber): Future[Either[Error, Sms.Outgoing]] =
      api
        .sendSms(body, List(recipient.numberWithoutPlus))
        .map(
          _.map(apiSms =>
            Sms.Outgoing(
              apiId = Sms.ApiId(apiSms.id.underlying),
              creationDate = apiSms.createdDatetime,
              recipient = recipient,
              body = body
            )
          )
        )

    def smsReceivedCallback(request: Request[String]): Future[Either[Error, Sms.Incoming]] =
      api
        .smsReceivedCallback(request)
        .map(
          _.map(apiSms =>
            Sms.Incoming(
              apiId = Sms.ApiId(apiSms.id.underlying),
              creationDate = apiSms.createdDatetime,
              originator = Sms.PhoneNumber("+" + apiSms.originator),
              body = apiSms.body
            )
          )
        )

    def deleteSms(id: Sms.ApiId): Future[Either[Error, Unit]] =
      api.deleteSms(MessageBirdApi.Sms.Id(id.underlying))
  }

  /** Fake API for dev and test */
  final class FakeSmsApi(
      configuration: Configuration,
      eventService: EventService,
      futures: Futures,
      ws: WSClient,
      implicit val executionContext: ExecutionContext
  ) extends SmsApi {
    import serializers.DataModel.SmsFormats._

    private val serverPort: String =
      Option(System.getProperty("http.port"))
        .getOrElse(configuration.underlying.getInt("play.server.http.port").toString)

    def sendSms(body: String, recipient: Sms.PhoneNumber): Future[Either[Error, Sms.Outgoing]] = {
      val outId = Sms.ApiId(scala.util.Random.nextBytes(16).toList.map("%02x".format(_)).mkString)
      val inId = Sms.ApiId(scala.util.Random.nextBytes(16).toList.map("%02x".format(_)).mkString)
      // Keep this warn in case this API is pushed in prod
      val warn = "(ATTENTION ! CE MESSAGE N'A PAS ETE ENVOYE !)"
      val sms = Sms.Outgoing(
        apiId = outId,
        creationDate = Time.nowParis(),
        body = body + " " + warn,
        recipient = recipient
      )

      futures
        .delayed(10.seconds)(
          // Not using the reverse router as this would create a cyclic dependency
          ws.url(s"http://0.0.0.0:$serverPort/mandats/sms/webhook")
            .post(
              Json.toJson(
                Sms.Incoming(
                  apiId = inId,
                  creationDate = Time.nowParis(),
                  originator = recipient,
                  body = "OUI " + warn
                )
              )
            )
        )
        .onComplete(
          _.fold(
            e =>
              eventService.error(
                User.systemUser,
                "",
                EventType.SmsCallbackError.code,
                s"Impossible d'envoyer un message de test (faux webhook). SMS id: ${inId.underlying}",
                None,
                None,
                Some(e)
              ),
            _ =>
              eventService.warn(
                User.systemUser,
                "",
                EventType.SmsCallbackError.code,
                s"Un message de test (faux webhook) a été envoyé. SMS id: ${inId.underlying}",
                None,
                None,
                None
              )
          )
        )
      Future(Right(sms))
    }

    def smsReceivedCallback(request: Request[String]): Future[Either[Error, Sms.Incoming]] = {
      val json = Json.parse(request.body)
      Future(Right(json.as[Sms.Incoming]))
    }

    def deleteSms(id: Sms.ApiId): Future[Either[Error, Unit]] = Future(Right(()))

  }

}
