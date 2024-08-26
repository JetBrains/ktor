/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlin.reflect.*

/**
 * A session-based [Authentication] provider.
 * @see [session]
 *
 * @property type of session
 * @property challengeFunction to be used if there is no session
 * @property validator applied to an application all and session providing a [Principal]
 */
public class SessionAuthenticationProvider<T : Any> private constructor(
    config: Config<T>
) : AuthenticationProvider(config) {
    public val type: KClass<T> = config.type

    private val challengeFunction: SessionAuthChallengeFunction<T> = config.challengeFunction

    private val validator: AuthenticationFunction<T> = config.validator

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val session = call.sessions.get(type)
        val principal = session?.let { validator(call, it) }

        if (principal != null) {
            context.principal(name, principal)
        } else {
            val cause =
                if (session == null) {
                    AuthenticationFailedCause.NoCredentials
                } else AuthenticationFailedCause.InvalidCredentials

            @Suppress("NAME_SHADOWING")
            context.challenge(SessionAuthChallengeKey, cause) { challenge, call ->
                challengeFunction(SessionChallengeContext(call), principal)
                if (!challenge.completed && call.response.status() != null) {
                    challenge.complete()
                }
            }
        }
    }

    /**
     * A configuration for the [session] authentication provider.
     */
    public class Config<T : Any> @PublishedApi internal constructor(
        name: String?,
        internal val type: KClass<T>
    ) : AuthenticationProvider.Config(name) {
        internal var validator: AuthenticationFunction<T> = UninitializedValidator

        internal var challengeFunction: SessionAuthChallengeFunction<T> = { call.respond(UnauthorizedResponse()) }

        /**
         * Specifies a response to send back if authentication failed.
         */
        public fun challenge(block: SessionAuthChallengeFunction<T>) {
            challengeFunction = block
        }

        /**
         * Specifies a response to send back if authentication failed.
         */
        public fun challenge(redirectUrl: String) {
            challenge {
                call.respondRedirect(redirectUrl)
            }
        }

        /**
         * Specifies a response to send back if authentication failed.
         */
        public fun challenge(redirect: Url) {
            challenge(redirect.toString())
        }

        /**
         * Sets a validation function that checks a given [T] session instance and returns principal [Any],
         * or null if the session does not correspond to an authenticated principal.
         */
        public fun validate(block: suspend ApplicationCall.(T) -> Any?) {
            check(validator === UninitializedValidator) { "Only one validator could be registered" }
            validator = block
        }

        private fun verifyConfiguration() {
            check(validator !== UninitializedValidator) {
                "It should be a validator supplied to a session auth provider"
            }
        }

        @PublishedApi
        internal fun buildProvider(): SessionAuthenticationProvider<T> {
            verifyConfiguration()
            return SessionAuthenticationProvider(this)
        }
    }

    public companion object {
        private val UninitializedValidator: suspend ApplicationCall.(Any) -> Any? = {
            error("It should be a validator supplied to a session auth provider")
        }
    }
}

/**
 * Installs the session [Authentication] provider.
 * This provider provides the ability to authenticate a user that already has an associated session.
 *
 * To learn how to configure the session provider, see [Session authentication](https://ktor.io/docs/session-auth.html).
 */
public inline fun <reified T : Any> AuthenticationConfig.session(
    name: String? = null
) {
    session(name, T::class)
}

/**
 * Installs the session [Authentication] provider.
 * This provider provides the ability to authenticate a user that already has an associated session.
 *
 * To learn how to configure the session provider, see [Session authentication](https://ktor.io/docs/session-auth.html).
 */
public fun <T : Any> AuthenticationConfig.session(
    name: String? = null,
    kClass: KClass<T>
) {
    session(name, kClass) {
        validate { session -> session }
    }
}

/**
 * Installs the session [Authentication] provider.
 * This provider provides the ability to authenticate a user that already has an associated session.
 *
 * To learn how to configure the session provider, see [Session authentication](https://ktor.io/docs/session-auth.html).
 */
public inline fun <reified T : Any> AuthenticationConfig.session(
    name: String? = null,
    noinline configure: SessionAuthenticationProvider.Config<T>.() -> Unit
) {
    session(name, T::class, configure)
}

/**
 * Installs the session [Authentication] provider.
 * This provider provides the ability to authenticate a user that already has an associated session.
 *
 * To learn how to configure the session provider, see [Session authentication](https://ktor.io/docs/session-auth.html).
 */
public fun <T : Any> AuthenticationConfig.session(
    name: String?,
    kClass: KClass<T>,
    configure: SessionAuthenticationProvider.Config<T>.() -> Unit,
) {
    val provider = SessionAuthenticationProvider.Config(name, kClass).apply(configure).buildProvider()
    register(provider)
}

/**
 * A context for [SessionAuthChallengeFunction].
 */
public class SessionChallengeContext(
    public val call: ApplicationCall
)

/**
 * Specifies what to send back if session authentication fails.
 */
public typealias SessionAuthChallengeFunction<T> = suspend SessionChallengeContext.(T?) -> Unit

/**
 * A key used to register authentication challenge.
 */
public const val SessionAuthChallengeKey: String = "SessionAuth"
