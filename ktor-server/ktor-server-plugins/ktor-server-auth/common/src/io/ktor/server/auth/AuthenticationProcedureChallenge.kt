/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.auth

import io.ktor.server.application.*
import kotlinx.atomicfu.*

public typealias ChallengeFunction = suspend (AuthenticationProcedureChallenge, ApplicationCall) -> Unit

/**
 * Represents an authentication challenging procedure requested by authentication mechanism.
 */
public class AuthenticationProcedureChallenge {
    internal val register = mutableListOf<Pair<AuthenticationFailedCause, ChallengeFunction>>()

    /**
     * List of currently installed challenges except errors.
     */
    internal val challenges: List<ChallengeFunction>
        get() = register.filter { it.first !is AuthenticationFailedCause.Error }.sortedBy {
            when (it.first) {
                AuthenticationFailedCause.InvalidCredentials -> 1
                AuthenticationFailedCause.NoCredentials -> 2
                else -> throw IllegalArgumentException("Unknown Auth fail: ${it.first}")
            }
        }.map { it.second }

    /**
     * List of currently installed challenges for errors.
     */
    internal val errorChallenges: List<ChallengeFunction>
        get() = register.filter { it.first is AuthenticationFailedCause.Error }.map { it.second }

    private val _completed = atomic(false)

    /**
     * Represents whether a challenge is successfully sent to the client and challenging should be stopped.
     */
    public var completed: Boolean
        get() = _completed.value
        private set(value) {
            _completed.value = value
        }

    /**
     * Completes a challenging procedure.
     */
    public fun complete() {
        completed = true
    }

    override fun toString(): String = "AuthenticationProcedureChallenge"
}
