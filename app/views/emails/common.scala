package views.emails

import constants.Constants
import helper.Time
import java.time.ZonedDateTime
import models._
import scalatags.Text.all._

object common {

  def renderEmail(inner: List[Modifier]): String =
    "<!DOCTYPE html>" + html(
      head(
        meta(
          name := "viewport",
          content := "width=device-width"
        ),
        meta(
          httpEquiv := "Content-Type",
          content := "text/html; charset=UTF-8"
        )
      ),
      body(
        inner: _*
      )
    )

  val magicLinkSubject = "Connexion sécurisée à Administration+"

  def magicLinkBody(
      user: User,
      loginToken: LoginToken,
      absoluteUrl: String,
      pathToRedirectTo: String,
      tokenExpirationInMinutes: Int
  ): List[Modifier] = {
    val url = s"${absoluteUrl}?token=${loginToken.token}&path=$pathToRedirectTo"
    List[Modifier](
      s"Bonjour ${user.name},",
      br,
      br,
      p(
        "Vous pouvez maintenant accéder au service Administration+ en cliquant sur le lien suivant :",
        br,
        a(href := url, url),
        br,
        br,
        "Ce lien est à ",
        b("usage unique"),
        " et est ",
        b(s"valide $tokenExpirationInMinutes minutes"),
        ", donc à utiliser avant ",
        ZonedDateTime
          .now(user.timeZone)
          .plusMinutes(tokenExpirationInMinutes.toLong)
          .format(Time.hourAndMinutesFormatter),
        ".",
        br,
        br,
        "Si vous avez des questions ou vous rencontrez un problème, ",
        "n’hésitez pas à nous contacter sur ",
        a(href := s"mailto:${Constants.supportEmail}", Constants.supportEmail),
        br,
        "Equipe Administration+"
      )
    ) ::: commonEmailFooter
  }

  val mandatSmsSentSubject = "[A+] Mandat initié par SMS"

  def mandatSmsSentBody(absoluteUrl: String): List[Modifier] =
    List[Modifier](
      span(
        "Le SMS a bien été envoyé ! Vous recevrez un e-mail dès que l’usager aura répondu. ",
        "Vous pouvez suivre l’échange en cliquant sur ce lien : ",
        a(href := absoluteUrl, absoluteUrl)
      )
    ) ::: commonEmailFooter

  val mandatSmsClosedSubject = "[A+] Vous avez reçu une réponse par SMS à votre mandat"

  def mandatSmsClosedBody(absoluteUrl: String): List[Modifier] =
    List[Modifier](
      span(
        "L’usager a répondu par SMS au mandat. ",
        "L’échange SMS est disponible en cliquant sur ce lien : ",
        a(href := absoluteUrl, absoluteUrl)
      )
    ) ::: commonEmailFooter

  def invitationBody(
      application: Application,
      answer: Option[Answer],
      invitedUser: User,
      absoluteUrl: String
  ): List[Modifier] = {
    val url =
      s"$absoluteUrl?key=${invitedUser.key}"
    List[Modifier](
      s"Bonjour ${invitedUser.name},",
      br,
      br,
      p(
        answer.map(_.creatorUserName).getOrElse[String](application.creatorUserName),
        " a besoin de vous.",
        br,
        "Cette personne vous a invité sur la demande suivante : ",
        application.subject,
        br,
        "Vous pouvez voir la demande et y répondre en suivant ce lien : ",
        br,
        a(
          href := url,
          url
        ),
        br
      )
    ) ::: applicationEmailFooter
  }

  def answerBody(
      application: Application,
      answer: Answer,
      user: User,
      absoluteUrl: String
  ): List[Modifier] = {
    val url =
      s"$absoluteUrl?key=${user.key}#answer-${answer.id}"
    val irrelevantDefaultText: List[Modifier] =
      if (answer.declareApplicationHasIrrelevant)
        List[Modifier](
          answer.creatorUserName.split('(').headOption.getOrElse[String]("L'agent"),
          " a indiqué qu'",
          b("il existe une procédure standard que vous pouvez utiliser pour cette demande"),
          " vous aurez plus de détails dans sa réponse.",
          br,
          br
        )
      else
        Nil
    List[Modifier](
      s"Bonjour ${user.name},",
      br,
      br,
      p(
        answer.creatorUserName,
        " a donné une réponse sur la demande : ",
        application.subject,
        br,
        irrelevantDefaultText,
        "Vous pouvez consulter la réponse, y répondre ou la clôturer en suivant le lien suivant : ",
        br,
        a(
          href := url,
          url
        )
      )
    ) ::: applicationEmailFooter
  }

