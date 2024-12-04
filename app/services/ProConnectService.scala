package services

import cats.data.EitherT
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import helper.Time
import io.jsonwebtoken.{
  Claims,
  Jws,
  JwtParserBuilder,
  Jwts,
  Locator,
  LocatorAdapter,
  ProtectedHeader
}
import io.jsonwebtoken.security.{Jwk, JwkSet, Jwks}
import java.security.{Key, MessageDigest, SecureRandom}
import java.time.{Duration, Instant, ZonedDateTime}
import java.util.Base64
import javax.inject.{Inject, Singleton}
import models.EventType
import modules.AppConfig
import org.apache.pekko.http.scaladsl.model.Uri
import play.api.db.Database
import play.api.libs.json._
import play.api.libs.ws.DefaultBodyWritables.writeableOf_urlEncodedSimpleForm
import play.api.libs.ws.WSClient
import play.api.mvc.{Request, RequestHeader, Result}
import scala.jdk.CollectionConverters._

object ProConnectService {

  implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  object ProviderMetadata {
    implicit val format: Format[ProviderMetadata] = Json.format[ProviderMetadata]
  }

  /** OpenID Connect Discovery 3. OpenID Provider Metadata
    * https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata
    *
    * Note: we only keep the fields we need here.
    */
  case class ProviderMetadata(
      /** Spec: This MUST also be identical to the iss Claim value in ID Tokens issued from this
        * Issuer.
        */
      issuer: String,
      authorizationEndpoint: String,
      tokenEndpoint: String,
      userinfoEndpoint: String,
      jwksUri: String,
  )

  case class DiscoveryMetadata(
      fetchTime: Instant,
      provider: ProviderMetadata,
      jwkSet: JwkSet,
      // https://github.com/jwtk/jjwt?tab=readme-ov-file#key-locator
      keyLocator: Locator[Key],
  )

  case class AuthorizationCode(code: String)

  object TokenResponse {
    implicit val format: Format[TokenResponse] = Json.format[TokenResponse]
  }

  /**   - RFC6749 5.1. Successful Response https://www.rfc-editor.org/rfc/rfc6749.html#section-5.1
    *   - OpenID Connect 3.1.3.3. Successful Token Response
    *     https://openid.net/specs/openid-connect-core-1_0.html#TokenResponse
    */
  case class TokenResponse(
      accessToken: String,
      tokenType: String,
      expiresIn: Option[Int], // AC default: 60
      refreshToken: Option[String],
      scope: Option[String],
      idToken: String
  )

  object TokenErrorResponse {
    implicit val format: Format[TokenErrorResponse] = Json.format[TokenErrorResponse]
  }

  /**   - RFC6749 5.2. Error Response https://www.rfc-editor.org/rfc/rfc6749.html#section-5.2
    *   - OpenID Connect 3.1.3.4. Token Error Response
    *     https://openid.net/specs/openid-connect-core-1_0.html#TokenErrorResponse
    */
  case class TokenErrorResponse(
      error: String,
      errorDescription: Option[String],
      errorUri: Option[String],
  )

  case class IDToken(signedToken: Jws[Claims], subject: String, authTime: Option[Long])

  case class UserInfo(
      signedToken: Jws[Claims],
      subject: String,
      email: String,
      givenName: Option[String],
      usualName: Option[String],
      uid: Option[String],
      siret: Option[String]
  )

  sealed abstract trait Error

  object Error {
    case class FailedGeneratingFromSecureRandom(error: Throwable) extends Error

    case class ProviderConfigurationRequestFailure(error: Throwable) extends Error
    case class ProviderConfigurationErrorResponse(status: Int, body: String) extends Error
    case class ProviderConfigurationUnparsableJson(status: Int, error: Throwable) extends Error
    case class ProviderConfigurationInvalidJson(error: JsError) extends Error

    case class ProviderConfigurationInvalidIssuer(wantedIssuer: String, providedIssuer: String)
        extends Error

    case class NotEnoughElapsedTimeBetweenDiscoveryCalls(lastFetchTime: Instant, now: Instant)
        extends Error

    case object AuthResponseMissingStateInSession extends Error
    case object AuthResponseMissingNonceInSession extends Error
    case class AuthResponseUnparseableState(requestState: String, error: Throwable) extends Error
    case class AuthResponseInvalidState(sessionState: String, requestState: String) extends Error
    case object AuthResponseMissingErrorQueryParam extends Error
    case object AuthResponseMissingStateQueryParam extends Error

