/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*

/**
 * Creates a route to match a request's host and port.
 * There are no any host resolutions/transformations applied to a host: a request host is treated as a string.
 *
 * When passes, it puts a request host and port into
 * call parameters by the [HostRouteSelector.HostNameParameter] and [HostRouteSelector.PortParameter] keys.
 *
 * @param host exact host name that is treated literally
 * @param port to be tested or `0` to pass all ports
 */
public fun Route.host(host: String, port: Int = 0, build: Route.() -> Unit): Route {
    return host(listOf(host), emptyList(), if (port > 0) listOf(port) else emptyList(), build)
}

/**
 * Creates a route to match a request host and port.
 * There are no any host resolutions/transformations applied to a host: a request host is treated as a string.
 *
 * When passes, it puts a request host and port into
 * call parameters by the [HostRouteSelector.HostNameParameter] and [HostRouteSelector.PortParameter] keys.
 *
 * @param hostPattern is a  regular expression to match request host
 * @param port to be tested or `0` to pass all ports
 */
public fun Route.host(hostPattern: Regex, port: Int = 0, build: Route.() -> Unit): Route {
    return host(emptyList(), listOf(hostPattern), if (port > 0) listOf(port) else emptyList(), build)
}

/**
 * Creates a route to match a request host and port.
 * There are no any host resolutions/transformations applied to a host: a request host is treated as a string.
 *
 * When passes, it puts request host and port into
 * call parameters by the [HostRouteSelector.HostNameParameter] and [HostRouteSelector.PortParameter] keys.
 *
 * @param hosts a list of exact host names that are treated literally
 * @param ports a list of ports to be passed or empty to pass all ports
 *
 * @throws IllegalArgumentException when no constraints were applied in [hosts] and [ports]
 */
public fun Route.host(
    hosts: List<String>,
    ports: List<Int> = emptyList(),
    build: Route.() -> Unit
): Route {
    return host(hosts, emptyList(), ports, build)
}

/**
 * Creates a route to match s request host and port.
 * There are no any host resolutions/transformations applied to a host: a request host is treated as a string.
 *
 * When passes, it puts request host and port into
 * call parameters by the [HostRouteSelector.HostNameParameter] and [HostRouteSelector.PortParameter] keys.
 *
 * @param hosts a list of exact host names that are treated literally
 * @param hostPatterns a list of regular expressions to match request host
 * @param ports a list of ports to be passed or empty to pass all ports
 *
 * @throws IllegalArgumentException when no constraints were applied in [host], [hostPatterns] and [ports]
 */
public fun Route.host(
    hosts: List<String>,
    hostPatterns: List<Regex>,
    ports: List<Int> = emptyList(),
    build: Route.() -> Unit
): Route {
    val selector = HostRouteSelector(hosts, hostPatterns, ports)
    return createChild(selector).apply(build)
}

/**
 * Creates a route to match a request port.
 *
 * When passes, it puts a request host and port into
 * call parameters by the [HostRouteSelector.HostNameParameter] and [HostRouteSelector.PortParameter] keys.
 *
 * @param ports a list of ports to be passed
 *
 * @throws IllegalArgumentException if no ports were specified
 */
public fun Route.port(vararg ports: Int, build: Route.() -> Unit): Route {
    require(ports.isNotEmpty()) { "At least one port need to be specified" }

    val selector = HostRouteSelector(emptyList(), emptyList(), ports.toList())
    return createChild(selector).apply(build)
}

/**
 * Evaluates a route against a request's host and port.
 * @param hostList contains exact host names
 * @param hostPatterns contains host patterns to match
 * @param portsList contains possible ports or empty to match all ports
 */
public data class HostRouteSelector(
    val hostList: List<String>,
    val hostPatterns: List<Regex>,
    val portsList: List<Int>
) : RouteSelector() {
    init {
        require(hostList.isNotEmpty() || hostPatterns.isNotEmpty() || portsList.isNotEmpty())
    }

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val requestHost = context.call.request.origin.serverHost
        val requestPort = context.call.request.origin.serverPort

        if (hostList.isNotEmpty() || hostPatterns.isNotEmpty()) {
            val matches1 = requestHost in hostList
            val matches2 = if (!matches1) hostPatterns.any { it.matches(requestHost) } else false

            if (!matches1 && !matches2) {
                return RouteSelectorEvaluation.Failed
            }
        }

        if (portsList.isNotEmpty()) {
            if (requestPort !in portsList) return RouteSelectorEvaluation.Failed
        }

        val params = Parameters.build {
            append(HostNameParameter, requestHost)
            append(PortParameter, requestPort.toString())
        }

        return RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityConstant, params)
    }

    override fun toString(): String = "($hostList, $hostPatterns, $portsList)"

    public companion object {
        /**
         * A parameter name for [ApplicationCall.parameters] for a request host.
         */
        public const val HostNameParameter: String = "\$RequestHost"

        /**
         * A parameter name for [ApplicationCall.parameters] for a request port.
         */
        public const val PortParameter: String = "\$RequestPort"
    }
}
