/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * A Bearer [Authentication] provider.
 *
 * @see [bearer]
 */
public class BearerAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    private val realm = config.realm
    private val defaultScheme = config.defaultScheme
    private val schemesLowerCase =
        config.additionalSchemes.map { it.lowercase() }.toSet() + config.defaultScheme.lowercase()
    private val authenticate = config.authenticate
    private val getAuthHeader = config.getAuthHeader

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val authHeader = getAuthHeader(context.call) ?: let {
            context.challenge(challengeKey, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(defaultScheme, realm)))
                challenge.complete()
            }
            return
        }

        val principal = (authHeader as? HttpAuthHeader.Single)
            ?.takeIf { it.authScheme.lowercase() in schemesLowerCase }
            ?.let { authenticate(context.call, BearerTokenCredential(it.blob)) }
            ?: let {
                context.challenge(challengeKey, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(defaultScheme, realm)))
                    challenge.complete()
                }
                return
            }

        context.principal(principal)
    }

    /**
     * A configuration for the [bearer] authentication provider.
     */
    public class Config(name: String?) : AuthenticationProvider.Config(name) {
        internal var authenticate: AuthenticationFunction<BearerTokenCredential> = {
            throw NotImplementedError(
                "Bearer auth authenticate function is not specified. Use bearer { authenticate { ... } } to fix."
            )
        }

        internal var getAuthHeader: (ApplicationCall) -> HttpAuthHeader? = { call ->
            call.request.parseAuthorizationHeader()
        }

        internal var defaultScheme = AuthScheme.Bearer
        internal var additionalSchemes = emptySet<String>()

        /**
         * Specifies an options Bearer realm to be passed in `WWW-Authenticate` header.
         */
        public var realm: String? = null

        /**
         * Exchanges the token for a Principal.
         * @return a principal or `null`
         */
        public fun authenticate(authenticate: suspend ApplicationCall.(BearerTokenCredential) -> Any?) {
            this.authenticate = authenticate
        }

        /**
         * Retrieves an HTTP authentication header.
         * By default, it parses the `Authorization` header content.
         */
        public fun authHeader(getAuthHeader: (ApplicationCall) -> HttpAuthHeader?) {
            this.getAuthHeader = getAuthHeader
        }

        /**
         * Provide the auth schemes accepted when validating the authentication.
         * By default, it accepts the "Bearer" scheme.
         */
        public fun authSchemes(defaultScheme: String = AuthScheme.Bearer, vararg additionalSchemes: String) {
            this.defaultScheme = defaultScheme
            this.additionalSchemes = additionalSchemes.toSet()
        }

        internal fun build() = BearerAuthenticationProvider(this)
    }
}

/**
 * Installs the Bearer [Authentication] provider.
 * Bearer auth requires the developer to provide a custom 'authenticate' function to authorize the token,
 * and return the associated principal.
 */
public fun AuthenticationConfig.bearer(
    name: String? = null,
    configure: BearerAuthenticationProvider.Config.() -> Unit,
) {
    val provider = BearerAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

private val challengeKey: Any = "BearerAuth"
