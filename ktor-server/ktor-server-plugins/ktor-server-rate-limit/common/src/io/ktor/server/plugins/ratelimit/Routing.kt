/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.server.routing.*
import io.ktor.util.*

/**
 * Creates a route with Rate-Limit rules applied to it.
 * This function accepts name of RateLimit providers defined in the [RateLimit] plugin configuration.
 * @see [RateLimit]
 *
 * @param configuration names of RateLimit providers defined in the [RateLimit] plugin configuration.
 * @throws IllegalArgumentException if there are no registered providers referred by [configuration] names.
 */
public fun Route.rateLimit(
    configuration: RateLimitName = LIMITER_NAME_EMPTY,
    build: Route.() -> Unit
): Route {
    val rateLimitRoute = createChild(RateLimitRouteSelector(configuration))
    rateLimitRoute.attributes.put(RateLimitProviderNameKey, configuration)
    val allConfigurations = generateSequence(rateLimitRoute) { it.parent }
        .toList()
        .reversed()
        .mapNotNull { it.attributes.getOrNull(RateLimitProviderNameKey) }
        .distinct()

    rateLimitRoute.install(RateLimitInterceptors) {
        this.providerNames = allConfigurations
    }
    rateLimitRoute.build()
    return rateLimitRoute
}

/**
 * A rate-limited route node that is used by the [RateLimit] plugin
 * and usually created by the [Route.rateLimit] DSL function, so generally there is no need to instantiate it directly
 * unless you are writing an extension.
 * @param name of rate-limit provider to be applied to this route.
 */
public class RateLimitRouteSelector(public val name: RateLimitName) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String = "(RateLimit ${name.name})"
}

private val RateLimitProviderNameKey = AttributeKey<RateLimitName>("RateLimitProviderNameKey")
