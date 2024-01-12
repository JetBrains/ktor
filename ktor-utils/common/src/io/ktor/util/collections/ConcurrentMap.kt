/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

internal const val INITIAL_CAPACITY = 32

/**
 * Ktor concurrent map implementation. Please do not use it.
 */
public expect class ConcurrentMap<Key, Value>(
    initialCapacity: Int = INITIAL_CAPACITY
) : MutableMap<Key, Value> {

    /**
     * Computes [block] and inserts result in map. The [block] will be evaluated at most once.
     */
    public fun computeIfAbsent(key: Key, block: () -> Value): Value

    /**
     * Removes [key] from map if it is mapped to [value].
     */
    public fun remove(key: Key, value: Value): Boolean

    override fun remove(key: Key): Value?

    override fun clear()

    override fun put(key: Key, value: Value): Value?

    override fun putAll(from: Map<out Key, Value>)

    override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>>

    override val keys: MutableSet<Key>

    override val values: MutableCollection<Value>

    override fun containsKey(key: Key): Boolean

    override fun containsValue(value: Value): Boolean

    override fun get(key: Key): Value?

    override fun isEmpty(): Boolean

    override val size: Int
}
