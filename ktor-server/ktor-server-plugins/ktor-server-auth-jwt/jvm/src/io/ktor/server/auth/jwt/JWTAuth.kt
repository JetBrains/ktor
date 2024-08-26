/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.interfaces.*
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import org.slf4j.*
import java.util.*
import kotlin.reflect.*

internal val JWTAuthKey: Any = "JWTAuth"

internal val JWTLogger: Logger = LoggerFactory.getLogger("io.ktor.auth.jwt")

/**
 * Shortcut functions for standard registered JWT claims.
 */
public abstract class JWTPayloadHolder(
    /**
     * A JWT payload.
     */
    public val payload: Payload
) {

    /**
     * Gets a value of the `iss` (issuer) claim, which specifies the issuer of the JWT.
     */
    public val issuer: String? get() = payload.issuer

    /**
     * Gets a value of the `sub` (subject) claim, or null if it's not available.
     * The `sub` claim specifies a subject of the JWT (the user).
     */
    public val subject: String? get() = payload.subject

    /**
     * Gets the value of the `aud` (audience) claim, or an empty list if it's not available.
     * The `aud` claim specifies a recipient for which the JWT is intended.
     */
    public val audience: List<String> get() = payload.audience ?: emptyList()

    /**
     * Gets the value of the `exp` (expiration time) claim, or null if it's not available.
     * This claim specifies a time after which the JWT expires.
     */
    public val expiresAt: Date? get() = payload.expiresAt

    /**
     * Gets the value of the `nbf` (not before time) claim, or null if it's not available.
     * The `nbf` specifies a time before which the JWT must not be accepted for processing.
     */
    public val notBefore: Date? get() = payload.notBefore

    /**
     * Gets the value of the `iat` (issued at) claim, or null if it's not available.
     * The `iat` claim specifies a time at which the JWT was issued.
     * This claim can be used to determine the age of the JWT.
     */
    public val issuedAt: Date? get() = payload.issuedAt

    /**
     * Gets the value of the `jti` (JWT ID) claim, or null if it's not available.
     * The `jti` claim specifies provides a unique identifier for the JWT.
     * This claim can be used to prevent the JWT from being replayed
     * (allows a token to be used only once).
     */
    public val jwtId: String? get() = payload.id

    /**
     * Gets a non-RFC JWT claim by its name.
     *
     * @param name a claim's key as it appears in the JSON object
     * @return a claim's value or null if not available or not a string
     */
    public operator fun get(name: String): String? {
        return payload.getClaim(name).asString()
    }

    /**
     * Gets a non-RFC JWT claim by its name and attempts to decode it as the specified type.
     *
     * @param name a claim's key as it appears in the JSON object
     * @return a claim's value or null if not available or unable to deserialize
     */
    public fun <T : Any> getClaim(name: String, clazz: KClass<T>): T? {
        return try {
            payload.getClaim(name).`as`(clazz.javaObjectType)
        } catch (ex: JWTDecodeException) {
            null
        }
    }

    /**
     * Retrieves a non-RFC JWT claim by its name and attempts to decode it as a list of the specified type.
     *
     * @param name a claim's key as it appears in the JSON object
     * @return a claim's value or an empty list if not available or unable to deserialize
     */
    public fun <T : Any> getListClaim(name: String, clazz: KClass<T>): List<T> {
        return try {
            payload.getClaim(name).asList(clazz.javaObjectType) ?: emptyList()
        } catch (ex: JWTDecodeException) {
            emptyList()
        }
    }
}

/**
 * A JWT credential that consists of the specified [payload].
 * @param payload JWT
 * @see Payload
 */
public class JWTCredential(payload: Payload) : JWTPayloadHolder(payload)

/**
 * A JWT principal that consists of the specified [payload].
 * @param payload JWT
 * @see Payload
 */
public class JWTPrincipal(payload: Payload) : JWTPayloadHolder(payload)

/**
 * A JWT verifier function used to verify a token format and its signature.
 */
public typealias JWTConfigureFunction = Verification.() -> Unit

/**
 * A JWT [Authentication] provider.
 *
 * @see [jwt]
 */
