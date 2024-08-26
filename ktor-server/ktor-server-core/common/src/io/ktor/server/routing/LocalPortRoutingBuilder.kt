/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*

/**
 * Creates a route to match a port on which a call was received.
 *
 * The selector checks the [io.ktor.server.request.ApplicationRequest.local] request port,
 * _ignoring_ HTTP headers such as `Host` or `X-Forwarded-Host`.
 * This is useful for securing routes under separate ports.
 *
 * For multi-tenant applications, you may want to use [io.ktor.server.routing.port],
 * which takes HTTP headers into consideration.
 *
 * @param port the port to match against
 *
 * @throws IllegalArgumentException if the port is outside the range of TCP/UDP ports
 */
public fun Route.localPort(port: Int, build: Route.() -> Unit): Route {
    require(port in 1..65535) { "Port $port must be a positive number between 1 and 65,535" }

    val selector = LocalPortRouteSelector(port)
    return createChild(selector).apply(build)
}

/**
 * Evaluates a route against the port on which a call is received.
 *
 * @param port the port to match against
 */
public data class LocalPortRouteSelector(val port: Int) : RouteSelector() {

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        if (context.call.request.local.localPort == port) {
            val parameters = parametersOf(LocalPortParameter, port.toString())
            RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityConstant, parameters)
        } else {
            RouteSelectorEvaluation.Failed
        }

    public companion object {
        /**
         * A parameter name for [ApplicationCall.parameters] for a request host.
         */
        public const val LocalPortParameter: String = "\$LocalPort"
    }
}