    case class AuthResponseEndpointError(
        errorCode: String,
        errorDescription: Option[String],
        errorUri: Option[String]
    ) extends Error

    case class JwksRequestFailure(error: Throwable) extends Error
    case class JwksUnparsableResponse(status: Int, body: String, error: Throwable) extends Error

    case class TokenRequestFailure(error: Throwable) extends Error
    case class TokenResponseUnparsableJson(status: Int, error: Throwable) extends Error

    /** Cannot parse the json of a 200 response */
    case class TokenResponseInvalidJson(error: JsError) extends Error

    /** Cannot parse the json of a 4xx response */
    case class TokenResponseErrorInvalidJson(error: JsError) extends Error
    case class TokenResponseUnknown(status: Int, body: String) extends Error
    case class TokenResponseError(error: TokenErrorResponse) extends Error
    case class TokenResponseInvalidTokenType(tokenType: String) extends Error

    /** Includes invalid signatures */
    case class InvalidIDToken(error: Throwable, claimsNames: Set[String]) extends Error

    case class IDTokenInvalidClaims(error: Option[Throwable], claimsNames: Set[String])
        extends Error

    case class UserInfoRequestFailure(error: Throwable) extends Error

    case class UserInfoResponseUnsuccessfulStatus(
        status: Int,
        wwwAuthenticateHeader: Option[String],
        body: String
    ) extends Error

    case class UserInfoResponseUnknownContentType(contentType: Option[String]) extends Error

    case class UserInfoInvalidClaims(error: Option[Throwable], claimsNames: Set[String])
        extends Error

    case class InvalidJwsAlgorithm(invalidAlgorithm: Option[String]) extends Error
  }

  /** Defined in order to be caught in case of key rotation.
    *
    * OpenID Connect 10.1.1. Rotation of Asymmetric Signing Keys
    * https://openid.net/specs/openid-connect-core-1_0.html#RotateSigKeys
    */
  case class UnknownKidInJwtHeader(message: String) extends Exception(message)

  /** Spec:
    *   - https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/implementation_technique.md#35-authentification-de-lutilisateur
    *   - la session Agent Connect a une durée de 12 heures et se termine dans tous les cas à la fin
    *     de la journée (minuit)
    */
  def calculateExpiresAt(now: Instant): IO[Instant] = IO {
    val currentDateTime = ZonedDateTime.ofInstant(now, Time.timeZoneParis)
    val endOfDay =
      currentDateTime.toLocalDate.plusDays(1).atStartOfDay(Time.timeZoneParis)
    val after12Hours = currentDateTime.plusHours(12)
    val expiresAt = if (after12Hours.isBefore(endOfDay)) after12Hours else endOfDay
    expiresAt.toInstant
  }

}

