/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.netty.channel.*
import io.netty.util.concurrent.*
import io.netty.util.concurrent.Future
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.*
import kotlin.coroutines.*

private val LOG = KtorSimpleLogger("io.ktor.server.netty.CIO")

@Suppress("IMPLICIT_NOTHING_AS_TYPE_PARAMETER")
private val identityErrorHandler = { t: Throwable, c: Continuation<*> ->
    c.resumeWithException(t)
}

/**
 * Suspend until the future completion.
 * Resumes with the same exception if the future completes exceptionally
 */
public suspend fun <T> Future<T>.suspendAwait(): T {
    return suspendAwait(identityErrorHandler)
}

@Suppress("IMPLICIT_NOTHING_AS_TYPE_PARAMETER")
private val wrappingErrorHandler = { t: Throwable, c: Continuation<*> ->
    if (t is IOException) {
        c.resumeWithException(ChannelWriteException("Write operation future failed", t))
    } else c.resumeWithException(t)
}

/**
 * Suspend until the future completion.
 * Wraps futures completion exceptions into [ChannelWriteException]
 */
public suspend fun <T> Future<T>.suspendWriteAwait(): T {
    return suspendAwait(wrappingErrorHandler)
}

/**
 * Suspend until the future completion handling exception from the future using [exception] function
 */
public suspend fun <T> Future<T>.suspendAwait(exception: (Throwable, Continuation<T>) -> Unit): T {
    @Suppress("BlockingMethodInNonBlockingContext")
    if (isDone) {
        try {
            return get()
        } catch (t: Throwable) {
            throw t.unwrap()
        }
    }

    return suspendCancellableCoroutine { continuation ->
        addListener(CoroutineListener(this, continuation, exception))
    }
}

internal object NettyDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !context[CurrentContextKey]!!.context.executor().inEventLoop()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val nettyContext = context[CurrentContextKey]!!.context
        val result = runCatching {
            nettyContext.executor().execute(block)
        }

        if (result.isFailure) {
            LOG.error("Failed to dispatch", result.exceptionOrNull())
        }
    }

    class CurrentContext(val context: ChannelHandlerContext) : AbstractCoroutineContextElement(CurrentContextKey)
    object CurrentContextKey : CoroutineContext.Key<CurrentContext>
}

private class CoroutineListener<T, F : Future<T>>(
    private val future: F,
    private val continuation: CancellableContinuation<T>,
    private val exception: (Throwable, Continuation<T>) -> Unit
) : GenericFutureListener<F>, CompletionHandler {
    init {
        continuation.invokeOnCancellation(this)
    }

    override fun operationComplete(future: F) {
        val value = try {
            future.get()
        } catch (t: Throwable) {
            exception(t.unwrap(), continuation)
            return
        }

        continuation.resume(value)
    }

    override fun invoke(p1: Throwable?) {
        future.removeListener(this)
        if (continuation.isCancelled) future.cancel(false)
    }
}

private tailrec fun Throwable.unwrap(): Throwable =
    if (this is ExecutionException && cause != null) cause!!.unwrap() else this
