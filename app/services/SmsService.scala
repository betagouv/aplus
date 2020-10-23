package services

import actions.RequestWithUserData
import cats.implicits.catsSyntaxEq
import javax.inject.{Inject, Singleton}
import models.{Error, EventType, Organisation, Sms, User, UserGroup}
import models.mandat.Mandat
import play.api.Configuration
import play.api.libs.concurrent.{Futures, MaterializerProvider}
import play.api.libs.ws.WSClient
import play.api.mvc.{PlayBodyParsers, Request}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SmsService @Inject() (
    bodyParsers: PlayBodyParsers,
    configuration: Configuration,
    eventService: EventService,
    futures: Futures,
    materializer: MaterializerProvider,
    ws: WSClient
)(implicit ec: ExecutionContext) {

  private val useLiveApi: Boolean = configuration
    .getOptional[Boolean]("app.smsUseLiveApi")
    .getOrElse(false)

  private val api: SmsApi =
    if (useLiveApi) new SmsApi.OvhSmsApi(bodyParsers, configuration, ws, ec, materializer.get)
    else new SmsApi.FakeSmsApi(configuration, eventService, futures, ws, ec)

  def sendMandatSms(
      recipient: Sms.PhoneNumber,
      mandat: Mandat,
      currentUser: User,
      userGroups: List[UserGroup]
  )(implicit request: RequestWithUserData[_]): Future[Either[Error, Sms.Outgoing]] = {
    val franceServiceGroups: List[UserGroup] = userGroups
      .filter(group =>
        group.organisation
          .map(organisationId =>
            Organisation.organismesAidants.map(_.id).toSet.contains(organisationId: Organisation.Id)
          )
          .getOrElse(false)
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
      } else if (franceServiceGroups.size === 1) {
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
      s"En répondant OUI, vous attestez sur l'honneur que " +
        s"les informations communiquées ($usagerInfos) sont exactes " +
        s"et vous autorisez $userInfos$groupInfos, à utiliser vos données personnelles " +
        s"pour la durée d'instruction de votre demande. " +
        s"Conformément aux CGU d'Administration+."
    api.sendSms(body, recipient)
  }

  def smsReceivedCallback(request: Request[String]): Future[Either[Error, Sms.Incoming]] =
    api.smsReceivedCallback(request)

}