public class JWTAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    private val realm: String = config.realm
    private val schemes: JWTAuthSchemes = config.schemes
    private val authHeader: (ApplicationCall) -> HttpAuthHeader? = config.authHeader
    private val verifier: ((HttpAuthHeader) -> JWTVerifier?) = config.verifier
    private val authenticationFunction = config.authenticationFunction
    private val challengeFunction: JWTAuthChallengeFunction = config.challenge

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val token = authHeader(call)
        if (token == null) {
            context.bearerChallenge(AuthenticationFailedCause.NoCredentials, realm, schemes, challengeFunction)
            return
        }

        try {
            val principal = verifyAndValidate(call, verifier(token), token, schemes, authenticationFunction)
            if (principal != null) {
                context.principal(name, principal)
                return
            }

            context.bearerChallenge(
                AuthenticationFailedCause.InvalidCredentials,
                realm,
                schemes,
                challengeFunction
            )
        } catch (cause: Throwable) {
            val message = cause.message ?: cause.javaClass.simpleName
            JWTLogger.trace("JWT verification failed", cause)
            context.error(JWTAuthKey, AuthenticationFailedCause.Error(message))
        }
    }

    /**
     * A configuration for the [jwt] authentication provider.
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal var authenticationFunction: AuthenticationFunction<JWTCredential> = {
            throw NotImplementedError(
                "JWT auth validate function is not specified. Use jwt { validate { ... } } to fix."
            )
        }

        internal var schemes = JWTAuthSchemes("Bearer")

        internal var authHeader: (ApplicationCall) -> HttpAuthHeader? =
            { call -> call.request.parseAuthorizationHeaderOrNull() }

        internal var verifier: ((HttpAuthHeader) -> JWTVerifier?) = { null }

        internal var challenge: JWTAuthChallengeFunction = { scheme, realm ->
            call.respond(
                UnauthorizedResponse(
                    HttpAuthHeader.Parameterized(
                        scheme,
                        mapOf(HttpAuthHeader.Parameters.Realm to realm)
                    )
                )
            )
        }

        /**
         * Specifies a JWT realm to be passed in `WWW-Authenticate` header.
         */
        public var realm: String = "Ktor Server"

        /**
         * Retrieves an HTTP authentication header.
         * By default, it parses the `Authorization` header content.
         */
        public fun authHeader(block: (ApplicationCall) -> HttpAuthHeader?) {
            authHeader = block
        }

        /**
         * @param [defaultScheme] default scheme used to challenge the client when no valid authentication is provided
         * @param [additionalSchemes] additional schemes that are accepted when validating the authentication
         */
        public fun authSchemes(defaultScheme: String = "Bearer", vararg additionalSchemes: String) {
            schemes = JWTAuthSchemes(defaultScheme, *additionalSchemes)
        }

        /**
         * Provides a [JWTVerifier] used to verify a token format and signature.
         * @param [verifier] verifies token format and signature
         */
        public fun verifier(verifier: JWTVerifier) {
            this.verifier = { verifier }
        }

        /**
         * Provides a [JWTVerifier] used to verify a token format and signature.
         */
        public fun verifier(verifier: (HttpAuthHeader) -> JWTVerifier?) {
            this.verifier = verifier
        }

        /**
         * Provides a [JWTVerifier] used to verify a token format and signature.
         * @param [jwkProvider] provides the JSON Web Key
         * @param [issuer] the issuer of the JSON Web Token
         * @param [configure] function is applied during [JWTVerifier] construction
         */
        public fun verifier(jwkProvider: JwkProvider, issuer: String, configure: JWTConfigureFunction = {}) {
            verifier = { token -> getVerifier(jwkProvider, issuer, token, schemes, configure) }
        }

        /**
         * Provides a [JWTVerifier] used to verify a token format and signature.
         * @param [jwkProvider] provides the JSON Web Key
         * @param [configure] function will be applied during [JWTVerifier] construction
         */
        public fun verifier(jwkProvider: JwkProvider, configure: JWTConfigureFunction = {}) {
            verifier = { token -> getVerifier(jwkProvider, token, schemes, configure) }
        }

        /**
         * Provides a [JWTVerifier] used to verify a token format and signature.
         *
         * @param [issuer] of the JSON Web Token
         * @param [audience] restriction
         * @param [algorithm] for validations of token signatures
         */
        public fun verifier(
            issuer: String,
            audience: String,
            algorithm: Algorithm,
            block: Verification.() -> Unit = {}
        ) {
            val verification: Verification = JWT
                .require(algorithm)
                .withAudience(audience)
                .withIssuer(issuer)

            verification.apply(block)
            verifier(verification.build())
        }

        /**
         * Provides a [JWTVerifier] used to verify a token format and signature.
         *
         * @param [issuer] the issuer of JSON Web Token
         * @param [block] configuration of [JwkProvider]
         */
        public fun verifier(issuer: String, block: JWTConfigureFunction = {}) {
            val provider = JwkProviderBuilder(issuer).build()
            verifier = { token -> getVerifier(provider, token, schemes, block) }
        }

        /**
         * Allows you to perform additional validations on the JWT payload.
         * @return a principal (usually an instance of [JWTPrincipal]) or `null`
         */
        public fun validate(validate: suspend ApplicationCall.(JWTCredential) -> Any?) {
            authenticationFunction = validate
        }

        /**
         * Specifies what to send back if JWT authentication fails.
         */
        public fun challenge(block: JWTAuthChallengeFunction) {
            challenge = block
        }

        internal fun build() = JWTAuthenticationProvider(this)
    }
}

/**
 * Installs the JWT [Authentication] provider.
 * JWT (JSON Web Token) is an open standard that defines a way for
 * securely transmitting information between parties as a JSON object.
 * To learn how to configure it, see [JSON Web Tokens](https://ktor.io/docs/jwt.html).
 */
public fun AuthenticationConfig.jwt(
    name: String? = null,
    configure: JWTAuthenticationProvider.Config.() -> Unit
) {
    val provider = JWTAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

/**
 * A context for [JWTAuthChallengeFunction].
 */
public class JWTChallengeContext(
    public val call: ApplicationCall
)

/**
 * Specifies what to send back if JWT authentication fails.
 */
public typealias JWTAuthChallengeFunction =
    suspend JWTChallengeContext.(defaultScheme: String, realm: String) -> Unit