  val weeklyEmailSubject = "[A+] Votre récapitulatif hebdomadaire"

  def weeklyEmailBody(
      infos: WeeklyEmailInfos,
      applicationAbsoluteUrl: Application => String,
      daySinceLastAgentAnswerForApplicationsThatShouldBeClosed: Int
  ): List[Modifier] = {
    val datePattern = "EEEE dd MMMM YYYY HH'h'mm"
    List[Modifier](
      s"Bonjour ${infos.user.name},",
      br,
      br,
      p(
        s"Vous avez ${infos.applicationsThatShouldBeClosed.size} ",
        (
          if (infos.applicationsThatShouldBeClosed.size <= 1)
            "demande qui a reçu une réponse il y a "
          else
            "demandes qui ont reçu une réponse il y a ",
        ),
        s"plus de $daySinceLastAgentAnswerForApplicationsThatShouldBeClosed jours. ",
        "Si votre échange est terminé, n’hésitez pas à ",
        (
          if (infos.applicationsThatShouldBeClosed.size <= 1)
            "la clore, "
          else
            "les clore, "
        ),
        """en appuyant sur le bouton « Clore l’échange »."""
      ),
      ul(
        infos.applicationsThatShouldBeClosed.map(application =>
          li(
            a(
              href := applicationAbsoluteUrl(application),
              s"Demande du ${Time.formatPatternFr(application.creationDate, datePattern)}"
            )
          )
        )
      ),
      br,
      br,
      p(
        "Comment avez-vous trouvé cet email ? ",
        "Nous expérimentons. ",
        "Pour nous aider, vous pouvez répondre à ce questionnaire : ",
        a(
          href := "https://startupdetat.typeform.com/to/PicUnQx4",
          "https://startupdetat.typeform.com/to/PicUnQx4"
        )
      ),
      br,
      br,
      p(
        i(
          "Si vous avez un problème ou besoin d’aide à propos de l’outil Administration+, contactez-nous sur ",
          a(
            href := s"mailto:${Constants.supportEmail}",
            s"${Constants.supportEmail}"
          )
        )
      )
    )
  }

  private def applicationEmailFooter: List[Modifier] =
    List(
      br,
      br,
      b("Ne transférez pas cet email et n’y répondez pas directement."),
      br,
      i(
        "- Vous pouvez transférer la demande à un autre utilisateur en ouvrant le lien ci-dessus",
        br,
        "- Si vous avez un problème ou besoin d’aide à propos de l’outil Administration+, contactez-nous sur ",
        a(
          href := s"mailto:${Constants.supportEmail}",
          s"${Constants.supportEmail}"
        ),
        br,
        "- Le navigateur Internet Explorer peut rencontrer des difficultés à accéder au site. Microsoft conseille depuis février 2019 de ne plus utiliser son navigateur historique qui n’est plus mis à jour depuis la sortie de Edge en 2015 et ne supporte donc plus les standards actuels du Web. ",
        a(
          href := "https://docs.aplus.beta.gouv.fr/faq/pourquoi-ne-plus-utiliser-le-navigateur-internet-explorer-de-microsoft",
          "Pour en savoir plus"
        )
      )
    )

  private def commonEmailFooter: List[Modifier] =
    List(
      br,
      br,
      b("Ne transférez pas cet email et n’y répondez pas directement."),
      br,
      i(
        "- Si vous avez un problème ou besoin d’aide à propos de l’outil Administration+, contactez-nous sur ",
        a(
          href := s"mailto:${Constants.supportEmail}",
          s"${Constants.supportEmail}"
        ),
        br,
        "- Le navigateur Internet Explorer peut rencontrer des difficultés à accéder au site. Microsoft conseille depuis février 2019 de ne plus utiliser son navigateur historique qui n’est plus mis à jour depuis la sortie de Edge en 2015 et ne supporte donc plus les standards actuels du Web. ",
        a(
          href := "https://docs.aplus.beta.gouv.fr/faq/pourquoi-ne-plus-utiliser-le-navigateur-internet-explorer-de-microsoft",
          "Pour en savoir plus"
        )
      )
    )

}
