package services

import actions.RequestWithUserData
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, Organisation, Sms, User, UserGroup}
import models.mandat.Mandat
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

  def sendMandatSms(
      recipient: Sms.PhoneNumber,
      mandat: Mandat,
      currentUser: User,
      userGroups: List[UserGroup]
  )(implicit request: RequestWithUserData[_]): Future[Either[Error, Sms.Outgoing]] = {
    val franceServiceGroups = userGroups
      .filter(group =>
        (group.organisation: Option[Organisation.Id]) ==
          (Some(Organisation.franceServicesId): Option[Organisation.Id])
      )
    val usagerInfos: String =
      mandat.usagerPrenom.getOrElse("") + " " +
        mandat.usagerNom.getOrElse("") + " " +
        mandat.usagerBirthDate.getOrElse("")
    val userInfos: String = currentUser.name
    val groupInfos: String =
      if (franceServiceGroups.size <= 0) {
        eventService.log(
          EventType.MandatInitiationBySmsWarn,
          s"Lors de la création du SMS, l'utilisateur ${currentUser.id} " +
            s"n'a pas de groupe FS"
        )
        ""
      } else if (franceServiceGroups.size == 1) {
        " de la structure " + franceServiceGroups.map(_.name).mkString(", ")
      } else if (franceServiceGroups.size <= 3) {
        " des structures " + franceServiceGroups.map(_.name).mkString(", ")
      } else {
        eventService.log(
          EventType.MandatInitiationBySmsWarn,
          s"Lors de la création du SMS, l'utilisateur ${currentUser.id} " +
            s"est dans trop de groupes (${franceServiceGroups.size}) " +
            "pour les inclure dans le SMS"
        )
        ""
      }

    val body =
      s"En répondant OUI, vous assurez sur l'honneur que " +
        s"les informations communiquées ($usagerInfos) sont exactes " +
        s"et vous autorisez $userInfos$groupInfos, à utiliser vos données personnelles " +
        s"dans le cadre d'une demande et pour la durée d'instruction de celle-ci. " +
        s"Conformément aux conditions générales d'utilisation de la plateforme Adminitration+."
    api.sendSms(body, recipient)
  }

  def smsReceivedCallback(request: Request[String]): Future[Either[Error, Sms.Incoming]] =
    api.smsReceivedCallback(request)

}
