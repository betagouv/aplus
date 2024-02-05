package views.emails

import cats.syntax.all._
import constants.Constants
import helper.Time
import java.time.{ZoneId, ZonedDateTime}
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
      userName: Option[String],
      userTimeZone: ZoneId,
      loginToken: LoginToken,
      absoluteUrl: String,
      pathToRedirectTo: String,
      tokenExpirationInMinutes: Int
  ): List[Modifier] = {
    val url = s"$absoluteUrl?token=${loginToken.token}&path=$pathToRedirectTo"
    List[Modifier](
      s"${userName.filter(_.nonEmpty).map(n => s"Bonjour $n,").getOrElse("Bonjour,")}",
      br,
      br,
      p(
        "Vous pouvez maintenant accéder au service Administration+ en cliquant sur le lien suivant :",
        br,
        a(href := raw(url).render, raw(url)),
        br,
        br,
        " ",
        "Ce lien est à ",
        b("usage unique"),
        " et est ",
        b(s"valide $tokenExpirationInMinutes minutes"),
        ", donc à utiliser ",
        b(
          "avant ",
          ZonedDateTime
            .now(userTimeZone)
            .plusMinutes(tokenExpirationInMinutes.toLong)
            .format(Time.hourAndMinutesFormatter)
        ),
        ".",
        br,
        br,
        "Si vous avez des questions ou vous rencontrez un problème, ",
        "n’hésitez pas à nous contacter sur ",
        a(href := s"mailto:${Constants.supportEmail}", Constants.supportEmail),
        br,
        " ",
        "Equipe Administration+"
      )
    ) ::: commonEmailFooter
  }

  val mandatV2Subject = "[A+] Mandat créé"

  def mandatV2Body(absoluteUrl: String): List[Modifier] =
    List[Modifier](
      span(
        "Vous avez généré un nouveau mandat. ",
        "Il est accessible, après connexion, sur Administration+ en cliquant sur ce lien : ",
        a(href := absoluteUrl, absoluteUrl)
      )
    ) ::: commonEmailFooter

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

  val fileQuarantinedSubject =
    "[A+] Un virus a été détecté dans votre fichier envoyé sur Administration+"

  def fileQuarantinedBody(absoluteUrl: String): List[Modifier] =
    List[Modifier](
      span(
        "Notre antivirus a scanné le fichier que vous avez récemment envoyé sur la demande ",
        a(href := absoluteUrl, target := "_blank", rel := "noopener", absoluteUrl),
        " et a détecté un virus. ",
        br,
        br,
        span(
          style := "color: red; font-weight: 800",
          "Il est fortement recommandé de passer un antivirus sur votre poste de travail. "
        ),
        br,
        br,
        "Il peut cependant s’agir d’un faux positif. ",
        "Vous pouvez joindre le document dans autre format (jpeg, etc.) sur le site Administration+ ",
        "en utilisant le formulaire de réponse à la demande ",
        a(href := absoluteUrl, target := "_blank", rel := "noopener", absoluteUrl),
        " ."
      )
    ) ::: commonEmailFooter

  val fileErrorSubject =
    "[A+] Erreur lors de l’enregistrement de votre fichier"

  def fileErrorBody(absoluteUrl: String): List[Modifier] =
    List[Modifier](
      span(
        "Une erreur s’est produite lors de votre envoi récent de fichier sur la demande ",
        a(href := absoluteUrl, target := "_blank", rel := "noopener", absoluteUrl),
        " . ",
        br,
        br,
        "Administration+ n’a pas été en mesure de sauvegarder le fichier. ",
        br,
        br,
        "L’erreur est probablement temporaire. ",
        "Il est possible d’envoyer à nouveau le fichier ",
        "en utilisant le formulaire de réponse à la demande ",
        a(href := absoluteUrl, target := "_blank", rel := "noopener", absoluteUrl),
        " ."
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
        if (infos.applicationsThatShouldBeClosed.size <= 1) "demande qui a reçu une réponse il y a "
        else "demandes qui ont reçu une réponse il y a ",
        s"plus de $daySinceLastAgentAnswerForApplicationsThatShouldBeClosed jours. ",
        "Si votre échange est terminé, n’hésitez pas à ",
        if (infos.applicationsThatShouldBeClosed.size <= 1) "l’archiver, " else "les archiver, ",
        """en appuyant sur le bouton « Archiver l’échange »."""
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

  private def formCreationAbuseStats(
      abuseThreshold: Int,
      formsByIp: Map[String, Int],
      abuseByIp: Map[String, Int]
  ): List[Modifier] =
    List(
      p(
        "Liste des IPs bloquées et nombre de tentatives au-delà du seuil ",
        s"($abuseThreshold) : "
      ),
      ul(
        abuseByIp.toList.sorted.map { case (ip, count) =>
          li(
            b(ip),
            " : ",
            count,
            " "
          )
        }
      ),
      br,
      br,
      p("Nombre de créations par IPs : "),
      ul(
        formsByIp.toList.sorted.map { case (ip, count) =>
          li(
            b(ip),
            " : ",
            count,
            " "
          )
        }
      ),
    )

  val formCreationStatsForAdminsSubject = "[A+] Statistiques des demandes de création de compte"

  def formCreationStatsForAdminsBody(
      stats: Option[AccountCreationStats],
      abuseThreshold: Int,
      formsByIp: Map[String, Int],
      abuseByIp: Map[String, Int]
  ): List[Modifier] = {
    val statsPart: List[Modifier] = stats match {
      case None => List(p(b("Aucune statistique n’est disponible")))
      case Some(stats) =>
        def periodStats(period: AccountCreationStats.PeriodStats): Frag =
          ul(
            li("Minimum : ", period.minCount),
            li("Maximum : ", period.maxCount),
            li(f"1er quartile : ${period.quartile1}%.2f "),
            li(f"Médiane : ${period.median}%.2f "),
            li(f"3ème quartile : ${period.quartile3}%.2f "),
            li(f"99ème pourcentile : ${period.percentile99}%.2f "),
            li(f"Moyenne : ${period.mean}%.2f "),
            li(f"Écart type : ${period.stddev}%.2f "),
          )
        List(
          h2("Statistiques du jour"),
          p("Nombre de formulaire(s) déposé(s) : ", stats.todayCount),
          h2("Statistiques de l’année"),
          periodStats(stats.yearStats),
          h2("Statistiques sur la vie de l’application"),
          periodStats(stats.allStats),
        )
    }
    List(h1("Statistiques des demandes de création de compte")) :::
      statsPart :::
      List(
        br,
        br,
        h2("Statistiques de rate-limit"),
        br,
        br,
      ) ::: formCreationAbuseStats(abuseThreshold, formsByIp, abuseByIp)
  }

  val formCreationAbuseForAdminsSubject = "[A+] ATTENTION ! Abus possible de demandes d’inscription"

  def formCreationAbuseForAdminsBody(
      abuseThreshold: Int,
      formsByIp: Map[String, Int],
      abuseByIp: Map[String, Int]
  ): List[Modifier] =
    List(
      b("ATTENTION: une adresse IP a créé une grande quantité de demandes d’inscription"),
      br,
      br,
    ) ::: formCreationAbuseStats(abuseThreshold, formsByIp, abuseByIp)

  private def applicationEmailFooter: List[Modifier] =
    List(
      br,
      br,
      b("Ne transférez pas cet email et n’y répondez pas directement."),
      br,
      i(
        "- Vous pouvez transférer la demande à un autre utilisateur dans l’outil Administration+ en ouvrant le lien ci-dessus",
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
        " ",
        "- Le navigateur Internet Explorer peut rencontrer des difficultés à accéder au site. Microsoft conseille depuis février 2019 de ne plus utiliser son navigateur historique qui n’est plus mis à jour depuis la sortie de Edge en 2015 et ne supporte donc plus les standards actuels du Web. ",
        a(
          href := "https://docs.aplus.beta.gouv.fr/faq/pourquoi-ne-plus-utiliser-le-navigateur-internet-explorer-de-microsoft",
          " ",
          "Pour en savoir plus"
        )
      )
    )

}
