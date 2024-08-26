/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.io.*
import java.nio.*

public typealias LookAheadSession = LookAheadSuspendSession

public class LookAheadSuspendSession(private val channel: ByteReadChannel) {
    /**
     * Request byte buffer range skipping [skip] bytes and [atLeast] bytes length
     * @return byte buffer for the requested range or null if it is impossible to provide such a buffer
     *
     * There are the following reasons for this function to return `null`:
     * - not enough bytes available yet (should be at least `skip + atLeast` bytes available)
     * - due to buffer fragmentation it is impossible to represent the requested range as a single byte buffer
     * - end of stream encountered and all bytes were consumed
     * - channel has been closed with an exception so buffer has been recycled
     */
    @OptIn(InternalAPI::class)
    public fun request(skip: Int, atLeast: Int): ByteBuffer? {
        if (channel.readBuffer.remaining < skip + atLeast) return null
        val buffer = channel.readBuffer.preview {
            ByteBuffer.wrap(it.readByteArray())
        }
        if (skip > 0) {
            buffer.position(buffer.position() + skip)
        }
        return buffer
    }

    @OptIn(InternalAPI::class)
    public suspend fun awaitAtLeast(min: Int): Boolean {
        if (channel.readBuffer.remaining >= min) return true
        channel.awaitContent(min)
        return channel.readBuffer.remaining >= min
    }

    @OptIn(InternalAPI::class)
    public fun consumed(count: Int) {
        channel.readBuffer.discard(count.toLong())
    }
}

public suspend fun ByteReadChannel.lookAhead(block: suspend LookAheadSuspendSession.() -> Unit) {
    block(LookAheadSuspendSession(this))
}

public suspend fun ByteReadChannel.lookAheadSuspend(block: suspend LookAheadSuspendSession.() -> Unit) {
    block(LookAheadSuspendSession(this))
}
