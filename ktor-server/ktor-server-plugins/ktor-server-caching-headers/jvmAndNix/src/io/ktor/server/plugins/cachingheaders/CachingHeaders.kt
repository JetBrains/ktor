/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cachingheaders

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*

/**
 * A configuration for the [CachingHeaders] plugin.
 */
@KtorDsl
public class CachingHeadersConfig {
    internal val optionsProviders = mutableListOf<(ApplicationCall, OutgoingContent) -> CachingOptions?>()

    init {
        optionsProviders.add { call, _ -> call.caching }
        optionsProviders.add { _, content -> content.caching }
    }

    /**
     * Provides caching options for a given [ApplicationCall] and [OutgoingContent].
     *
     * @see [CachingHeaders]
     */
    public fun options(provider: (ApplicationCall, OutgoingContent) -> CachingOptions?) {
        optionsProviders.add(provider)
    }
}

/**
 * A plugin that adds the capability to configure the `Cache-Control` and `Expires` headers used for HTTP caching.
 * The example below shows how to add the `Cache-Control` header with the `max-age` option for CSS and JSON:
 * ```kotlin
 * install(CachingHeaders) {
 *     options { call, outgoingContent ->
 *         when (outgoingContent.contentType?.withoutParameters()) {
 *             ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600))
 *             ContentType.Application.Json -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60))
 *             else -> null
 *         }
 *     }
 * }
 * ```
 *
 * You can learn more from [Caching headers](https://ktor.io/docs/caching.html).
 */
public val CachingHeaders: RouteScopedPlugin<CachingHeadersConfig> = createRouteScopedPlugin(
    "Caching Headers",
    ::CachingHeadersConfig
) {
    val optionsProviders = pluginConfig.optionsProviders.toList()

    fun optionsFor(call: ApplicationCall, content: OutgoingContent): List<CachingOptions> {
        return optionsProviders.mapNotNullTo(ArrayList(optionsProviders.size)) { it(call, content) }
    }

    on(ResponseBodyReadyForSend) { call, content ->
        val options = optionsFor(call, content)
        if (options.isEmpty()) return@on

        val headers = Headers.build {
            options.mapNotNull { it.cacheControl }
                .mergeCacheControlDirectives()
                .ifEmpty { null }?.let { directives ->
                    append(HttpHeaders.CacheControl, directives.joinToString(separator = ", "))
                }
            options.firstOrNull { it.expires != null }?.expires?.let { expires ->
                append(HttpHeaders.Expires, expires.toHttpDate())
            }
        }

        val responseHeaders = call.response.headers
        headers.forEach { name, values ->
            values.forEach { responseHeaders.append(name, it) }
        }
    }
}

/**
 * Gets or sets the [CacheControl] for this call.
 */
public var ApplicationCall.caching: CachingOptions?
    get() = attributes.getOrNull(CachingProperty)
    set(value) = when (value) {
        null -> attributes.remove(CachingProperty)
        else -> attributes.put(CachingProperty, value)
    }
