package views.emails

import constants.Constants
import helper.Time
import java.time.{ZoneId, ZonedDateTime}
import models._
import scalatags.Text.all._
import views.helpers.common.contactLink

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
        "Vous pouvez maintenant accéder au service Administration+ en cliquant sur le lien suivant : ",
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
        "Un bug récent peut provoquer un double clic sur notre lien de connexion, ce qui invalide le jeton de connexion unique.",
        br,
        "Si vous rencontrez ce problème, veuillez essayer l’une des solutions suivantes :",
        br,
        "- Copiez-collez le lien directement dans la barre d’adresse de votre navigateur.",
        br,
        "- Faites un clic droit sur le lien et sélectionnez « Ouvrir dans un nouvel onglet ».",
        br,
        "- Utilisez CTRL + Clic sur le lien pour le même résultat.",
        br,
        br,
        "Si vous avez des questions ou vous rencontrez un problème, ",
        "n’hésitez pas à ",
        contactLink("contacter l’équipe A+"),
        br,
        " ",
        "Equipe Administration+"
      )
    ) ::: commonEmailFooter
  }

  val passwordReinitializationSubject = "Réinitialisation du mot de passe Administration+"

  def passwordReinitializationBody(
      userName: Option[String],
      userTimeZone: ZoneId,
      absoluteUrl: String,
      expirationDate: ZonedDateTime,
  ): List[Modifier] =
    List[Modifier](
      s"${userName.filter(_.nonEmpty).map(n => s"Bonjour $n,").getOrElse("Bonjour,")}",
      br,
      br,
      p(
        "Une demande de réinitialisation de mot de passe a été faite pour " +
          "votre compte Administration+. Pour terminer la procédure, " +
          "veuillez cliquer sur le lien suivant : ",
        br,
        a(href := raw(absoluteUrl).render, raw(absoluteUrl)),
        br,
        br,
        " ",
        "Ce lien est valide jusqu’à ",
        b(expirationDate.format(Time.hourAndMinutesFormatter)),
        ".",
        br,
        br,
        "Si vous n’avez pas effectué de demande de réinitialisation de mot de passe, ",
        "vous pouvez ignorer cet email.",
        br,
        " ",
        "Equipe Administration+"
      )
    ) ::: commonEmailFooter

  val userInactivityReminderSubject = "[A+] Votre compte Administration+ est inactif"

  def userInactivityReminderBody(
      userName: Option[String],
      url: String,
      numberOfMonths: Int
  ): List[Modifier] =
    List[Modifier](
      s"${userName.filter(_.nonEmpty).map(n => s"Bonjour $n,").getOrElse("Bonjour,")}",
      br,
      br,
      p(
        s"Votre compte sur la plateforme Administration+ est inactif depuis plus de $numberOfMonths mois.",
        br,
        br,
        "Il est possible que certaines demandes en cours soient encore associées à votre profil.",
        br,
        "Si vous souhaitez les consulter ou continuer à utiliser la plateforme, nous vous invitons à vous reconnecter dans les 30 prochains jours, à l’adresse suivante : ",
        br,
        br,
        a(href := url, url),
        " ",
        br,
        br,
        "Cette nouvelle connexion est nécessaire pour éviter la suppression automatique de votre compte.",
        br,
        br,
        "Si vous ne prévoyez plus d’utiliser Administration+, vous pouvez simplement nous transmettre l’adresse e-mail de votre remplaçant, afin de garantir la continuité du suivi des dossiers.",
        br,
        br,
        "Nous restons à votre disposition pour toute question,",
        br,
        br,
        "L’équipe Administration+"
      )
    ) ::: commonEmailFooter

  val userInactivityDeactivationSubject = "[A+] Suppression de votre compte Administration+"

  def userInactivityDeactivationBody(
      userName: Option[String],
  ): List[Modifier] =
    List[Modifier](
      s"${userName.filter(_.nonEmpty).map(n => s"Bonjour $n,").getOrElse("Bonjour,")}",
      br,
      br,
      p(
        "Votre compte a été supprimé de la base d’utilisateurs d’Administration+.",
        br,
        br,
        "Il n’est désormais plus possible de recevoir ou de formuler de demandes via la plateforme.",
        br,
        br,
        "🔄 En cas d’erreur, tout membre de votre équipe peut réactiver votre compte à tout moment depuis la page ",
        a(
          href := "https://docs.aplus.beta.gouv.fr/guides-et-tutoriels/responsable-de-groupe",
          "« Mes groupes »"
        ),
        ". ",
        br,
        br,
        "👤 Si vous connaissez votre remplaçant, n’hésitez pas à nous transmettre ses coordonnées afin que nous puissions lui créer un compte. Le responsable de votre groupe peut également effectuer cette démarche depuis la même page.",
        br,
        br,
        "Si vous avez la moindre question, n’hésitez pas à nous contacter via l’adresse ",
        a(href := s"mailto:${Constants.supportEmail}", Constants.supportEmail),
        ". ",
        br,
        br,
        "Bien cordialement,",
        br,
        br,
        "L’équipe Administration+"
      )
    ) ::: commonEmailFooter

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
        i(
          "Si vous avez un problème ou besoin d’aide à propos de l’outil, ",
          contactLink("contactez l’équipe A+"),
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
        "- Vous pouvez transférer la demande à un autre utilisateur dans l’outil Administration+ en ouvrant le lien ci-dessus",
        br,
        "- Si vous avez un problème ou besoin d’aide à propos de l’outil, ",
        contactLink("contactez l’équipe A+"),
        br,
        "- Le navigateur Internet Explorer peut rencontrer des difficultés à accéder au site. Microsoft conseille depuis février 2019 de ne plus utiliser son navigateur historique qui n’est plus mis à jour depuis la sortie de Edge en 2015 et ne supporte donc plus les standards actuels du Web. ",
        a(
          href := "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/pourquoi-ne-plus-utiliser-le-navigateur-internet-explorer-de-microsoft",
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
        "- Si vous avez un problème ou besoin d’aide à propos de l’outil, ",
        contactLink("contactez l’équipe A+"),
        br,
        " ",
        "- Le navigateur Internet Explorer peut rencontrer des difficultés à accéder au site. Microsoft conseille depuis février 2019 de ne plus utiliser son navigateur historique qui n’est plus mis à jour depuis la sortie de Edge en 2015 et ne supporte donc plus les standards actuels du Web. ",
        a(
          href := "https://docs.aplus.beta.gouv.fr/faq/questions-frequentes-pour-tous-les-profils/pourquoi-ne-plus-utiliser-le-navigateur-internet-explorer-de-microsoft",
          " ",
          "Pour en savoir plus"
        )
      )
    )

}
