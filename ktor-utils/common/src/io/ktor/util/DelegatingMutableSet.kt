/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

internal open class DelegatingMutableSet<From, To>(
    private val delegate: MutableSet<From>,
    private val convertTo: From.() -> To,
    private val convert: To.() -> From
) : MutableSet<To> {

    open fun Collection<To>.convert(): Collection<From> = map { it.convert() }
    open fun Collection<From>.convertTo(): Collection<To> = map { it.convertTo() }

    override val size: Int = delegate.size

    override fun add(element: To): Boolean = delegate.add(element.convert())

    override fun addAll(elements: Collection<To>): Boolean = delegate.addAll(elements.convert())

    override fun clear() {
        delegate.clear()
    }

    override fun remove(element: To): Boolean = delegate.remove(element.convert())

    override fun removeAll(elements: Collection<To>): Boolean = delegate.removeAll(elements.convert().toSet())

    override fun retainAll(elements: Collection<To>): Boolean = delegate.retainAll(elements.convert().toSet())

    override fun contains(element: To): Boolean = delegate.contains(element.convert())

    override fun containsAll(elements: Collection<To>): Boolean = delegate.containsAll(elements.convert())

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun iterator(): MutableIterator<To> = object : MutableIterator<To> {
        val delegateIterator = delegate.iterator()

        override fun hasNext(): Boolean = delegateIterator.hasNext()

        override fun next(): To = delegateIterator.next().convertTo()

        override fun remove() = delegateIterator.remove()
    }

    override fun hashCode(): Int = delegate.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Set<*>) return false

        val elements = delegate.convertTo()
        return other.containsAll(elements) && elements.containsAll(other)
    }

    override fun toString(): String = delegate.convertTo().toString()
}
