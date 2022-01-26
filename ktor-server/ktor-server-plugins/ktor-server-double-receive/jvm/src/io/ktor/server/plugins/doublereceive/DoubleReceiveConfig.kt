/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.reflect.*

/**
 * [DoubleReceive] Plugin configuration.
 */
public class DoubleReceiveConfig {
    internal val filters = mutableListOf<(ApplicationCall, ApplicationReceiveRequest) -> Boolean>()

    /**
     * Cache request before applying any transformations.
     *
     * This is useful for example when you want to use receive request body twice with different types or receive data
     * as stream multiple times.
     */
    public var cacheRawRequest: Boolean = true

    /**
     * Add filter to [DoubleReceive] plugin.
     * If [block] returns `true`, the body will not be cached in memory.
     */
    public fun excludeFromCache(block: (call: ApplicationCall, receiveState: ApplicationReceiveRequest) -> Boolean) {
        filters += block
    }

    /**
     * Exclude requests with content size greater than [maxSize] from cache.
     */
    public fun maxSize(limit: Long) {
        excludeFromCache { call, _ ->
            val contentLength = call.request.contentLength() ?: return@excludeFromCache true
            return@excludeFromCache contentLength > limit
        }
    }

    /**
     * Exclude specific type from caching.
     */
    public inline fun <reified T : Any> exclude() {
        val excludeType = typeInfo<T>()
        excludeFromCache { _, state -> state.typeInfo != excludeType }
    }
}
