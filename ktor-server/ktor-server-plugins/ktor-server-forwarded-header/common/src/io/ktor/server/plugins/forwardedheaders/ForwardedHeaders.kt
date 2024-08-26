/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.server.plugins.forwardedheaders

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * A key for the application call attribute that is used to cache parsed header values.
 */
public val FORWARDED_PARSED_KEY: AttributeKey<List<ForwardedHeaderValue>> =
    AttributeKey("ForwardedParsedKey")

/**
 * A configuration for the [ForwardedHeaders] plugin.
 */
@KtorDsl
public class ForwardedHeadersConfig {
    internal var forwardedHeadersHandler: (MutableOriginConnectionPoint, List<ForwardedHeaderValue>) -> Unit =
        { _, _ -> }

    init {
        useFirstValue()
    }

    /**
     * Custom logic to extract the value from the Forward headers when multiple values are present.
     * You need to modify [MutableOriginConnectionPoint] based on headers from [ForwardedHeaderValue].
     */
    public fun extractValue(block: (MutableOriginConnectionPoint, List<ForwardedHeaderValue>) -> Unit) {
        forwardedHeadersHandler = block
    }

    /**
     * Takes the first value from the Forward header when multiple values are present.
     */
    public fun useFirstValue() {
        extractValue { connectionPoint, headers ->
            setValues(connectionPoint, headers.firstOrNull())
        }
    }

    /**
     * Takes the last value from Forward header when multiple values are present.
     */
    public fun useLastValue() {
        extractValue { connectionPoint, headers ->
            setValues(connectionPoint, headers.lastOrNull())
        }
    }

    /**
     * Takes [proxiesCount] before the last value from Forward header when multiple values are present.
     */
    public fun skipLastProxies(proxiesCount: Int) {
        extractValue { connectionPoint, headers ->
            setValues(connectionPoint, headers.getOrElse(headers.size - proxiesCount - 1) { headers.last() })
        }
    }

    /**
     * Removes known [hosts] from the end of the list and takes the last value
     * from Forward headers when multiple values are present.
     */
    public fun skipKnownProxies(hosts: List<String>) {
        extractValue { connectionPoint, headers ->
            val forValues = headers.map { it.forParam }

            var proxiesCount = 0
            while (
                hosts.lastIndex >= proxiesCount &&
                forValues.lastIndex >= proxiesCount &&
                hosts[hosts.size - proxiesCount - 1].trim() == forValues[forValues.size - proxiesCount - 1]?.trim()
            ) {
                proxiesCount++
            }
            setValues(connectionPoint, headers.getOrElse(headers.size - proxiesCount - 1) { headers.last() })
        }
    }

    private fun setValues(
        connectionPoint: MutableOriginConnectionPoint,
        forward: ForwardedHeaderValue?
    ) {
        if (forward == null) {
            return
        }

        if (forward.proto != null) {
            val proto: String = forward.proto
            connectionPoint.scheme = proto
            URLProtocol.byName[proto]?.let { p ->
                connectionPoint.port = p.defaultPort
                connectionPoint.serverPort = p.defaultPort
            }
        }

        if (forward.forParam != null) {
            val remoteHostOrAddress = forward.forParam.split(",").first().trim()
            if (remoteHostOrAddress.isNotBlank()) {
                connectionPoint.remoteHost = remoteHostOrAddress
                if (remoteHostOrAddress.isNotHostAddress()) {
                    connectionPoint.remoteAddress = remoteHostOrAddress
                }
            }
        }

        if (forward.host != null) {
            val host = forward.host.substringBefore(':')
            val port = forward.host.substringAfter(':', "")

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
    }
}

/**
 * Parsed a forwarded header value. All fields are optional as proxy could provide different fields.
 * @property host field value (optional)
 * @property by field value (optional)
 * @property forParam field value (optional)
 * @property proto field value (optional)
 * @property others contains custom field values passed by proxy
 */
public data class ForwardedHeaderValue(
    val host: String?,
    val by: String?,
    val forParam: String?,
    val proto: String?,
    val others: Map<String, String>
)

/**
 * A plugin that allows you to handle reverse proxy headers to get information
 * about the original request when a Ktor server is placed behind a reverse proxy.
 *
 * To learn how to install and use [ForwardedHeaders], see
 * [Forwarded headers](https://ktor.io/docs/forward-headers.html).
 */
public val ForwardedHeaders: ApplicationPlugin<ForwardedHeadersConfig> = createApplicationPlugin(
    "ForwardedHeaders",
    ::ForwardedHeadersConfig
) {
    fun parseForwardedValue(value: HeaderValue): ForwardedHeaderValue {
        val map = value.params.associateByTo(HashMap(), { it.name }, { it.value })

        return ForwardedHeaderValue(
            map.remove("host"),
            map.remove("by"),
            map.remove("for"),
            map.remove("proto"),
            map
        )
    }

    fun ApplicationRequest.forwardedHeaders() =
        headers.getAll(HttpHeaders.Forwarded)
            ?.flatMap { it.split(',') }
            ?.flatMap { parseHeaderValue(";$it") }?.map {
                parseForwardedValue(it)
            }

    on(CallSetup) { call ->
        val forwardedHeaders = call.request.forwardedHeaders() ?: return@on
        call.attributes.put(FORWARDED_PARSED_KEY, forwardedHeaders)
        pluginConfig.forwardedHeadersHandler.invoke(call.mutableOriginConnectionPoint, forwardedHeaders)
    }
}
