/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.utils.io.*
import kotlinx.atomicfu.*

private typealias EngineFactory = HttpClientEngineFactory<HttpClientEngineConfig>

@InternalAPI
/**
 * Shared engines collection for.
 * Use [append] to enable engine auto discover in [HttpClient()].
 */
public object engines : Iterable<EngineFactory> {
    private val head = atomic<Node?>(null)

    /**
     * Add engine to head.
     */
    public fun append(item: EngineFactory) {
        while (true) {
            val current = head.value
            val new = Node(item, current)

            if (head.compareAndSet(current, new)) break
        }
    }

    /**
     * @return unfrozen collection iterator.
     */
    override fun iterator(): Iterator<EngineFactory> = object : Iterator<EngineFactory> {
        var current = head.value

        override fun next(): EngineFactory {
            val result = current!!
            current = result.next
            return result.item
        }

        override fun hasNext(): Boolean = (null != current)
    }

    private class Node(
        val item: EngineFactory,
        val next: Node?
    )
}
