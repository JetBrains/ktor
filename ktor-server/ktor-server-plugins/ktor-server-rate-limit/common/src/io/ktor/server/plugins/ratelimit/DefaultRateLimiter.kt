/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultRateLimiter(
    private val limit: Int,
    private val refillPeriod: Duration,
    private val clock: () -> Long = ::getTimeMillis,
) : RateLimiter {

    private val tokens = atomic(limit)
    private var lastRefillTimeMillis = clock()

    override suspend fun tryConsume(tokens: Int): RateLimiter.State {
        while (true) {
            refillIfNeeded()
            val current = this.tokens.value
            val new = current - tokens

            if (new < 0) return RateLimiter.State.Exhausted(timeToWaitMillis().milliseconds)
            if (this.tokens.compareAndSet(current, new)) {
                return RateLimiter.State.Available(new, limit, lastRefillTimeMillis + refillPeriod.inWholeMilliseconds)
            }
        }
    }

    private fun refillIfNeeded() {
        if (timeToWaitMillis() > 0) return
        tokens.value = limit
        lastRefillTimeMillis = clock()
    }

    private fun timeToWaitMillis() = refillPeriod.inWholeMilliseconds - (clock() - lastRefillTimeMillis)
}
