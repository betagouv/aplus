package models

import java.time.Instant
import java.util.UUID

/** https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/donnees_fournies.md#le-champ-sub
  *
  * ProConnect transmet systématiquement au Fournisseur de Services un identifiant unique pour
  * chaque agent (le sub) : cet identifiant est spécifique à chaque Fournisseur d'Identité. Il est
  * recommandé de l'utiliser pour effectuer la réconciliation d'identité.
  *
  * Le code est basé sur l'ancienne documentation :
  *
  * AgentConnect transmet systématiquement au Fournisseur de Services un identifiant unique pour
  * chaque agent (le sub) : cet identifiant est spécifique à chaque couple Fournisseur de Services /
  * Fournisseur d'Identité. Il ne peut donc pas être utilisé pour faire de la réconciliation
  * d'identité : nous vous recommandons l'utilisation de l'email professionnel pour cet usage.
  */
case class ProConnectClaims(
    subject: String,
    email: String,
    givenName: Option[String],
    usualName: Option[String],
    uid: Option[String],
    siret: Option[String],
    creationDate: Instant,
    lastAuthTime: Option[Instant],
    userId: Option[UUID]
)
