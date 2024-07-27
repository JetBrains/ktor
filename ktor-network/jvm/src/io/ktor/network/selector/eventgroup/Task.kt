/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.cancellation.*
import kotlin.coroutines.resume

/**
 * A task for the event loop
 *
 * It contains a runnable, which perform an i/o operation,
 * and a continuation, that will be resumed with the result
 */
internal data class Task<T>(
    val continuation: ResumableCancellable<T>,
    val runnable: suspend () -> T,
) {
    suspend fun runAndResume() {
        try {
            val result = runnable.invoke()
            continuation.resume(result)
        } catch (e: Throwable) {
            continuation.cancel(e)
        }
    }
}

internal fun <T> CancellableContinuation<T>.toResumableCancellable(): ResumableCancellable<T> {
    return object : ResumableCancellable<T> {
        override fun resume(value: T) {
            this@toResumableCancellable.resume(value)
        }

        override fun cancel(cause: Throwable?) {
            this@toResumableCancellable.cancel(cause)
        }
    }
}

internal fun <T> CompletableDeferred<T>.toResumableCancellable(): ResumableCancellable<T> {
    return object : ResumableCancellable<T> {
        override fun resume(value: T) {
            this@toResumableCancellable.complete(value)
        }

        override fun cancel(cause: Throwable?) {
            val realCause = if (cause is CancellationException) cause else CancellationException(cause)
            this@toResumableCancellable.cancel(realCause)
        }
    }
}


internal interface ResumableCancellable<T> {
    fun resume(value: T)

    fun cancel(cause: Throwable?)
}
