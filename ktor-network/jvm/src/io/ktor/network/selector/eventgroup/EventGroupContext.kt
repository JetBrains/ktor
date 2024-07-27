/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import io.ktor.utils.io.*
import kotlin.coroutines.*

private val MAX_THREADS by lazy {
    Runtime.getRuntime().availableProcessors()
        .minus(2)
        .coerceAtLeast(1)
}

@InternalAPI
public class EventGroupContext(
    public val parallelism: Int,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    public companion object Key : CoroutineContext.Key<EventGroupContext>
}

@InternalAPI
internal fun CoroutineContext.eventGroupParallelism(): Int {
    return get(EventGroupContext.Key)?.parallelism ?: MAX_THREADS
}
