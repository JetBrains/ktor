/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

internal fun newThreadContext(nThreads: Int): CoroutineContext {
    val pool = when (nThreads) {
        1 -> Executors.newSingleThreadExecutor()
        else -> Executors.newFixedThreadPool(nThreads)
    }

    return pool.asCoroutineDispatcher()
}

internal fun CoroutineContext.wrapInScope() = CoroutineScope(this)
