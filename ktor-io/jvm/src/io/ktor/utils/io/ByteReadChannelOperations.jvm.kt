/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.bytestring.*
import kotlinx.io.unsafe.*
import java.nio.*
import java.nio.channels.*

/**
 * Creates a channel for reading from the specified byte buffer.
 */
public fun ByteReadChannel(content: ByteBuffer): ByteReadChannel {
    val packet = buildPacket {
        writeFully(content)
    }

    return ByteReadChannel(packet)
}

/**
 * Reads bytes from the channel and writes them to the buffer up to its limit.
 * If the channel's read buffer is exhausted, it suspends until there are bytes available.
 *
 * @param buffer the buffer to write the read bytes into
 * @return the number of bytes read and written to the buffer or -1 if the channel is closed
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readAvailable(buffer: ByteBuffer): Int {
    if (isClosedForRead) return -1
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return -1

    return readBuffer.readAtMostTo(buffer)
}

public fun ByteString(buffer: ByteBuffer): ByteString {
    val array = ByteArray(buffer.remaining())
    buffer.mark()
    buffer.get(array)
    buffer.reset()
    return ByteString(array)
}

/**
 * Copy up to [limit] bytes to blocking NIO [channel].
 * Copying to a non-blocking channel requires selection and not supported.
 * It is suspended if no data are available in a byte channel but may block if destination NIO channel blocks.
 *
 * @return number of bytes copied
 */
public suspend fun ByteReadChannel.copyTo(channel: WritableByteChannel, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0L) { "Limit shouldn't be negative: $limit" }
    if (channel is SelectableChannel && !channel.isBlocking) {
        throw IllegalArgumentException("Non-blocking channels are not supported")
    }

    if (isClosedForRead) {
        closedCause?.let { throw it }
        return 0
    }

    var copied = 0L
    val copy = { bb: ByteBuffer ->
        val rem = limit - copied

        if (rem < bb.remaining()) {
            val l = bb.limit()
            bb.limit(bb.position() + rem.toInt())

            while (bb.hasRemaining()) {
                channel.write(bb)
            }

            bb.limit(l)
            copied += rem
        } else {
            var written = 0L
            while (bb.hasRemaining()) {
                written += channel.write(bb)
            }

            copied += written
        }
    }

    while (copied < limit) {
        read(min = 0, consumer = copy)
        if (isClosedForRead) break
    }

    closedCause?.let { throw it }

    return copied
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readUntilDelimiter(delimiter: ByteString, out: ByteBuffer): Int {
    val initial = out.remaining()
    while (!isClosedForRead && out.hasRemaining()) {
        if (availableForRead == 0) {
            awaitContent()
            continue
        }

        val index = readBuffer.indexOf(delimiter)
        if (index == -1L) {
            readBuffer.readAtMostTo(out)
            continue
        }

        val count = minOf(out.remaining(), index.toInt())
        val limit = out.limit()
        out.limit(minOf(out.limit(), out.position() + count))
        readBuffer.readAtMostTo(out)
        out.limit(limit)
        break
    }

    return initial - out.remaining()
}

public suspend fun ByteReadChannel.readUntilDelimiter(delimiter: ByteBuffer, out: ByteBuffer): Int {
    return readUntilDelimiter(ByteString(delimiter), out)
}

public suspend fun ByteReadChannel.skipDelimiter(delimiter: ByteBuffer) {
    skipDelimiter(ByteString(delimiter))
}

public suspend fun ByteReadChannel.skipDelimiter(delimiter: ByteString) {
    for (i in 0 until delimiter.size) {
        val byte = readByte()
        if (byte != delimiter[i]) {
            throw IllegalStateException("Delimiter is not found")
        }
    }
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readFully(buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
        if (availableForRead == 0) {
            awaitContent()
        }
        readBuffer.readAtMostTo(buffer)
    }
}

/**
 * Invokes [block] if it is possible to read at least [min] byte
 * providing byte buffer to it so lambda can read from the buffer
 * up to [ByteBuffer.available] bytes. If there are no [min] bytes available then the invocation returns 0.
 *
 * Warning: it is not guaranteed that all of available bytes will be represented as a single byte buffer
 * eg: it could be 4 bytes available for read but the provided byte buffer could have only 2 available bytes:
 * in this case you have to invoke read again (with decreased [min] accordingly).
 *
 * @param min amount of bytes available for read, should be positive
 * @param block to be invoked when at least [min] bytes available
 *
 * @return number of consumed bytes or -1 if the block wasn't executed.
 */
@OptIn(InternalAPI::class, UnsafeIoApi::class, InternalIoApi::class)
public fun ByteReadChannel.readAvailable(block: (ByteBuffer) -> Int): Int {
    if (isClosedForRead || readBuffer.exhausted()) return -1

    var result = 0
    UnsafeBufferOperations.readFromHead(readBuffer.buffer) { array, start, endExclusive ->
        val buffer = ByteBuffer.wrap(array, start, endExclusive - start)
        result = block(buffer)
        result
    }

    return result
}

/**
 * Invokes [consumer] when it will be possible to read at least [min] bytes
 * providing byte buffer to it so lambda can read from the buffer
 * up to [ByteBuffer.remaining] bytes. If there are no [min] bytes available then the invocation could
 * suspend until the requirement will be met.
 *
 * If [min] is zero then the invocation will suspend until at least one byte available.
 *
 * Warning: it is not guaranteed that all of remaining bytes will be represented as a single byte buffer
 * eg: it could be 4 bytes available for read but the provided byte buffer could have only 2 remaining bytes:
 * in this case you have to invoke read again (with decreased [min] accordingly).
 *
 * It will fail with [EOFException] if not enough bytes ([availableForRead] < [min]) available
 * in the channel after it is closed.
 *
 * [consumer] lambda should modify buffer's position accordingly. It also could temporarily modify limit however
 * it should restore it before return. It is not recommended to access any bytes of the buffer outside of the
 * provided byte range [position(); limit()) as there could be any garbage or incomplete data.
 *
 * @param min amount of bytes available for read, should be positive or zero
 * @param consumer to be invoked when at least [min] bytes available for read
 */
@OptIn(InternalAPI::class)
public suspend inline fun ByteReadChannel.read(min: Int = 1, noinline consumer: (ByteBuffer) -> Unit) {
    require(min >= 0) { "min should be positive or zero" }
    if (availableForRead > 0 && availableForRead >= min) {
        readBuffer.read(consumer)
        return
    }

    awaitContent()
    if (isClosedForRead && min > 0) {
        throw EOFException("Not enough bytes available: required $min but $availableForRead available")
    }

    if (availableForRead > 0) readBuffer.read(consumer)
}
