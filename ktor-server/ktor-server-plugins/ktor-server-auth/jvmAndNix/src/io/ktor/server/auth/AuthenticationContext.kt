/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * An authentication context for a call.
 * @param call instance of [ApplicationCall] this context is for.
 */
public class AuthenticationContext(call: ApplicationCall) {

    public var call: ApplicationCall = call
        private set

    private val _errors = HashMap<Any, AuthenticationFailedCause>()

    internal val _principal: CombinedPrincipal = CombinedPrincipal()

    /**
     * Retrieves an authenticated principal, or returns `null` if a user isn't authenticated.
     */
    @Deprecated("Use accessor methods instead", level = DeprecationLevel.ERROR)
    public var principal: Principal?
        get() = _principal.principals.firstOrNull()?.second
        set(value) {
            check(value != null)
            _principal.add(null, value)
        }

    /**
     * All registered errors during auth procedure (only [AuthenticationFailedCause.Error]).
     */
    public val allErrors: List<AuthenticationFailedCause.Error>
        get() = _errors.values.filterIsInstance<AuthenticationFailedCause.Error>()

    /**
     * All authentication failures during auth procedure including missing or invalid credentials.
     */
    public val allFailures: List<AuthenticationFailedCause>
        get() = _errors.values.toList()

    /**
     * Appends an error to the errors list. Overwrites if already registered for the same [key].
     */
    public fun error(key: Any, cause: AuthenticationFailedCause) {
        _errors[key] = cause
    }

    /**
     * Gets an [AuthenticationProcedureChallenge] for this context.
     */
    public val challenge: AuthenticationProcedureChallenge = AuthenticationProcedureChallenge()

    /**
     * Sets an authenticated principal for this context.
     */
    public fun principal(principal: Principal) {
        _principal.add(null, principal)
    }

    /**
     * Sets an authenticated principal for this context from provider with name [provider].
     */
    public fun principal(provider: String? = null, principal: Principal) {
        _principal.add(provider, principal)
    }

    /**
     * Retrieves a principal of the type [T] from provider with name [provider], if any.
     */
    public inline fun <reified T : Principal> principal(provider: String? = null): T? {
        return principal(provider, T::class)
    }

    /**
     * Retrieves a principal of the type [T], if any.
     */
    public fun <T : Principal> principal(provider: String?, klass: KClass<T>): T? {
        return _principal.get(provider, klass)
    }

    /**
     * Requests a challenge to be sent to the client if none of mechanisms can authenticate a user.
     */
    public fun challenge(
        key: Any,
        cause: AuthenticationFailedCause,
        function: ChallengeFunction
    ) {
        error(key, cause)
        challenge.register.add(cause to function)
    }

    public companion object {
        private val AttributeKey = AttributeKey<AuthenticationContext>("AuthContext")

        internal fun from(call: ApplicationCall): AuthenticationContext {
            val existingContext = call.attributes.getOrNull(AttributeKey)
            if (existingContext != null) {
                existingContext.call = call
                return existingContext
            }
            val context = AuthenticationContext(call)
            call.attributes.put(AttributeKey, context)
            return context
        }
    }
}
