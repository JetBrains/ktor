/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.auth

import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*

/**
 * A `basic` [Authentication] provider.
 *
 * @see [basic]
 * @property name is the name of the provider, or `null` for a default provider.
 */
public class BasicAuthenticationProvider internal constructor(
    config: Config
) : AuthenticationProvider(config) {
    internal val realm: String = config.realm

    internal val charset: Charset? = config.charset

    internal val authenticationFunction = config.authenticationFunction

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val credentials = call.request.basicAuthenticationCredentials(charset)
        val principal = credentials?.let { authenticationFunction(call, it) }

        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            @Suppress("NAME_SHADOWING")
            context.challenge(basicAuthenticationChallengeKey, cause) { challenge, call ->
                call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge(realm, charset)))
                challenge.complete()
            }
        }
        if (principal != null) {
            context.principal(name, principal)
        }
    }

    /**
     * A configuration for the [basic] authentication provider.
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal var authenticationFunction: AuthenticationFunction<UserPasswordCredential> = {
            throw NotImplementedError(
                "Basic auth validate function is not specified. Use basic { validate { ... } } to fix."
            )
        }

        /**
         * Specifies a realm to be passed in the `WWW-Authenticate` header.
         */
        public var realm: String = "Ktor Server"

        /**
         * Specifies the charset to be used. It can be either `UTF_8` or `null`.
         * Setting `null` turns on a legacy mode (`ISO-8859-1`).
         */
        public var charset: Charset? = Charsets.UTF_8
            set(value) {
                if (value != null && value != Charsets.UTF_8) {
                    // https://tools.ietf.org/html/rfc7617#section-2.1
                    // 'The only allowed value is "UTF-8"; it is to be matched case-insensitively'
                    throw IllegalArgumentException("Basic Authentication charset can be either UTF-8 or null")
                }
                field = value
            }

        /**
         * Sets a validation function that checks a specified [UserPasswordCredential] instance and
         * returns principal [Any] in a case of successful authentication or null if authentication fails.
         */
        public fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> Any?) {
            authenticationFunction = body
        }
    }
}

/**
 * Installs the basic [Authentication] provider.
 * You can use basic authentication for logging in users and protecting specific routes.
 * To learn how to configure it, see [Basic authentication](https://ktor.io/docs/basic.html).
 */
public fun AuthenticationConfig.basic(
    name: String? = null,
    configure: BasicAuthenticationProvider.Config.() -> Unit
) {
    val provider = BasicAuthenticationProvider(BasicAuthenticationProvider.Config(name).apply(configure))
    register(provider)
}

/**
 * Retrieves [basic] authentication credentials for this [ApplicationRequest].
 */
public fun ApplicationRequest.basicAuthenticationCredentials(charset: Charset? = null): UserPasswordCredential? {
    when (val authHeader = parseAuthorizationHeader()) {
        is HttpAuthHeader.Single -> {
            // Verify the auth scheme is HTTP Basic. According to RFC 2617, the authorization scheme should not be
            // case-sensitive; thus BASIC, or Basic, or basic are all valid.
            if (!authHeader.authScheme.equals("Basic", ignoreCase = true)) return null

            val userPass = try {
                val bytes = authHeader.blob.decodeBase64Bytes()
                bytes.decodeToString(0, 0 + bytes.size)
            } catch (e: Throwable) {
                return null
            }

            val colonIndex = userPass.indexOf(':')

            if (colonIndex == -1) return null

            return UserPasswordCredential(userPass.substring(0, colonIndex), userPass.substring(colonIndex + 1))
        }
        else -> return null
    }
}

private val basicAuthenticationChallengeKey: Any = "BasicAuth"
