/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.server.plugins.forwardedheaders

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.utils.io.*

/**
 * A configuration for the [XForwardedHeaders] plugin.
 */
@KtorDsl
public class XForwardedHeadersConfig {
    /**
     * Gets headers used to identify the original host requested by the client.
     * Default are `X-Forwarded-Server` and `X-Forwarded-Host`.
     */
    public val hostHeaders: ArrayList<String> = arrayListOf(HttpHeaders.XForwardedHost, HttpHeaders.XForwardedServer)

    /**
     * Gets headers used to identify the protocol (HTTP or HTTPS) that a client used
     * to connect to a proxy or load balancer. Default are `X-Forwarded-Proto` and `X-Forwarded-Protocol`.
     *
     */
    public val protoHeaders: MutableList<String> = mutableListOf(HttpHeaders.XForwardedProto, "X-Forwarded-Protocol")

    /**
     * Gets headers used to identify the originating IP address of a client connecting to
     * a server through a proxy or a load balancer.
     */
    public val forHeaders: MutableList<String> = mutableListOf(HttpHeaders.XForwardedFor)

    /**
     * Gets headers used to identify whether HTTPS/TLS is used between
     * the client and the front-end server. Default are `X-Forwarded-SSL` and `Front-End-Https`.
     */
    public val httpsFlagHeaders: MutableList<String> = mutableListOf("X-Forwarded-SSL", "Front-End-Https")

    /**
     * Gets headers used to identify the destination port.
     * The default is `X-Forwarded-Port`.
     */
    public val portHeaders: MutableList<String> = mutableListOf("X-Forwarded-Port")

    internal var xForwardedHeadersHandler: (MutableOriginConnectionPoint, XForwardedHeaderValues) -> Unit = { _, _ -> }

    init {
        useFirstProxy()
    }

    /**
     * Custom logic to extract the value from the X-Forward-* headers when multiple values are present.
     * You need to modify [MutableOriginConnectionPoint] based on headers from [XForwardedHeaderValues].
     */
    public fun extractEdgeProxy(block: (MutableOriginConnectionPoint, XForwardedHeaderValues) -> Unit) {
        xForwardedHeadersHandler = block
    }

    /**
     * Takes the first value from the `X-Forward-*` headers when multiple values are present.
     */
    public fun useFirstProxy() {
        extractEdgeProxy { connectionPoint, headers ->
            setValues(connectionPoint, headers) { it.firstOrNull()?.trim() }
        }
    }

    /**
     * Takes the last value from the `X-Forward-*` headers when multiple values are present.
     */
    public fun useLastProxy() {
        extractEdgeProxy { connectionPoint, headers ->
            setValues(connectionPoint, headers) { it.lastOrNull()?.trim() }
        }
    }

    /**
     * Takes the [proxiesCount]-before-last value from the `X-Forward-*` headers when multiple values are present.
     */
    public fun skipLastProxies(proxiesCount: Int) {
        extractEdgeProxy { connectionPoint, headers ->
            setValues(connectionPoint, headers) { values ->
                values.getOrElse(values.size - proxiesCount - 1) { values.lastOrNull() }?.trim()
            }
        }
    }

    /**
     * Removes known [hosts] from the end of the list and takes the last value
     * from `X-Forward-*` headers when multiple values are present.
     * */
    public fun skipKnownProxies(hosts: List<String>) {
        extractEdgeProxy { connectionPoint, headers ->
            val forValues = headers.forHeader?.split(',')

            var proxiesCount = 0
            while (
                hosts.lastIndex >= proxiesCount &&
                forValues != null && forValues.lastIndex >= proxiesCount &&
                hosts[hosts.size - proxiesCount - 1].trim() == forValues[forValues.size - proxiesCount - 1].trim()
            ) {
                proxiesCount++
            }
            setValues(connectionPoint, headers) { values ->
                values.getOrElse(values.size - proxiesCount - 1) { values.lastOrNull() }?.trim()
            }
        }
    }

