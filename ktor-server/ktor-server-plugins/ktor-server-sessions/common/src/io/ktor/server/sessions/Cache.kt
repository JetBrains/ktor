/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * A cache for [CookieStorage]
 */
public interface Cache<in K : Any, V : Any> {

    /**
     * Returns value for [key] or computes ans saves it if it's not found in the cache.
     */
    public suspend fun getOrCompute(key: K): V

    /**
     * Returns value for [key] or `null` if it's not found in the cache.
     */
    public fun peek(key: K): V?

    /**
     * Invalidates [key] in the cache.
     */
    public fun invalidate(key: K): V?

    /**
     * Invalidates [key] in this cache if its value equals to [value].
     */
    public fun invalidate(key: K, value: V): Boolean

    /**
     * Invalidates all keys in the cache.
     */
    public fun invalidateAll()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class BaseCache<in K : Any, V : Any>(val calc: suspend (K) -> V) : Cache<K, V> {
    private val container = ConcurrentMap<K, Deferred<V>>()

    override suspend fun getOrCompute(key: K): V {
        val coroutineContext = coroutineContext
        return container.computeIfAbsent(key) {
            CoroutineScope(coroutineContext.minusKey(Job)).async(Dispatchers.Unconfined) {
                calc(key)
            }
        }.await()
    }

    override fun peek(key: K): V? = container[key]?.let { if (!it.isActive) it.getCompleted() else null }

    override fun invalidate(key: K): V? {
        container.remove(key)?.let {
            if (!it.isActive) {
                try {
                    it.getCompleted()
                } catch (_: Throwable) {
                    // we shouldn't re-throw a failure but simply return null
                }
            }
        }

        return null
    }

    override fun invalidate(key: K, value: V): Boolean {
        container[key]?.let { l ->
            if (!l.isActive) {
                try {
                    if (l.getCompleted() == value && container.remove(key, l)) {
                        return true
                    }
                } catch (_: Throwable) {
                    return false
                }
            }
        }

        return false
    }

    override fun invalidateAll() {
        container.clear()
    }
}
