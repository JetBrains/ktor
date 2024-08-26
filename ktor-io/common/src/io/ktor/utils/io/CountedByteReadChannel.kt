/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.io.*

@Deprecated(
    "Counter is no longer available on the regular ByteReadChannel. Use CounterByteReadChannel instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("this.counted().totalBytesRead")
)
public val ByteReadChannel.totalBytesRead: Long
    get() = error("Counter is no longer available on the regular ByteReadChannel. Use CounterByteReadChannel instead.")

public fun ByteReadChannel.counted(): CountedByteReadChannel = CountedByteReadChannel(this)

public class CountedByteReadChannel(public val delegate: ByteReadChannel) : ByteReadChannel {
    private val buffer = Buffer()
    private var initial = 0L
    private var consumed = 0L

    public val totalBytesRead: Long
        get() {
            updateConsumed()
            return consumed
        }

    override val closedCause: Throwable?
        get() = delegate.closedCause

    override val isClosedForRead: Boolean
        get() = buffer.exhausted() && delegate.isClosedForRead

    @InternalAPI
    override val readBuffer: Source
        get() {
            updateConsumed()
            val appended = buffer.transferFrom(delegate.readBuffer)
            initial += appended
            return buffer
        }

    override suspend fun awaitContent(min: Int): Boolean {
        return delegate.awaitContent(min)
    }

    override fun cancel(cause: Throwable?) {
        delegate.cancel(cause)
        buffer.close()
    }

    private fun updateConsumed() {
        consumed += initial - buffer.size
        initial = buffer.size
    }
}
