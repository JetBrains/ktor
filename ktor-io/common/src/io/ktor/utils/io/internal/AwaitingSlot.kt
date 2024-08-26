/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.internal

import kotlinx.coroutines.*
import kotlin.coroutines.*

internal val CLOSED = ClosedSlot(null)

internal class ClosedSlot(val cause: Throwable?) : CancellableContinuation<Unit> {
    override val context: CoroutineContext = EmptyCoroutineContext
    override val isActive: Boolean = false
    override val isCancelled: Boolean = cause != null
    override val isCompleted: Boolean = true

    override fun cancel(cause: Throwable?): Boolean {
        return false
    }

    @InternalCoroutinesApi
    override fun completeResume(token: Any) {
        checkClosed()
    }

    @InternalCoroutinesApi
    override fun initCancellability() = Unit

    override fun invokeOnCancellation(handler: CompletionHandler) = Unit

    @InternalCoroutinesApi
    override fun tryResumeWithException(exception: Throwable): Any? {
        checkClosed()
        return null
    }

    @ExperimentalCoroutinesApi
    override fun CoroutineDispatcher.resumeUndispatchedWithException(exception: Throwable) {
        checkClosed()
    }

    @ExperimentalCoroutinesApi
    override fun CoroutineDispatcher.resumeUndispatched(value: Unit) {
        checkClosed()
    }

    @InternalCoroutinesApi
    override fun tryResume(value: Unit, idempotent: Any?, onCancellation: ((cause: Throwable) -> Unit)?): Any? {
        checkClosed()
        return null
    }

    @InternalCoroutinesApi
    override fun tryResume(value: Unit, idempotent: Any?): Any? {
        checkClosed()
        return null
    }

    @ExperimentalCoroutinesApi
    override fun resume(value: Unit, onCancellation: ((cause: Throwable) -> Unit)?) = checkClosed()

    override fun resumeWith(result: Result<Unit>) = checkClosed()

    private fun checkClosed() {
        if (cause != null) throw cause
    }
}
