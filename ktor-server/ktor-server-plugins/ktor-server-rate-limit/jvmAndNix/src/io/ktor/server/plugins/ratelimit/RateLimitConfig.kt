/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.jvm.*
import kotlin.time.*

/**
 * A config for the [RateLimit] plugin.
 */
@KtorDsl
public class RateLimitConfig {

    internal val providers: MutableMap<RateLimitName, RateLimitProvider> = mutableMapOf()
    internal var global: RateLimitProvider? = null

    /**
     * Registers the Rate-Limit provider that can be used in sub-routes via the [Route.rateLimit] function.
     */
    public fun register(name: RateLimitName = LIMITER_NAME_EMPTY, block: RateLimitProviderConfig.() -> Unit) {
        if (providers.containsKey(name)) {
            throw IllegalStateException("Rate limit provider with name $name is already configured")
        }
        providers[name] = RateLimitProvider(
            RateLimitProviderConfig(name).apply {
                block()
            }
        )
    }

    /**
     * Registers the Rate-Limit provider that is applied to a whole application.
     */
    public fun global(block: RateLimitProviderConfig.() -> Unit) {
        global = RateLimitProvider(
            RateLimitProviderConfig(LIMITER_NAME_GLOBAL).apply {
                block()
            }
        )
    }
}

/**
 * A name of registered [RateLimit] provider.
 */
@JvmInline
public value class RateLimitName(internal val name: String)

/**
 * A config for [RateLimit] provider inside [RateLimiterConfig].
 */
@KtorDsl
public class RateLimitProviderConfig(internal val name: RateLimitName) {

    internal var requestKey: suspend (ApplicationCall) -> Any = { }
    internal var requestWeight: suspend (ApplicationCall, key: Any) -> Int = { _, _ -> 1 }

    internal var modifyResponse: (ApplicationCall, RateLimiter.State) -> Unit = { call, state ->
        when (state) {
            is RateLimiter.State.Available -> {
                call.response.headers.appendIfAbsent("X-RateLimit-Limit", state.limit.toString())
                call.response.headers.appendIfAbsent("X-RateLimit-Remaining", state.remainingTokens.toString())
                call.response.headers.appendIfAbsent("X-RateLimit-Reset", (state.refillAtTimeMillis / 1000).toString())
            }

            is RateLimiter.State.Exhausted -> {
                if (!call.response.headers.contains(HttpHeaders.RetryAfter)) {
                    call.response.header(HttpHeaders.RetryAfter, state.toWait.inWholeSeconds.toString())
                }
            }
        }
    }

    internal var rateLimiterProvider: ((call: ApplicationCall, key: Any) -> RateLimiter)? = null

    /**
     * Sets [RateLimit] for this provider based on [ApplicationCall] and `key` of this request.
     */
    public fun rateLimiter(provider: (call: ApplicationCall, key: Any) -> RateLimiter) {
        rateLimiterProvider = provider
    }

    /**
     * Sets [RateLimit] for this provider
     */
    public fun rateLimiter(
        limit: Int,
        refillPeriod: Duration,
        initialSize: Int = limit,
        clock: () -> Long = { getTimeMillis() }
    ) {
        rateLimiter { _: ApplicationCall, _: Any ->
            RateLimiter.default(limit = limit, refillPeriod = refillPeriod, initialSize = initialSize, clock = clock)
        }
    }

    /**
     * Sets a function that returns a key for a request. Requests with different keys will have independent Rate-Limits.
     * Keys should have good equals and hashCode implementations.
     * By default, the key is a [Unit], so all requests share the same Rate-Limit.
     */
    public fun requestKey(block: suspend (ApplicationCall) -> Any) {
        requestKey = block
    }

    /**
     * Sets a function that returns a weight of a request.
     * Weight is used to calculate how many tokens are consumed by a request.
     * By default, weight is always 1.
     */
    public fun requestWeight(block: suspend (ApplicationCall, key: Any) -> Int) {
        requestWeight = block
    }

    /**
     * Sets a function that modifies response headers.
     * By default, these headers will be added:
     *
     * if request is allowed:
     *
     *    `X-RateLimit-Limit` limit setting
     *
     *    `X-RateLimit-Remaining` amount of tokens left
     *
     *    `X-RateLimit-Reset` UTC timestamp for next limit reset time in seconds
     *
     * if request is declined:
     *
     *    `Retry-After` time to wait for next limit reset time in seconds
     */
    public fun modifyResponse(block: (ApplicationCall, RateLimiter.State) -> Unit) {
        modifyResponse = block
    }
}
