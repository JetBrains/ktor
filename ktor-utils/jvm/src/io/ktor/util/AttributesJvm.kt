/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import java.util.concurrent.*

/**
 * Create JVM specific attributes instance.
 */
public actual fun Attributes(concurrent: Boolean): Attributes =
    if (concurrent) ConcurrentSafeAttributes() else HashMapAttributes()

private abstract class AttributesJvmBase : Attributes {
    protected abstract val map: MutableMap<AttributeKey<*>, Any?>

    @Suppress("UNCHECKED_CAST")
    final override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    final override operator fun contains(key: AttributeKey<*>): Boolean = map.containsKey(key)

    final override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    final override fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    final override val allKeys: List<AttributeKey<*>>
        get() = map.keys.toList()
}

private class ConcurrentSafeAttributes : AttributesJvmBase() {
    override val map: ConcurrentHashMap<AttributeKey<*>, Any?> = ConcurrentHashMap()

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value.
     * Note: [block] could be eventually evaluated twice for the same key.
     * TODO: To be discussed. Workaround for android < API 24.
     */
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        map[key]?.let { return it as T }
        val result = block()
        @Suppress("UNCHECKED_CAST")
        return (map.putIfAbsent(key, result) ?: result) as T
    }
}

private class HashMapAttributes : AttributesJvmBase() {
    override val map: MutableMap<AttributeKey<*>, Any?> = HashMap()

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value.
     * Note: [block] could be eventually evaluated twice for the same key.
     * TODO: To be discussed. Workaround for android < API 24.
     */
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        map[key]?.let { return it as T }
        val result = block()
        @Suppress("UNCHECKED_CAST")
        return (map.put(key, result) ?: result) as T
    }
}
