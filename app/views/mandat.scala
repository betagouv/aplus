package views

import cats.syntax.all._
import constants.Constants
import controllers.routes.{ApplicationController, Assets}
import helper.Time
import helper.TwirlImports.toHtml
import models.{Authorization, Mandat, User, UserGroup}
import org.webjars.play.WebJarsUtil
import play.api.mvc.{Flash, RequestHeader}
import play.twirl.api.Html
import scalatags.Text.all._

object mandat {

  def pageV1(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      mandat: Mandat,
  )(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash,
      request: RequestHeader,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)(
      "Mandat du " + Time.formatPatternFr(mandat.creationDate, "dd MMM YYYY - HH'h'mm")
    )(Html(""))(mandatV1(mandat))(Nil)

  def pageV2(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
      mandat: Mandat,
      creatorGroup: Option[UserGroup],
  )(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash,
      request: RequestHeader,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)(
      "Mandat du " + Time.formatPatternFr(mandat.creationDate, "dd MMM YYYY - HH'h'mm")
    )(Html(""))(mandatV2(mandat, creatorGroup))(Nil)

  def mandatDoesNotExist(
      currentUser: User,
      currentUserRights: Authorization.UserRights,
  )(implicit
      webJarsUtil: WebJarsUtil,
      flash: Flash,
      request: RequestHeader,
      mainInfos: MainInfos
  ): Html =
    views.html.main(currentUser, currentUserRights, maxWidth = false)(
      "Erreur, ce mandat n’existe pas"
    )(Html(""))(
      div(
        cls := "mdl-cell mdl-cell--12-col do-not-print",
        div(
          cls := "notification notification--error",
          span(
            "Aucun mandat n’existe sur cette page. ",
            "Si vous avez cliqué sur un lien, ce lien n’est pas valide, ",
            "et il s’agit d’une erreur. ",
            "Vous pouvez rapporter cette erreur à l’équipe Administration+ ",
            a(href := s"mailto:${Constants.supportEmail}", Constants.supportEmail),
            ". ",
            "Un mandat générique peut être ",
            a(
              href := Assets.versioned("pdf/mandat_administration_plus_juillet_2019.pdf").url,
              target := "_blank",
              rel := "noopener noreferrer",
              "téléchargé ici",
            ),
            ", il est à remplir à la main et a été validé juridiquement en 2019."
          )
        )
      )
    )(Nil)

  def mandatV1(mandat: Mandat): Tag =
    div(
      cls := "mdl-grid",
      div(
        cls := "mdl-cell mdl-cell--12-col",
        h4(s"""Mandat du ${Time.formatPatternFr(mandat.creationDate, "dd MMM YYYY - HH:mm")}"""),
        div(
          cls := "mdl-grid",
          mandat.applicationId.map(applicationId =>
            div(
              cls := "mdl-cell mdl-cell--12-col",
              a(href := ApplicationController.show(applicationId).url, "Demande liée")
            )
          ),
          div(
            cls := "mdl-cell mdl-cell--12-col",
            "Prénom : ",
            mandat.usagerPrenom
          ),
          div(
            cls := "mdl-cell mdl-cell--12-col",
            "Nom : ",
            mandat.usagerNom
          ),
          div(
            cls := "mdl-cell mdl-cell--12-col",
            "Date de naissance : ",
            mandat.usagerBirthDate
          ),
          div(
            cls := "mdl-cell mdl-cell--12-col",
            "Téléphone : ",
            mandat.usagerPhoneLocal
          ),
          frag(
            mandat.smsThread.map(sms =>
              div(
                cls := "mdl-cell mdl-cell--12-col",
                s"""Message du : ${Time
                    .formatPatternFr(sms.creationDate, "dd MMM YYYY - HH:mm")}""",
                br,
                sms.body
              )
            )
          )
        )
      )
    )

