/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

/**
 * A caching storage for sessions.
 */
public class CacheStorage(
    public val delegate: SessionStorage,
    idleTimeout: Long,
) : SessionStorage {

    private val cache = platformCache(delegate, idleTimeout)

    override suspend fun read(id: String): String {
        return cache.getOrCompute(id)
    }

    override suspend fun write(id: String, value: String) {
        val cachedValue = try {
            read(id)
        } catch (_: Throwable) {
            null
        }
        if (cachedValue == value) return
        cache.invalidate(id)
        delegate.write(id, value)
    }

    override suspend fun invalidate(id: String) {
        cache.invalidate(id)
        delegate.invalidate(id)
    }
}

internal expect fun platformCache(delegate: SessionStorage, idleTimeout: Long): Cache<String, String>
