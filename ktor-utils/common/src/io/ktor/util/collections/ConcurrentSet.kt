/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

/**
 * Concurrent set implemented on top of [ConcurrentMap]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.collections.ConcurrentSet)
 */
@Suppress("FunctionName")
public fun <Key : Any> ConcurrentSet(): MutableSet<Key> = object : MutableSet<Key> {
    private val delegate = ConcurrentMap<Key, Unit>()

    override fun add(element: Key): Boolean {
        if (delegate.containsKey(element)) return false
        delegate[element] = Unit
        return true
    }

    override fun addAll(elements: Collection<Key>): Boolean = elements.all { add(it) }

    override fun clear() {
        delegate.clear()
    }

    override fun iterator(): MutableIterator<Key> = delegate.keys.iterator()

    override fun remove(element: Key): Boolean = delegate.remove(element) != null

    override fun removeAll(elements: Collection<Key>): Boolean = elements.all { remove(it) }

    override fun retainAll(elements: Collection<Key>): Boolean {
        val removeList = mutableSetOf<Key>()
        for (key in delegate.keys) {
            if (key !in elements) removeList.add(key)
        }

        return removeAll(removeList)
    }

    override val size: Int
        get() = delegate.size

    override fun contains(element: Key): Boolean = delegate.containsKey(element)

    override fun containsAll(elements: Collection<Key>): Boolean = elements.containsAll(delegate.keys)

    override fun isEmpty(): Boolean = delegate.isEmpty()
}
