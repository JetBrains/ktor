/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.util

import io.ktor.utils.io.*

@InternalAPI
public class CaseInsensitiveSet() : MutableSet<String> {
    private val backingMap = CaseInsensitiveMap<Boolean>()

    public constructor(initial: Iterable<String>) : this() {
        addAll(initial)
    }

    override fun add(element: String): Boolean {
        if (element in backingMap) {
            return false
        }
        backingMap[element] = true
        return true
    }

    override val size: Int
        get() = backingMap.size

    override fun remove(element: String): Boolean {
        return backingMap.remove(element) == true
    }

    override fun addAll(elements: Collection<String>): Boolean {
        var added = false
        for (element in elements) {
            if (add(element)) {
                added = true
            }
        }
        return added
    }

    override fun clear() {
        backingMap.clear()
    }

    override fun removeAll(elements: Collection<String>): Boolean {
        return backingMap.keys.removeAll(elements)
    }

    override fun retainAll(elements: Collection<String>): Boolean {
        return backingMap.keys.retainAll(elements)
    }

    override fun contains(element: String): Boolean {
        return backingMap.contains(element)
    }

    override fun containsAll(elements: Collection<String>): Boolean {
        return backingMap.keys.containsAll(elements)
    }

    override fun isEmpty(): Boolean {
        return backingMap.isEmpty()
    }

    override fun iterator(): MutableIterator<String> = backingMap.keys.iterator()
}
