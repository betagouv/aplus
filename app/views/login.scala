package views

import cats.syntax.all._
import controllers.routes.LoginController
import scalatags.Text.all._

object login {

  def page(
      featureAgentConnectEnabled: Boolean,
      agentConnectErrorMessage: Option[(String, String)] = None
  ): Tag =
    views.main.publicLayout(
      pageName = "Connexion",
      content = loginInner(featureAgentConnectEnabled, agentConnectErrorMessage),
      breadcrumbs = List(("Connexion", LoginController.loginPage.url)),
      additionalHeadTags = frag(),
      additionalFooterTags = frag()
    )

  private def loginInner(
      featureAgentConnectEnabled: Boolean,
      agentConnectErrorMessage: Option[(String, String)]
  ): Tag =
    div(cls := "fr-grid-row fr-grid-row-gutters fr-grid-row--center")(
      div(cls := "fr-col-12 fr-col-md-8 fr-col-lg-6")(
        h1("Connexion à Administration+"),
        featureAgentConnectEnabled.some
          .filter(identity)
          .map(_ => agentConnectLoginBlock(agentConnectErrorMessage)),
        div(
          a(
            href := "/",
            "Revenir à la page d’accueil pour se connecter avec un lien à usage unique."
          )
        )
      )
    )

  private def agentConnectLoginBlock(agentConnectErrorMessage: Option[(String, String)]): Frag =
    frag(
      div(cls := "fr-mt-10v fr-mb-8v")(
        h2("Se connecter avec AgentConnect"),
        agentConnectErrorMessage.map { case (title, description) =>
          div(cls := "fr-my-4w fr-alert fr-alert--error")(
            h3(cls := "fr-alert__title")(title),
            p(description)
          )
        },
        // https://www.systeme-de-design.gouv.fr/composants-et-modeles/composants/bouton-franceconnect/
        // https://github.com/numerique-gouv/agentconnect-documentation/blob/main/doc_fs/bouton_agentconnect.md
        div(cls := "fr-connect-group")(
          a(href := LoginController.agentConnectLoginRedirection.url, cls := "fr-connect")(
            span(cls := "fr-connect__login")("S’identifier avec"),
            span(cls := "fr-connect__brand")("AgentConnect")
          ),
          p(
            a(
              href := "https://agentconnect.gouv.fr/",
              target := "_blank",
              rel := "noopener",
              title := "Qu’est ce que AgentConnect ? - nouvelle fenêtre"
            )("Qu’est ce que AgentConnect ?")
          ),
          p(
            "La connexion avec AgentConnect est actuellement en phase de test. Il n’est pas possible de créer de nouveau compte, vous devez posséder un compte sur Administration+. Si vous n’en avez pas, vous pouvez demander au responsable de votre structure ou à votre responsable départemental d’en créer un avec l’adresse email que vous utilisez pour vous connecter avec AgentConnect."
          )
        )
      ),
      p(cls := "fr-hr-or")("ou"),
    )

}
