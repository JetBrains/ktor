/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.io.*

/**
 * Channel for asynchronous reading of sequences of bytes.
 * This is a **single-reader channel**.
 *
 * Operations on this channel cannot be invoked concurrently.
 */
public interface ByteReadChannel {

    public val closedCause: Throwable?

    public val isClosedForRead: Boolean

    @InternalAPI
    public val readBuffer: Source

    /**
     * Suspend the channel until it has [min] bytes or gets closed. Throws exception if the channel was closed with an
     * error. If there are bytes available in the channel, this function returns immediately.
     *
     * @return return `false` eof is reached, otherwise `true`.
     */
    public suspend fun awaitContent(min: Int = 1): Boolean

    public fun cancel(cause: Throwable?)

    public companion object {
        public val Empty: ByteReadChannel = object : ByteReadChannel {
            override val closedCause: Throwable? = null

            override val isClosedForRead: Boolean
                get() = true

            @InternalAPI
            override val readBuffer: Source = Buffer()

            override suspend fun awaitContent(min: Int): Boolean = false

            override fun cancel(cause: Throwable?) {
            }
        }
    }
}

public fun ByteReadChannel.cancel() {
    cancel(IOException("Channel was cancelled"))
}
