/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine

import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Abstract implementation of [HttpClientEngine] responsible for lifecycle control of [dispatcher] and
 * [coroutineContext] as well as proper call context management. Should be considered as the best parent class for
 * custom [HttpClientEngine] implementations.
 */
public abstract class HttpClientEngineBase(private val engineName: String) : HttpClientEngine {
    private val closed = atomic(false)

    override val dispatcher: CoroutineDispatcher by lazy { config.dispatcher ?: ioDispatcher() }

    override val coroutineContext: CoroutineContext by lazy {
        SilentSupervisor() + dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val requestJob = coroutineContext[Job] as? CompletableJob ?: return

        requestJob.complete()
    }
}

/**
 * An exception indicating that the client's engine is already closed.
 */
public class ClientEngineClosedException(override val cause: Throwable? = null) :
    IllegalStateException("Client already closed")

/**
 * Closes [CoroutineDispatcher] if it's [CloseableCoroutineDispatcher] or [Closeable].
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineDispatcher.close() {
    try {
        when (this) {
            is CloseableCoroutineDispatcher -> close()
            is Closeable -> close()
        }
    } catch (ignore: Throwable) {
        // Some closeable dispatchers like Dispatchers.IO can't be closed.
    }
}

internal expect fun ioDispatcher(): CoroutineDispatcher
