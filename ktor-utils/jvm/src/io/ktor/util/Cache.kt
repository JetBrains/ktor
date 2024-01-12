/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.*
import java.util.*

/**
 * Initial capacity of [LinkedHashMap] used as [LRUCache].
 */
private const val CACHE_INITIAL_CAPACITY = 10

/**
 * Load factory of [LinkedHashMap] used as [LRUCache].
 */
private const val CACHE_LOAD_FACTOR = 0.75f

/**
 * Create a new instance of thread safe [LRUCache] and return it.
 */
@InternalAPI
public fun <K, V> createLRUCache(supplier: (K) -> V, close: (V) -> Unit, maxSize: Int): Map<K, V> =
    Collections.synchronizedMap(LRUCache(supplier, close, maxSize))

/**
 * LRU cache based on [LinkedHashMap] with specified [maxSize] and [supplier].
 */
internal class LRUCache<K, V> internal constructor(
    private val supplier: (K) -> V,
    private val close: (V) -> Unit,
    private val maxSize: Int
) : LinkedHashMap<K, V>(CACHE_INITIAL_CAPACITY, CACHE_LOAD_FACTOR, true) {

    override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
        return (size > maxSize).also {
            if (it) {
                close(eldest.value)
            }
        }
    }

    override fun get(key: K): V {
        return if (maxSize == 0) {
            supplier(key)
        } else {
            synchronized(this) {
                super.get(key)?.let { return it }

                supplier(key).let {
                    put(key, it)
                    it
                }
            }
        }
    }
}
