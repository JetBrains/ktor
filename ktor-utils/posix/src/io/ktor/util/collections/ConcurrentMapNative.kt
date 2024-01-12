// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.utils.io.*
import io.ktor.utils.io.locks.*

/**
 * Ktor concurrent map implementation. Please do not use it.
 */
@OptIn(InternalAPI::class)
public actual class ConcurrentMap<Key, Value> public actual constructor(
    initialCapacity: Int
) : MutableMap<Key, Value> {
    private val delegate = LinkedHashMap<Key, Value>(initialCapacity)
    private val lock = SynchronizedObject()

    /**
     * Computes [block] and inserts result in map. The [block] will be evaluated at most once.
     */
    public actual fun computeIfAbsent(key: Key, block: () -> Value): Value = synchronized(lock) {
        if (delegate.containsKey(key)) return delegate[key]!!
        val value = block()
        delegate[key] = value
        return value
    }

    actual override val size: Int
        get() = delegate.size

    actual override fun containsKey(key: Key): Boolean = synchronized(lock) { delegate.containsKey(key) }

    actual override fun containsValue(value: Value): Boolean = synchronized(lock) { delegate.containsValue(value) }

    actual override fun get(key: Key): Value? = synchronized(lock) { delegate[key] }

    actual override fun isEmpty(): Boolean = delegate.isEmpty()

    actual override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>>
        get() = synchronized(lock) { delegate.entries }

    actual override val keys: MutableSet<Key>
        get() = synchronized(lock) { delegate.keys }

    actual override val values: MutableCollection<Value>
        get() = synchronized(lock) { delegate.values }

    actual override fun clear() {
        synchronized(lock) {
            delegate.clear()
        }
    }

    actual override fun put(key: Key, value: Value): Value? = synchronized(lock) { delegate.put(key, value) }

    actual override fun putAll(from: Map<out Key, Value>) {
        synchronized(lock) {
            delegate.putAll(from)
        }
    }

    actual override fun remove(key: Key): Value? = synchronized(lock) { delegate.remove(key) }

    public actual fun remove(key: Key, value: Value): Boolean = synchronized(lock) {
        if (delegate[key] != value) return false
        delegate.remove(key)
        return true
    }

    override fun hashCode(): Int = synchronized(lock) { delegate.hashCode() }

    override fun equals(other: Any?): Boolean = synchronized(lock) {
        if (other !is Map<*, *>) return false
        return other == delegate
    }

    override fun toString(): String = "ConcurrentMapJs by $delegate"
}
