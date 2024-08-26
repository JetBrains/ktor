/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlin.coroutines.*
import kotlin.reflect.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.doublereceive.DoubleReceive")

/**
 * A plugin that provides the ability to receive a request body several times
 * with no [RequestAlreadyConsumedException] exception.
 * This might be useful if a plugin is already consumed a request body, so you cannot receive it inside a route handler.
 * For example, you can use `DoubleReceive` to log a request body using the `CallLogging` plugin and
 * then receive a body one more time inside the `post` route handler.
 *
 * You can learn more from [DoubleReceive](https://ktor.io/docs/double-receive.html).
 *
 */
public val DoubleReceive: RouteScopedPlugin<DoubleReceiveConfig> = createRouteScopedPlugin(
    "DoubleReceive",
    ::DoubleReceiveConfig
) {
    val filters = pluginConfig.filters
    val cacheRawRequest: Boolean = pluginConfig.cacheRawRequest

    on(ReceiveBytes) { call, body ->
        if (filters.any { it(call, body) }) return@on body

        val cache = call.receiveCache

        if (cache.containsKey(call.receiveType.type)) {
            LOGGER.trace("Return cached value for ${call.receiveType.type}")
            return@on cache[call.receiveType.type]!!
        }

        if (!cacheRawRequest) {
            LOGGER.trace(
                "Return origin body because cache is not available for ${call.receiveType.type} and " +
                    "raw caching is disabled"
            )
            return@on body
        }

        val cacheValue = cache[DoubleReceiveCache::class] as? DoubleReceiveCache
        if (cacheValue != null) {
            LOGGER.trace("Return raw body from cache")
            return@on cacheValue.read()
        }

        val value = body as? ByteReadChannel ?: return@on body

        val content = if (pluginConfig.shouldUseFileCache.any { it(call) }) {
            LOGGER.trace("Storing raw body in file cache")
            FileCache(value, context = coroutineContext)
        } else {
            LOGGER.trace("Storing raw body in memory cache")
            MemoryCache(body, coroutineContext)
        }

        cache[DoubleReceiveCache::class] = content
        return@on content.read()
    }

    on(ResponseSent) { call ->
        val cache = call.receiveCache
        (cache[DoubleReceiveCache::class] as DoubleReceiveCache?)?.dispose()
    }

    on(ReceiveBodyTransformed) { call, body ->
        if (filters.any { it(call, body) }) return@on body

        val cache = call.receiveCache
        cache[body::class] = body
        LOGGER.trace("Storing transformed body for type ${body::class} in memory cache")
        return@on body
    }
}

private val ApplicationCall.receiveCache: ReceiveCache
    get() = attributes.computeIfAbsent(ReceiveCacheKey) { mutableMapOf() }

private val ReceiveCacheKey = AttributeKey<ReceiveCache>("ReceiveCache")

private typealias ReceiveCache = MutableMap<KClass<*>, Any>