  def mandatV2(mandat: Mandat, creatorGroup: Option[UserGroup]): Frag = {
    def emptyGroupName: String = "_" * 40
    val groupName: String = creatorGroup.map(_.name).getOrElse(emptyGroupName)
    val groupNameBold: Frag =
      creatorGroup.map(group => b(group.name)).getOrElse(emptyGroupName: Frag)

    val creatorGroupEmptyError =
      if (creatorGroup.isEmpty)
        div(
          cls := "mdl-cell mdl-cell--12-col do-not-print",
          div(
            cls := "notification notification--error",
            span(
              "Votre structure n’est pas renseignée dans l’outil Administration+, ",
              "le mandat suivant est un mandat de structure. ",
              "Vous pouvez l’imprimer puis remplir ",
              "à la main le nom de votre structure (ce message n'est pas imprimé). ",
              "Alternativement vous pouvez utiliser votre propre mandat. ",
              "Si vous n’en disposez pas, le mandat à ",
              a(
                href := Assets.versioned("pdf/mandat_administration_plus_juillet_2019.pdf").url,
                target := "_blank",
                rel := "noopener noreferrer",
                "télécharger ici",
              ),
              " est à remplir entièrement à la main et a été validé juridiquement en 2019."
            )
          )
        ).some
      else
        none

    frag(
      creatorGroupEmptyError,
      div(
        cls := "mdl-cell mdl-cell--12-col mdl-grid mandat-print-wrapper single--justify-content-center",
        div(
          cls := "single--max-width-800px single--padding-left-16px single--padding-right-16px single--padding-bottom-16px single--background-color-white",
          h4(
            cls := "typography--text-align-center",
            "Mandat Administration+"
          ),
          p(
            "Je m’appelle : ",
            b(
              mandat.usagerNom,
              " ",
              mandat.usagerPrenom,
            ),
            " ",
            i("(je suis le mandant)"),
            ", né(e) le ",
            b(mandat.usagerBirthDate),
            ".",
            br,
            "Je mandate ",
            groupNameBold,
            " ",
            i("(c’est le mandataire)"),
            " pour débloquer ma situation administrative via/en utilisant la plateforme Administration+.",
            br,
            "J’autorise ",
            groupNameBold,
            " à utiliser à cette fin toutes les données à caractère personnel fournies strictement nécessaires."
          ),
          p(
            "Cette autorisation est conforme aux articles 1984 et suivants du Code civil."
          ),
          p(
            "Je peux annuler mon autorisation à tout moment."
          ),
          p(
            cls := "single--margin-bottom-0px",
            "Pour que ",
            groupNameBold,
            " puisse agir à ma place :",
          ),
          ul(
            li(
              "Je reconnais que l’aidant habilité par ",
              groupName,
              " m’a rappelé l’objet de son intervention, et m’a informé sur la nécessité et l’utilité de informations collectées",
            ),
            li(
              "J’autorise les aidants habilités par ",
              groupName,
              " à utiliser mes données à caractère personnel dans le cadre de ce mandat. Je sais que j’ai des droits sur les informations me concernant : accès, rectification, suppression."
            )
          ),
          p(
            cls := "single--margin-bottom-0px",
            "Les aidants habilités par ",
            groupNameBold,
            " doivent:"
          ),
          ul(
            li("effectuer les démarches à partir des informations que je leur ai données ;"),
            li(
              "collecter et conserver seulement les informations nécessaires aux démarches bloquées ou à celles à qui s’y rattachent;"
            ),
            li(
              "utiliser et communiquer seulement les informations nécessaires aux démarches bloquées ou à celles qui s’y rattachent;"
            ),
            li("m’informer et demander mon autorisation avant d’effectuer d’autres démarches;"),
            li(
              "supprimer* l’ensemble de mes informations personnelles lorsqu’elles ne sont plus utiles ; s’interdire de rendre publiques mes informations personnelles;"
            ),
            li(
              "prendre toutes les précautions pour assurer la sécurité de me informations personnelles."
            ),
          ),
          p(
            "A partir du moment où un aidant habilité par ",
            groupNameBold,
            " réalise à ma place une démarche [via Administration+], il accepte de le faire dans les conditions décrites dans ce document."
          ),
          p(
            "Le présent mandat prend fin à compter de la notification du déblocage de ma situation administrative. À défaut, il est valable pour une durée d’un an renouvelable."
          ),
          p(
            cls := "single--margin-bottom-60px",
            "Date, lieu et signature du mandant"
          ),
          p(
            "* Cette suppression est automatisée sur Administration+ conformément ",
            "aux articles 1.4.5 et 2.3 de nos Conditions Générales d’Utilisation"
          ),
        ),
      ),
      div(
        cls := "single--margin-16px single--width-100pc single--display-flex single--justify-content-center do-not-print",
        button(
          id := "print-mandat-button",
          cls := "mdl-button mdl-js-button mdl-button--raised mdl-button--colored do-not-print",
          "Imprimer"
        ),
      ),
      div(
        cls := "single--margin-bottom-32px single--width-100pc single--display-flex single--justify-content-center do-not-print",
        p(
          "Si le bouton « Imprimer » ne fonctionne pas, ",
          "dans votre navigateur faites « Fichier » puis « Imprimer »."
        )
      ),
      div(
        cls := "do-not-print",
        p(
          cls := "single--font-size-12px",
          "Sinon, le précédent mandat Administration+ version 2019, à remplir entièrement à la main, ",
          "reste valide juridiquement et est disponible au téléchargement en ",
          a(
            href := Assets.versioned("pdf/mandat_administration_plus_juillet_2019.pdf").url,
            target := "_blank",
            rel := "noopener noreferrer",
            "cliquant ici"
          ),
          "."
        )
      )
    )

  }

}