@Singleton
class ProConnectService @Inject() (
    config: AppConfig,
    db: Database,
    dependencies: ServicesDependencies,
    eventService: EventService,
    ws: WSClient,
) {
  import ProConnectService._

  import dependencies.ioRuntime

  //
  //
  // Discovery
  //
  //

  /** Ref is just a wrapper around AtomicReference
    *   - https://github.com/typelevel/cats-effect/blob/d5a3d4ce31d6e76ec8c5461ab4179a5ed9cdd6a4/kernel/jvm/src/main/scala/cats/effect/kernel/SyncRef.scala#L25
    *   - https://typelevel.org/cats-effect/docs/std/ref
    *
    * This code is not referentially transparent. This is done for simplicity, as the component
    * creation is handled by Play.
    */
  private lazy val discoveryMetadataRef: Ref[IO, Either[Error, DiscoveryMetadata]] = {
    val metadata = fetchDiscoveryMetadata.value.unsafeRunSync()
    Ref.unsafe(metadata)
  }

  private def discoveryMetadata: EitherT[IO, Error, DiscoveryMetadata] =
    EitherT(discoveryMetadataRef.get)

  private def updateDiscoveryMetadata: EitherT[IO, Error, DiscoveryMetadata] =
    EitherT(
      IO.realTimeInstant.flatMap(now =>
        discoveryMetadataRef.get.flatMap {
          case Right(metadata)
              if Duration
                .between(metadata.fetchTime, now)
                .toMillis < config.proConnectMinimumDurationBetweenDiscoveryCalls.toMillis =>
            IO.pure(
              Error
                .NotEnoughElapsedTimeBetweenDiscoveryCalls(
                  lastFetchTime = metadata.fetchTime,
                  now = now
                )
                .asLeft
            )
          case _ =>
            (IO.blocking(
              eventService.logNoRequest(
                EventType.ProConnectUpdateProviderConfiguration,
                "Tentative de mise à jour de la provider configuration ProConnect"
              )
            ) >> fetchDiscoveryMetadata.value)
              .flatMap(
                _.fold(
                  error => IO.pure(error.asLeft),
                  newMetadata =>
                    discoveryMetadataRef.set(newMetadata.asRight).as(newMetadata.asRight)
                )
              )
        }
      )
    )

  /**   - OpenID Connect Discovery 4.1. OpenID Provider Configuration Request
    *     https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest
    *   - OpenID Connect Discovery 4.3. OpenID Provider Configuration Validation
    *     https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationValidation
    */
  private def providerConfigurationRequest: EitherT[IO, Error, ProviderMetadata] =
    EitherT(
      IO.fromFuture(
        // Spec: If the Issuer value contains a path component, any terminating / MUST be removed before appending /.well-known/openid-configuration
        IO.pure(
          ws.url(
            config.proConnectIssuerUri.stripSuffix("/") + "/.well-known/openid-configuration"
          ).get()
        )
      ).attempt
        .map(_.left.map[Error](Error.ProviderConfigurationRequestFailure.apply))
    )
      .subflatMap(response =>
        // Spec: A successful response MUST use the 200 OK HTTP status code
        if (response.status === 200)
          Either
            .catchNonFatal(Json.parse(response.body))
            .leftMap(e => Error.ProviderConfigurationUnparsableJson(response.status, e))
            .flatMap(_.validate[ProviderMetadata] match {
              case JsSuccess(token, _) => token.asRight
              case e: JsError          => Error.ProviderConfigurationInvalidJson(e).asLeft
            })
            .flatMap(metadata =>
              // TODO: check endpoints are https://
              // Spec: The issuer value returned MUST be identical to the Issuer URL that was used as the prefix to /.well-known/openid-configuration to retrieve the configuration information.
              // See also "OpenID Connect Discovery 7.2.  Impersonation Attacks  https://openid.net/specs/openid-connect-discovery-1_0.html#Impersonation"
              if (metadata.issuer === config.proConnectIssuerUri)
                // Spec: Communication with the Token Endpoint MUST utilize TLS.
                // OpenID Connect 3.1.3. Token Endpoint  https://openid.net/specs/openid-connect-core-1_0.html#TokenEndpoint
                metadata.asRight
              else
                Error
                  .ProviderConfigurationInvalidIssuer(
                    wantedIssuer = config.proConnectIssuerUri,
                    providedIssuer = metadata.issuer
                  )
                  .asLeft
            )
        else
          Error.ProviderConfigurationErrorResponse(response.status, response.body).asLeft
      )

  /**   - OpenID Connect Discovery 3. OpenID Provider Metadata
    *     https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata
    *   - OpenID Connect 10.1. Signing https://openid.net/specs/openid-connect-core-1_0.html#Signing
    */
  private def jwksRequest(jwksUri: String): EitherT[IO, Error, (JwkSet, Locator[Key])] =
    EitherT(
      IO.fromFuture(IO.pure(ws.url(jwksUri).get()))
        .attempt
        .map(_.left.map[Error](Error.JwksRequestFailure.apply))
    ).subflatMap(response =>
      Either
        .catchNonFatal(Jwks.setParser().build().parse(response.body))
        .left
        .map(e => Error.JwksUnparsableResponse(response.status, response.body, e))
    ).subflatMap { keyset =>
      val keyLocator: Locator[Key] = new LocatorAdapter[Key] {
        val keys: List[Jwk[_]] = keyset.getKeys.asScala.toList
        override def locate(header: ProtectedHeader): Key =
          Option(header.getKeyId) match {
            case None =>
              val headerKeys = header.keySet.asScala.mkString(", ")
              val message = s"JWT header does not have kid, header keys are $headerKeys"
              throw new Exception(message)
            case Some(keyId) =>
              keys.find(_.getId === keyId) match {
                // Spec: If there are multiple keys in the referenced JWK Set document, a kid value MUST be provided in the JOSE Header. The key usage of the respective keys MUST support signing.
                case None =>
                  val knownKids = keys.map(_.getId).mkString(", ")
                  val message = s"JWT needs kid $keyId while known kids are $knownKids"
                  throw UnknownKidInJwtHeader(message)
                case Some(jwk) => jwk.toKey.asInstanceOf[Key]
              }
          }
      }
      (keyset, keyLocator).asRight
    }

  private def fetchDiscoveryMetadata: EitherT[IO, Error, DiscoveryMetadata] =
    for {
      now <- EitherT.right(IO.realTimeInstant)
      provider <- providerConfigurationRequest
      jwks <- jwksRequest(provider.jwksUri)
      (jwkSet, keyLocator) = jwks
    } yield DiscoveryMetadata(
      fetchTime = now,
      provider = provider,
      jwkSet = jwkSet,
      keyLocator = keyLocator,
    )

  //
  //
  // Core
  //
  //

  val NONCE_SIZE_BYTES = 16

  /** CSRF token size, minimum recommended is 128 bits,
    * https://security.stackexchange.com/questions/6957/length-of-csrf-token
    */
  val STATE_SIZE_BYTES = 24

  val sessionStateKey = "pro-connect_state"
  val sessionNonceKey = "pro-connect_nonce"
  val sessionIdTokenKey = "pro-connect_idtoken"

  def resetSessionKeys(result: Result)(implicit request: RequestHeader): Result =
    result.removingFromSession(sessionStateKey, sessionNonceKey)

  /**   - OpenID Connect 3.1.2.1. Authentication Request
    *     https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest OpenID Connect
    *   - OpenID Connect 15.5.2. Nonce Implementation Notes
    *     https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes
    *   - https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/implementation_technique.md#2-faire-pointer-le-bouton-proconnect-vers-le-authorization_endpoint
    */
  def authenticationRequestUrl: EitherT[IO, Error, (String, (Result, RequestHeader) => Result)] =
    discoveryMetadata.subflatMap { metadata =>
      for {
        nonce <- base64EncodedBytesFromCsprng(NONCE_SIZE_BYTES)
        state <- base64EncodedBytesFromCsprng(STATE_SIZE_BYTES)
      } yield {

        // OpenID Connect 5.5.  Requesting Claims using the "claims" Request Parameter  https://openid.net/specs/openid-connect-core-1_0.html#ClaimsParameter
        val claims = Json.stringify(
          Json.obj(
            "userinfo" -> Json.obj(
              "given_name" -> JsNull,
              "usual_name" -> JsNull,
              "email" -> Json.obj("essential" -> true),
              "siret" -> JsNull,
            )
            // ID token claims: https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/scope-claims.md#les-donn%C3%A9es-sur-lauthentification
          )
        )

        val uri = Uri(metadata.provider.authorizationEndpoint).withQuery(
          Uri.Query(
            // 1. OAuth 2.0 parameters
            //
            // OpenID Connect 5.4.  Requesting Claims using Scope Values  https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims
            // Multiple scope values MAY be used by creating a space-delimited, case-sensitive list of ASCII scope values.
            "scope" -> "openid given_name usual_name email siret",
            "response_type" -> "code",
            "client_id" -> config.proConnectClientId,
            "redirect_uri" -> config.proConnectRedirectUri,
            "state" -> state,

            // 2. OpenID Connect parameters
            //
            // OpenID Connect 15.5.2.  Nonce Implementation Notes  https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes
            "nonce" -> nonce,
            "prompt" -> "login",
            "acr_values" -> "eidas1",
            // OpenID Connect 5.5.  Requesting Claims using the "claims" Request Parameter  https://openid.net/specs/openid-connect-core-1_0.html#ClaimsParameter
            "claims" -> claims,
          )
        )

        // Spec "15.5.2.  Nonce Implementation Notes" recommends using a cryptographic hash of the random bytes, since Play session is signed (and HttpOnly), we store the nonce directly inside it.
        // AC "générer un state et un nonce aléatoires et stockez-les dans la session du navigateur"
        val addToSession = (result: Result, request: RequestHeader) =>
          result.addingToSession(sessionStateKey -> state, sessionNonceKey -> nonce)(request)
        (uri.toString, addToSession)
      }
    }

  /** https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/implementation_technique.md#34-stockage-du-id_token
    *
    * Stocker le id_token dans la session du navigateur. Cette valeur sera utilisée plus tard, lors
    * de la déconnexion auprès du serveur ProConnect.
    */
  def handleAuthenticationResponse(
      request: Request[_]
  )(
      onFailure: Error => IO[Result]
  )(onSuccess: (IDToken, UserInfo) => IO[Result]): IO[Result] =
    authorizationResponseCodeFlow(request).value.flatMap(
      _.fold(
        error =>
          onFailure(error).map(
            _.removingFromSession(sessionStateKey, sessionNonceKey, sessionIdTokenKey)(request)
          ),
        { case (tokenResponse, idToken, userInfo) =>
          onSuccess(idToken, userInfo).map(
            _.removingFromSession(sessionStateKey, sessionNonceKey)(request)
              .addingToSession(sessionIdTokenKey -> tokenResponse.idToken)(request)
          )
        }
      )
    )

  /**   - OpenID Connect 3.1.2.5. Successful Authentication Response
    *     https://openid.net/specs/openid-connect-core-1_0.html#AuthResponse
    *   - OpenID Connect 3.1.2.6. Authentication Error Response
    *     https://openid.net/specs/openid-connect-core-1_0.html#AuthError
    *   - RFC6749 4.1.2. Authorization Response
    *     https://www.rfc-editor.org/rfc/rfc6749.html#section-4.1.2
    *   - RFC6749 10.5. Authorization Codes https://www.rfc-editor.org/rfc/rfc6749.html#section-10.5
    *   - RFC6749 10.12. Cross-Site Request Forgery
    *     https://www.rfc-editor.org/rfc/rfc6749.html#section-10.12
    *   - RFC6749 10.14. Code Injection and Input Validation
    *     https://www.rfc-editor.org/rfc/rfc6749.html#section-10.14
    *
    * Important note from the spec: If an authorization code is used more than once, the
    * authorization server MUST deny the request and SHOULD revoke (when possible) all tokens
    * previously issued based on that authorization code.
    */
  private def authorizationResponseCode(request: Request[_]): Either[Error, AuthorizationCode] = {
    def verifyingState[A](requestState: String, ifValid: => Either[Error, A]): Either[Error, A] =
      request.session.get(sessionStateKey) match {
        case None =>
          Error.AuthResponseMissingStateInSession.asLeft
        case Some(sessionState) =>
          // This check is considered to be sanitization and validation of the "state"
          isConstantTimeEqualBase64(requestState, sessionState).left
            .map[Error](error => Error.AuthResponseUnparseableState(requestState, error))
            .flatMap(statesAreEqual =>
              // CSRF Check
              if (statesAreEqual) {
                ifValid
              } else {
                Error
                  .AuthResponseInvalidState(
                    sessionState = sessionState,
                    requestState = requestState
                  )
                  .asLeft
              }
            )
      }

    // Spec: The client MUST ignore unrecognized response parameters.
    (request.getQueryString("code"), request.getQueryString("state")) match {
      case (Some(code), Some(requestState)) =>
        verifyingState(requestState, AuthorizationCode(code).asRight)

      // Error case
      // - RFC6749 4.1.2.1.  Error Response  https://www.rfc-editor.org/rfc/rfc6749.html#section-4.1.2.1
      // - OpenID Connect 3.1.2.6.  Authentication Error Response  https://openid.net/specs/openid-connect-core-1_0.html#AuthError
      case _ =>
        (
          request.getQueryString("error"),
          request.getQueryString("error_description"),
          request.getQueryString("error_uri"),
          request.getQueryString("state")
        ) match {
          case (None, _, _, _) => Error.AuthResponseMissingErrorQueryParam.asLeft
          case (_, _, _, None) => Error.AuthResponseMissingStateQueryParam.asLeft
          case (Some(errorCode), errorDescription, errorUri, Some(requestState)) =>
            // TODO: validate code+description+uri
            verifyingState(
              requestState,
              Error.AuthResponseEndpointError(errorCode, errorDescription, errorUri).asLeft
            )
        }
    }
  }

  /** Relevant docs:
    *   - https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/implementation_technique.md#32-g%C3%A9n%C3%A9ration-du-token
    *   - OpenID Connect 3.1.3. Token Endpoint
    *     https://openid.net/specs/openid-connect-core-1_0.html#TokenEndpoint
    *   - RFC6749 3.2. Token Endpoint https://www.rfc-editor.org/rfc/rfc6749.html#section-3.2
    *   - RFC6749 4.1.3. Access Token Request
    *     https://www.rfc-editor.org/rfc/rfc6749.html#section-4.1.3
    *   - RFC6749 2.3.1. Client Password https://www.rfc-editor.org/rfc/rfc6749.html#section-2.3.1
    *   - RFC6749 4.1.4. Access Token Response
    *     https://www.rfc-editor.org/rfc/rfc6749.html#section-4.1.4
    *   - RFC6749 5.1. Successful Response https://www.rfc-editor.org/rfc/rfc6749.html#section-5.1
    *   - RFC6749 5.2. Error Response https://www.rfc-editor.org/rfc/rfc6749.html#section-5.2
    */
  private def tokenRequest(code: AuthorizationCode): EitherT[IO, Error, TokenResponse] =
    discoveryMetadata.flatMap { metadata =>
      EitherT(
        IO.fromFuture(
          IO.pure(
            // Content-Type is set to application/x-www-form-urlencoded
            // https://github.com/playframework/play-ws/blob/a98acd48ee3a0c28769a5981885f9604bdf4d65b/play-ws-standalone/src/main/scala/play/api/libs/ws/DefaultBodyWritables.scala#L113
            ws.url(metadata.provider.tokenEndpoint)
              .post(
                Map(
                  "grant_type" -> "authorization_code",
                  "code" -> code.code,
                  "redirect_uri" -> config.proConnectRedirectUri,
                  "client_id" -> config.proConnectClientId,
                  "client_secret" -> config.proConnectClientSecret,
                )
              )
          )
        ).attempt
          .map(_.left.map(Error.TokenRequestFailure.apply))
      )
        .subflatMap(response =>
          if (response.status === 200)
            Either
              .catchNonFatal(Json.parse(response.body))
              .leftMap(e => Error.TokenResponseUnparsableJson(response.status, e))
              .flatMap(_.validate[TokenResponse] match {
                case JsSuccess(response, _) => response.asRight
                case e: JsError             => Error.TokenResponseInvalidJson(e).asLeft
              })
              // Spec: The OAuth 2.0 token_type response parameter value MUST be Bearer
              // Spec: Note that the token_type value is case insensitive.
              .flatMap(response =>
                if (response.tokenType.toLowerCase === "bearer")
                  response.asRight
                else
                  Error.TokenResponseInvalidTokenType(response.tokenType).asLeft
              )
          else if (response.status % 100 === 4)
            Either
              .catchNonFatal(Json.parse(response.body))
              .leftMap(e => Error.TokenResponseUnparsableJson(response.status, e))
              .flatMap(_.validate[TokenErrorResponse] match {
                case JsSuccess(response, _) => Error.TokenResponseError(response).asLeft
                case e: JsError             => Error.TokenResponseErrorInvalidJson(e).asLeft
              })
          else
            Error.TokenResponseUnknown(response.status, response.body).asLeft
        )
    }

  /**   - 3.1.3.7. ID Token Validation
    *     https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
    */
  private def idTokenValidation(
      tokenResponse: TokenResponse,
      sessionNonce: String
  ): EitherT[IO, Error, IDToken] =
    discoveryMetadata.flatMap { metadata =>
      parseSignedJwtOrRetryWithDiscovery(
        tokenResponse.idToken,
        metadata,
        _
          // Spec: The Issuer Identifier for the OpenID Provider (which is typically obtained during Discovery) MUST exactly match the value of the iss (issuer) Claim.
          .requireIssuer(metadata.provider.issuer)
          // Spec: The Client MUST validate that the aud (audience) Claim contains its client_id value registered at the Issuer identified by the iss (issuer) Claim as an audience. The aud (audience) Claim MAY contain an array with more than one element. The ID Token MUST be rejected if the ID Token does not list the Client as a valid audience, or if it contains additional audiences not trusted by the Client.
          .requireAudience(config.proConnectClientId)
          // Note: jjwt validates "exp" by default
          // - https://github.com/jwtk/jjwt/blob/5812f63a76084914c5b653025bd3b84048389223/impl/src/main/java/io/jsonwebtoken/impl/DefaultJwtParser.java#L677
          // - https://github.com/jwtk/jjwt/blob/5812f63a76084914c5b653025bd3b84048389223/impl/src/main/java/io/jsonwebtoken/impl/DefaultClaims.java#L48
          .require("nonce", sessionNonce)
      )
        .subflatMap(signedToken =>
          Either.catchNonFatal(
            (
              Option(signedToken.getPayload.get("sub", classOf[String])),
              Option(signedToken.getPayload.get("auth_time", classOf[Long]))
            )
          ) match {
            case Right((Some(subject), authTime)) =>
              IDToken(signedToken = signedToken, subject = subject, authTime = authTime).asRight
            case invalidClaims =>
              val error = invalidClaims.left.toOption
              val claimsNames = signedToken.getPayload.keySet.asScala.toSet
              Error.IDTokenInvalidClaims(error, claimsNames).asLeft
          }
        )
    }

  /**   - OpenID Connect 5.3. UserInfo Endpoint
    *     https://openid.net/specs/openid-connect-core-1_0.html#UserInfo
    */
  private def userInfoRequest(
      accessToken: String,
      idTokenSubjectClaim: String
  ): EitherT[IO, Error, UserInfo] =
    discoveryMetadata.flatMap { metadata =>
      EitherT(
        IO.fromFuture(
          IO.pure(
            ws.url(metadata.provider.userinfoEndpoint)
              .withHttpHeaders("Authorization" -> s"Bearer $accessToken")
              .get()
          )
        ).attempt
          .map(_.left.map[Error](Error.UserInfoRequestFailure.apply))
      )
        .flatMap(response =>
          if (response.status === 200)
            response.header("Content-Type") match {
              // Spec: If the UserInfo Response is signed and/or encrypted, then the Claims are returned in a JWT and the content-type MUST be application/jwt.
              case Some("application/jwt") =>
                parseSignedJwtOrRetryWithDiscovery(
                  response.body,
                  metadata,
                  _
                    // Spec: The sub Claim in the UserInfo Response MUST be verified to exactly match the sub Claim in the ID Token
                    .requireSubject(idTokenSubjectClaim)
                    // Spec: If signed, the UserInfo Response MUST contain the Claims iss (issuer) and aud (audience) as members. The iss value MUST be the OP's Issuer Identifier URL. The aud value MUST be or include the RP's Client ID value.
                    .requireIssuer(metadata.provider.issuer)
                    .requireAudience(config.proConnectClientId)
                ).subflatMap { signedToken =>
                  Either.catchNonFatal(
                    (
                      Option(signedToken.getPayload.getSubject),
                      Option(signedToken.getPayload.get("email", classOf[String])),
                      Option(signedToken.getPayload.get("given_name", classOf[String])),
                      Option(signedToken.getPayload.get("usual_name", classOf[String])),
                      Option(signedToken.getPayload.get("uid", classOf[String])),
                      Option(signedToken.getPayload.get("siret", classOf[String]))
                    )
                  ) match {
                    case Right((Some(subject), Some(email), givenName, usualName, uid, siret)) =>
                      UserInfo(
                        signedToken = signedToken,
                        subject = subject,
                        email = email,
                        givenName = givenName,
                        usualName = usualName,
                        uid = uid,
                        siret = siret
                      ).asRight
                    case invalidClaims =>
                      val error = invalidClaims.left.toOption
                      val claimsNames = signedToken.getPayload.keySet.asScala.toSet
                      Error.UserInfoInvalidClaims(error, claimsNames).asLeft
                  }
                }
              case contentType =>
                EitherT.leftT(Error.UserInfoResponseUnknownContentType(contentType))
            }
          else
            // RFC6750 3.  The WWW-Authenticate Response Header Field  https://www.rfc-editor.org/rfc/rfc6750.html#section-3
            EitherT.leftT(
              Error
                .UserInfoResponseUnsuccessfulStatus(
                  response.status,
                  response.header("WWW-Authenticate"),
                  response.body
                )
            )
        )
    }

  private def authorizationResponseCodeFlow(
      request: Request[_]
  ): EitherT[IO, Error, (TokenResponse, IDToken, UserInfo)] =
    for {
      // Note that discoveryMetadata is not called at the beginning, in order to better handle
      // possible key rotations
      code <- EitherT.fromEither[IO](authorizationResponseCode(request))
      tokenResponse <- tokenRequest(code)
      sessionNonce <- EitherT
        .fromOption[IO](
          request.session.get(sessionNonceKey),
          Error.AuthResponseMissingNonceInSession
        )
      idToken <- idTokenValidation(tokenResponse, sessionNonce)
      userInfo <- userInfoRequest(tokenResponse.accessToken, idToken.subject)
    } yield {
      (tokenResponse, idToken, userInfo)
    }

  //
  // Utils
  //

  private def base64EncodedBytesFromCsprng(numberOfBytes: Int): Either[Error, String] = Either
    .catchNonFatal {
      val bytes = Array.ofDim[Byte](numberOfBytes)
      new SecureRandom().nextBytes(bytes)
      Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    .left
    .map(Error.FailedGeneratingFromSecureRandom.apply)

  /** Uses MessageDigest.isEqual that is documented to be a constant time equality check.
    *
    * https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html#isEqual(byte%5B%5D,byte%5B%5D)
    *
    * We consider Base64.getUrlDecoder().decode() to be the "sanitization" step.
    *
    * RFC6749 10.14. Code Injection and Input Validation
    * https://www.rfc-editor.org/rfc/rfc6749.html#section-10.14
    *
    * From spec: The authorization server and client MUST sanitize (and validate when possible) any
    * value received -- in particular, the value of the "state" and "redirect_uri" parameters.
    */
  private def isConstantTimeEqualBase64(
      aBase64: String,
      bBase64: String
  ): Either[Throwable, Boolean] = Either.catchNonFatal {
    // Accepts base64 without pad: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Base64.Decoder.html
    val decoder = Base64.getUrlDecoder()
    val a = decoder.decode(aBase64)
    val b = decoder.decode(bBase64)
    MessageDigest.isEqual(a, b)
  }

  /** If the kid in the JWT header is unknown, we try to update our cached discovery metadata.
    *
    *   - OpenID Connect 10.1.1. Rotation of Asymmetric Signing Keys
    *     https://openid.net/specs/openid-connect-core-1_0.html#RotateSigKeys
    */
  private def parseSignedJwtOrRetryWithDiscovery(
      jwt: String,
      metadata: DiscoveryMetadata,
      requirements: JwtParserBuilder => JwtParserBuilder
  ): EitherT[IO, Error, Jws[Claims]] =
    EitherT(IO.pure(parseSignedJwtOnce(jwt, metadata, requirements)))
      .leftFlatMap[Jws[Claims], Error] {
        case UnknownKidInJwtHeader(_) =>
          for {
            updatedMetadata <- updateDiscoveryMetadata
            // We retry only once
            signedToken <- EitherT(IO.pure(parseSignedJwtOnce(jwt, updatedMetadata, requirements)))
              .leftMap[Error](error =>
                Error.InvalidIDToken(error, parseJwtClaimsNamesUnsecurely(jwt))
              )
          } yield signedToken
        case error =>
          EitherT.leftT[IO, Jws[Claims]](
            Error.InvalidIDToken(error, parseJwtClaimsNamesUnsecurely(jwt))
          )
      }
      .subflatMap { jws =>
        val algorithm: Option[String] =
          Option(jws.getHeader).flatMap(header => Option(header.getAlgorithm))
        if (algorithm === Some(config.proConnectSigningAlgorithm))
          jws.asRight
        else
          Error.InvalidJwsAlgorithm(algorithm).asLeft
      }

  private def parseSignedJwtOnce(
      jwt: String,
      metadata: DiscoveryMetadata,
      requirements: JwtParserBuilder => JwtParserBuilder
  ): Either[Throwable, Jws[Claims]] =
    Either
      .catchNonFatal(
        requirements(
          Jwts
            .parser()
            .keyLocator(metadata.keyLocator)
        )
          .build()
          .parseSignedClaims(jwt)
      )

  private def parseJwtClaimsNamesUnsecurely(jwt: String): Set[String] =
    Either
      .catchNonFatal(
        Jwts
          .parser()
          .build()
          .parseUnsecuredClaims(jwt)
          .getPayload
          .keySet
          .asScala
          .toSet
      )
      .fold(_ => Set.empty, identity)

}
