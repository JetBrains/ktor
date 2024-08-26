/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.io.*

/**
 * Channel for asynchronous writing of sequences of bytes.
 * This is a **single-writer channel**.
 *
 * Operations on this channel cannot be invoked concurrently, unless explicitly specified otherwise
 * in the description. Exceptions are [close] and [flush].
 */
public interface ByteWriteChannel {

    public val isClosedForWrite: Boolean

    public val closedCause: Throwable?

    @InternalAPI
    public val writeBuffer: Sink

    public suspend fun flush()

    public suspend fun flushAndClose()

    public fun cancel(cause: Throwable?)
}

@Deprecated(
    "Async close is deprecated. Please consider replacing it with flushAndClose or cancel ",
    ReplaceWith("flushAndClose()"),
    level = DeprecationLevel.WARNING
)
public fun ByteWriteChannel.close() {
    ::flushAndClose.fireAndForget()
}

public fun ByteChannel.cancel() {
    cancel(IOException("Channel was cancelled"))
}

@Deprecated(
    "Cancel without reason is deprecated. Please provide a cause for cancellation.",
    ReplaceWith("cancel(IOException())", "kotlinx.coroutines.cancel"),
    level = DeprecationLevel.ERROR
)
public fun ByteWriteChannel.cancel() {
    cancel(IOException("Channel was cancelled"))
}

@InternalAPI
public suspend fun ByteWriteChannel.flushIfNeeded() {
    rethrowCloseCauseIfNeeded()

    if ((this as? ByteChannel)?.autoFlush == true || writeBuffer.size >= CHANNEL_MAX_SIZE) flush()
}
