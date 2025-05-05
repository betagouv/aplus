package views

import cats.syntax.all._
import controllers.routes.LoginController
import scalatags.Text.all._

object login {

  def page(
      featureProConnectEnabled: Boolean,
      proConnectErrorMessage: Option[(String, String)] = None
  ): Tag =
    views.main.publicLayout(
      pageName = "Connexion",
      content = loginInner(featureProConnectEnabled, proConnectErrorMessage),
      breadcrumbs = List(("Connexion", LoginController.loginPage.url)),
      additionalHeadTags = frag(),
      additionalFooterTags = frag()
    )

  private def loginInner(
      featureProConnectEnabled: Boolean,
      proConnectErrorMessage: Option[(String, String)]
  ): Tag =
    div(cls := "fr-grid-row fr-grid-row-gutters fr-grid-row--center")(
      div(cls := "fr-col-12 fr-col-md-8 fr-col-lg-6")(
        h1("Connexion à Administration+"),
        featureProConnectEnabled.some
          .filter(identity)
          .map(_ => proConnectLoginBlock(proConnectErrorMessage)),
        div(
          a(
            href := "/",
            "Revenir à la page d’accueil pour se connecter avec un lien à usage unique."
          )
        )
      )
    )

  private def proConnectLoginBlock(proConnectErrorMessage: Option[(String, String)]): Frag =
    frag(
      div(cls := "fr-mt-10v fr-mb-8v")(
        h2("Se connecter avec ProConnect"),
        proConnectErrorMessage.map { case (title, description) =>
          div(cls := "fr-my-4w fr-alert fr-alert--error")(
            h3(cls := "fr-alert__title")(title),
            p(description)
          )
        },
        // https://www.systeme-de-design.gouv.fr/composants-et-modeles/composants/bouton-franceconnect/
        // https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/bouton_proconnect.md
        div(cls := "fr-connect-group")(
          a(href := LoginController.proConnectLoginRedirection.url, cls := "fr-connect")(
            span(cls := "fr-connect__login")("S’identifier avec"),
            span(cls := "fr-connect__brand")("ProConnect")
          ),
          p(
            a(
              href := "https://www.proconnect.gouv.fr/",
              target := "_blank",
              rel := "noopener",
              title := "Qu’est ce que ProConnect ? - nouvelle fenêtre"
            )("Qu’est ce que ProConnect ?")
          ),
          p(
            "La connexion avec ProConnect est actuellement en phase de test. Il n’est pas possible de créer de nouveau compte, vous devez posséder un compte sur Administration+. Si vous n’en avez pas, vous pouvez demander au responsable de votre structure ou à votre responsable départemental d’en créer un avec l’adresse email que vous utilisez pour vous connecter avec ProConnect."
          )
        )
      ),
      p(cls := "fr-hr-or")("ou"),
    )

}
