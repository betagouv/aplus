package services

import javax.inject.{Inject, Singleton}
import models.{Error, Sms}
import play.api.libs.ws.WSClient
import play.api.mvc.Request
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SmsService @Inject() (
    bodyParsers: play.api.mvc.PlayBodyParsers,
    configuration: play.api.Configuration,
    eventService: EventService,
    futures: play.api.libs.concurrent.Futures,
    ws: WSClient
)(implicit ec: ExecutionContext) {

  private val useLiveApi: Boolean = configuration
    .getOptional[Boolean]("app.smsUseLiveApi")
    .getOrElse(false)

  private val api: SmsApi =
    if (useLiveApi) new SmsApi.MessageBirdSmsApi(bodyParsers, configuration, ws, ec)
    else new SmsApi.FakeSmsApi(configuration, eventService, futures, ws, ec)

  def sendSms(body: String, recipient: Sms.PhoneNumber): Future[Either[Error, Sms.Outgoing]] =
    api.sendSms(body, recipient)

  def smsReceivedCallback(request: Request[String]): Future[Either[Error, Sms.Incoming]] =
    api.smsReceivedCallback(request)

}
