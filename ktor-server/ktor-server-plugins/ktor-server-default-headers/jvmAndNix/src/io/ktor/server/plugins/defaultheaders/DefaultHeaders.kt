/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.defaultheaders

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.DefaultHeadersConfig.*
import io.ktor.server.response.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

/**
 * A configuration for the [DefaultHeaders] plugin.
 * Allows you to configure additional default headers.
 */
@KtorDsl
public class DefaultHeadersConfig {
    /**
     * Provides a builder to append any custom headers to be sent with each request
     */
    internal val headers = HeadersBuilder()

    /**
     * Adds a standard header with the specified [name] and [value].
     */
    public fun header(name: String, value: String): Unit = headers.append(name, value)

    /**
     * Provides a time source. Useful for testing.
     */
    public var clock: Clock = Clock { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() }

    /**
     * Utility interface for obtaining timestamp.
     */
    public fun interface Clock {
        /**
         * Get current timestamp.
         */
        public fun now(): Long
    }

    internal val cachedDateText: AtomicRef<String> = atomic("")
}

/**
 * A plugin that adds the standard `Date` and `Server` HTTP headers into each response and allows you to:
 * - add additional default headers;
 * - override the `Server` header.
 *
 * The example below shows how to add a custom header:
 * ```kotlin
 * install(DefaultHeaders) {
 *     header("Custom-Header", "Some value")
 * }
 * ```
 * You can learn more from [Default headers](https://ktor.io/docs/default-headers.html).
 */
public val DefaultHeaders: RouteScopedPlugin<DefaultHeadersConfig> = createRouteScopedPlugin(
    "DefaultHeaders",
    ::DefaultHeadersConfig
) {
    val ktorVersion = if (pluginConfig.headers.getAll(HttpHeaders.Server) == null) {
        readKtorVersion(this)
    } else {
        "debug"
    }

    val headers = pluginConfig.headers.build()
    val DATE_CACHE_TIMEOUT_MILLISECONDS = 1000
    var cachedDateTimeStamp = 0L

    fun calculateDateHeader(): String {
        val captureCached = cachedDateTimeStamp
        val currentTimeStamp = pluginConfig.clock.now()
        if (captureCached + DATE_CACHE_TIMEOUT_MILLISECONDS <= currentTimeStamp) {
            cachedDateTimeStamp = currentTimeStamp
            pluginConfig.cachedDateText.value = GMTDate(currentTimeStamp).toHttpDate()
        }
        return pluginConfig.cachedDateText.value
    }

    val serverHeader = "Ktor/$ktorVersion"
    onCallRespond { call, _ ->
        headers.forEach { name, value ->
            if (!call.response.headers.contains(name)) value.forEach { call.response.header(name, it) }
        }

        if (!call.response.headers.contains(HttpHeaders.Date)) {
            call.response.header(HttpHeaders.Date, calculateDateHeader())
        }
        if (!call.response.headers.contains(HttpHeaders.Server)) {
            call.response.header(HttpHeaders.Server, serverHeader)
        }
    }
}

internal expect fun <T : Any> readKtorVersion(plugin: RouteScopedPluginBuilder<T>): String