    private fun setValues(
        connectionPoint: MutableOriginConnectionPoint,
        headers: XForwardedHeaderValues,
        extractValue: (List<String>) -> String?
    ) {
        val protoValues = headers.protoHeader?.split(',')
        val httpsFlagValues = headers.httpsFlagHeader?.split(',')
        val hostValues = headers.hostHeader?.split(',')
        val portValues = headers.portHeader?.split(',')
        val forValues = headers.forHeader?.split(',')

        protoValues?.let { values ->
            val scheme = extractValue(values) ?: return@let
            connectionPoint.scheme = scheme
            URLProtocol.byName[scheme]?.let {
                connectionPoint.port = it.defaultPort
                connectionPoint.serverPort = it.defaultPort
            }
        }

        httpsFlagValues?.let { values ->
            val useHttps = extractValue(values).toBoolean()
            if (!useHttps) return@let

            connectionPoint.let { route ->
                route.scheme = "https"
                route.port = URLProtocol.HTTPS.defaultPort
                route.serverPort = URLProtocol.HTTPS.defaultPort
            }
        }

        hostValues?.let { values ->
            val hostAndPort = extractValue(values) ?: return@let
            val host = hostAndPort.substringBefore(':')
            val port = hostAndPort.substringAfter(':', "")

            connectionPoint.host = host
            connectionPoint.serverHost = host
            port.toIntOrNull()?.let {
                connectionPoint.port = it
                connectionPoint.serverPort = it
            } ?: URLProtocol.byName[connectionPoint.scheme]?.let {
                connectionPoint.port = it.defaultPort
                connectionPoint.serverPort = it.defaultPort
            }
        }

        portValues?.let { values ->
            val port = extractValue(values) ?: return@let
            connectionPoint.port = port.toInt()
            connectionPoint.serverPort = port.toInt()
        }

        forValues?.let { values ->
            val remoteHostOrAddress = extractValue(values) ?: return@let
            if (remoteHostOrAddress.isNotBlank()) {
                connectionPoint.remoteHost = remoteHostOrAddress
                if (remoteHostOrAddress.isNotHostAddress()) {
                    connectionPoint.remoteAddress = remoteHostOrAddress
                }
            }
        }
    }

    private fun String?.toBoolean() = this == "yes" || this == "true" || this == "on"
}

/**
 * Values of the `X-Forward-*` headers. Each property may contain multiple comma-separated values.
 */
public data class XForwardedHeaderValues(
    /**
     * A comma-separated list of values for the [XForwardedHeadersConfig.protoHeaders] header.
     */
    public val protoHeader: String?,
    /**
     * A comma-separated list of values for the [XForwardedHeadersConfig.forHeaders] header.
     */
    public val forHeader: String?,
    /**
     * A comma-separated list of values for the [XForwardedHeadersConfig.hostHeaders] header.
     */
    public val hostHeader: String?,
    /**
     * A comma-separated list of values for the [XForwardedHeadersConfig.httpsFlagHeaders] header.
     */
    public val httpsFlagHeader: String?,
    /**
     * A comma-separated list of values for the [XForwardedHeadersConfig.portHeaders] header.
     */
    public val portHeader: String?
)

/**
 * A plugin that allows you to handle reverse proxy headers to get information
 * about the original request when a Ktor server is placed behind a reverse proxy.
 *
 * To learn how to install and use [XForwardedHeaders], see
 * [Forwarded headers](https://ktor.io/docs/forward-headers.html).
 */
public val XForwardedHeaders: ApplicationPlugin<XForwardedHeadersConfig> = createApplicationPlugin(
    "XForwardedHeaders",
    ::XForwardedHeadersConfig
) {
    on(CallSetup) { call ->
        val strategy = pluginConfig.xForwardedHeadersHandler
        val headers = XForwardedHeaderValues(
            protoHeader = pluginConfig.protoHeaders.firstNotNullOfOrNull { call.request.headers[it] },
            forHeader = pluginConfig.forHeaders.firstNotNullOfOrNull { call.request.headers[it] },
            hostHeader = pluginConfig.hostHeaders.firstNotNullOfOrNull { call.request.headers[it] },
            httpsFlagHeader = pluginConfig.httpsFlagHeaders.firstNotNullOfOrNull { call.request.headers[it] },
            portHeader = pluginConfig.portHeaders.firstNotNullOfOrNull { call.request.headers[it] },
        )

        strategy.invoke(call.mutableOriginConnectionPoint, headers)
    }
}
