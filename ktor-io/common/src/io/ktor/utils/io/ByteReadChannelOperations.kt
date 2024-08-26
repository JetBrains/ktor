/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import kotlin.coroutines.*
import kotlin.jvm.*
import kotlin.math.*

@OptIn(InternalAPI::class)
public val ByteWriteChannel.availableForWrite: Int
    get() = CHANNEL_MAX_SIZE - writeBuffer.size

/**
 * Suspends the channel until it is exhausted or gets closed.
 * If the read buffer is empty, it suspends until there are bytes available in the channel.
 * Once the channel is exhausted or closed, this function returns.
 *
 * @return `true` if the channel is exhausted, `false` if EOF is reached or an error occurred.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.exhausted(): Boolean {
    if (readBuffer.exhausted()) awaitContent()
    return readBuffer.exhausted()
}

public suspend fun ByteReadChannel.toByteArray(): ByteArray {
    return readBuffer().readBytes()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readByte(): Byte {
    if (readBuffer.exhausted()) {
        awaitContent()
    }

    if (readBuffer.exhausted()) {
        throw EOFException("Not enough data available")
    }

    return readBuffer.readByte()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readShort(): Short {
    while (availableForRead < 2 && awaitContent()) {
    }

    if (availableForRead < 2) throw EOFException("Not enough data available")

    return readBuffer.readShort()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readInt(): Int {
    while (availableForRead < 4 && awaitContent()) {
    }

    if (availableForRead < 4) throw EOFException("Not enough data available")

    return readBuffer.readInt()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readLong(): Long {
    while (availableForRead < 8 && awaitContent()) {
    }

    if (availableForRead < 8) throw EOFException("Not enough data available")
    return readBuffer.readLong()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readBuffer(): Buffer {
    val result = Buffer()
    while (!isClosedForRead) {
        result.transferFrom(readBuffer)
        awaitContent()
    }

    closedCause?.let { throw it }

    return result
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readBuffer(max: Int): Buffer {
    val result = Buffer()
    var remaining = max

    while (remaining > 0 && !isClosedForRead) {
        if (readBuffer.exhausted()) awaitContent()

        val size = minOf(remaining.toLong(), readBuffer.remaining)
        readBuffer.readTo(result, size)
        remaining -= size.toInt()
    }

    return result
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.copyAndClose(channel: ByteWriteChannel): Long {
    var result = 0L
    try {
        while (!isClosedForRead) {
            result += readBuffer.transferTo(channel.writeBuffer)
            channel.flush()
            awaitContent()
        }

        closedCause?.let { throw it }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flushAndClose()
    }

    return result
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readUTF8Line(): String? {
    val result = StringBuilder()
    val completed = readUTF8LineTo(result)
    return if (!completed) null else result.toString()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.copyTo(channel: ByteWriteChannel): Long {
    var result = 0L
    try {
        while (!isClosedForRead) {
            result += readBuffer.transferTo(channel.writeBuffer)
            channel.flush()
            awaitContent()
        }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flush()
    }

    return result
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.copyTo(channel: ByteWriteChannel, limit: Long): Long {
    var remaining = limit
    try {
        while (!isClosedForRead && remaining > 0) {
            if (readBuffer.exhausted()) awaitContent()
            val count = minOf(remaining, readBuffer.remaining)
            readBuffer.readTo(channel.writeBuffer, count)
            remaining -= count
            channel.flush()
        }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flush()
    }

    return limit - remaining
}

public suspend fun ByteReadChannel.readByteArray(count: Int): ByteArray = buildPacket {
    while (size < count) {
        val packet = readPacket(count - size)
        writePacket(packet)
    }
}.readByteArray()

@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.readRemaining(): Source {
    val result = BytePacketBuilder()
    while (!isClosedForRead) {
        result.transferFrom(readBuffer)
        awaitContent()
    }

    rethrowCloseCauseIfNeeded()
    return result.buffer
}

@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.readRemaining(max: Long): Source {
    val result = BytePacketBuilder()
    var remaining = max
    while (!isClosedForRead && remaining > 0) {
        if (remaining >= readBuffer.remaining) {
            remaining -= readBuffer.remaining
            readBuffer.transferTo(result)
        } else {
            readBuffer.readTo(result, remaining)
            remaining = 0
        }

        awaitContent()
    }

    return result.buffer
}

/**
 * Reads all available bytes to [dst] buffer and returns immediately or suspends if no bytes available
 * @return number of bytes were read or `-1` if the channel has been closed
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readAvailable(
    buffer: ByteArray,
    offset: Int = 0,
    length: Int = buffer.size - offset
): Int {
    if (isClosedForRead) return -1
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return -1

    return readBuffer.readAvailable(buffer, offset, length)
}

/**
 * Invokes [block] if it is possible to read at least [min] byte
 * providing buffer to it so lambda can read from the buffer
 * up to [Buffer.readRemaining] bytes. If there are no [min] bytes available then the invocation returns -1.
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
@OptIn(InternalAPI::class, InternalIoApi::class)
public fun ByteReadChannel.readAvailable(min: Int, block: (Buffer) -> Int): Int {
    require(min > 0) { "min should be positive" }
    require(min <= CHANNEL_MAX_SIZE) { "Min($min) shouldn't be greater than $CHANNEL_MAX_SIZE" }

    if (availableForRead < min) return -1
    return block(readBuffer.buffer)
}

public class ReaderScope(
    public val channel: ByteReadChannel,
    override val coroutineContext: CoroutineContext
) : CoroutineScope

public class ReaderJob internal constructor(
    public val channel: ByteWriteChannel,
    public override val job: Job
) : ChannelJob

@Suppress("UNUSED_PARAMETER")
public fun CoroutineScope.reader(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    autoFlush: Boolean = false,
    block: suspend ReaderScope.() -> Unit
): ReaderJob = reader(coroutineContext, ByteChannel(), block)

@OptIn(InternalCoroutinesApi::class)
public fun CoroutineScope.reader(
    coroutineContext: CoroutineContext,
    channel: ByteChannel,
    block: suspend ReaderScope.() -> Unit
): ReaderJob {
    val job = launch(coroutineContext) {
        val nested = Job(this.coroutineContext.job)
        try {
            block(ReaderScope(channel, this.coroutineContext + nested))
            nested.complete()

            if (this.coroutineContext.job.isCancelled) {
                channel.cancel(this.coroutineContext.job.getCancellationException())
            }
        } catch (cause: Throwable) {
            nested.cancel("Exception thrown while reading from channel", cause)
            channel.close(cause)
        } finally {
            nested.join()
            runCatching { channel.flushAndClose() }
        }
    }.apply {
        invokeOnCompletion {
            if (it != null && !channel.isClosedForRead) {
                channel.cancel(it)
            }
        }
    }

    return ReaderJob(channel, job)
}

/**
 * Reads a packet of [packet] bytes from the channel.
 *
 * @throws EOFException if the channel is closed before the packet is fully read.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readPacket(packet: Int): Source {
    val result = Buffer()
    while (result.size < packet) {
        if (readBuffer.exhausted()) awaitContent()
        if (isClosedForRead) break

        if (readBuffer.remaining > packet - result.size) {
            readBuffer.readTo(result, packet - result.size)
        } else {
            readBuffer.transferTo(result)
        }
    }

    if (result.size < packet) {
        throw EOFException("Not enough data available, required $packet bytes but only ${result.size} available")
    }
    return result
}

public suspend fun ByteReadChannel.discardExact(value: Long) {
    if (discard(value) < value) throw EOFException("Unable to discard $value bytes")
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.discard(max: Long = Long.MAX_VALUE): Long {
    var remaining = max
    while (remaining > 0 && !isClosedForRead) {
        if (availableForRead == 0) {
            awaitContent()
        }
        val count = minOf(remaining, readBuffer.remaining)
        readBuffer.discard(count)

        remaining -= count
    }

    return max - remaining
}

/**
 * Reads a line of UTF-8 characters to the specified [out] buffer.
 * It recognizes CR, LF and CRLF as a line delimiter.
 *
 * @param out the buffer to write the line to
 * @param max the maximum number of characters to read
 *
 * @return `true` if a new line separator was found or max bytes appended. `false` if no new line separator and no bytes read.
 */
@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.readUTF8LineTo(out: Appendable, max: Int = Int.MAX_VALUE): Boolean {
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return false

    var consumed = 0
    while (!isClosedForRead) {
        awaitContent()

        val cr = readBuffer.indexOf('\r'.code.toByte())
        val lf = readBuffer.indexOf('\n'.code.toByte())

        // No new line separator
        if (cr == -1L && lf == -1L) {
            if (max == Int.MAX_VALUE) {
                val value = readBuffer.readString()
                out.append(value)
            } else {
                val count = minOf(max - consumed, readBuffer.remaining.toInt())
                consumed += count
                out.append(readBuffer.readString(count.toLong()))

                if (consumed == max) return true
            }

            continue
        }

        // CRLF fully in buffer
        if (cr >= 0 && lf == cr + 1) {
            val count = if (max != Int.MAX_VALUE) cr else minOf(max - consumed, cr.toInt()).toLong()
            out.append(readBuffer.readString(count))
            if (count == cr) readBuffer.discard(2)
            return true
        }

        // CR in buffer before LF
        if (cr >= 0 && (lf == -1L || cr < lf)) {
            val count = if (max != Int.MAX_VALUE) cr else minOf(max - consumed, cr.toInt()).toLong()
            out.append(readBuffer.readString(count))
            if (count == cr) readBuffer.discard(1)

            // Check if LF follows CR after awaiting
            if (readBuffer.exhausted()) awaitContent()
            if (readBuffer.buffer[0] == '\n'.code.toByte()) {
                readBuffer.discard(1)
            }

            return true
        }

        // LF in buffer before CR
        if (lf >= 0) {
            val count = if (max != Int.MAX_VALUE) lf else minOf(max - consumed, lf.toInt()).toLong()
            out.append(readBuffer.readString(count))
            if (count == lf) readBuffer.discard(1)
            return true
        }
    }

    return true
}

@OptIn(InternalAPI::class, UnsafeIoApi::class, InternalIoApi::class)
public suspend inline fun ByteReadChannel.read(crossinline block: suspend (ByteArray, Int, Int) -> Int): Int {
    if (isClosedForRead) return -1
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return -1

    var result = 0
    UnsafeBufferOperations.readFromHead(readBuffer.buffer) { array, start, endExclusive ->
        result = block(array, start, endExclusive)
        result
    }

    return result
}

@OptIn(InternalAPI::class, InternalIoApi::class)
public val ByteReadChannel.availableForRead: Int
    get() = readBuffer.buffer.size.toInt()

/**
 * Reads all [length] bytes to [dst] buffer or fails if channel has been closed.
 * Suspends if not enough bytes available.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readFully(out: ByteArray) {
    if (isClosedForRead) throw EOFException("Channel is already closed")

    var offset = 0
    while (offset < out.size) {
        if (readBuffer.exhausted()) awaitContent()
        if (isClosedForRead) throw EOFException("Channel is already closed")

        val count = min(out.size - offset, readBuffer.remaining.toInt())
        readBuffer.readTo(out, offset, offset + count)
        offset += count
    }
}

@InternalAPI
public fun ByteReadChannel.rethrowCloseCauseIfNeeded() {
    closedCause?.let { throw it }
}

@InternalAPI
public fun ByteWriteChannel.rethrowCloseCauseIfNeeded() {
    closedCause?.let { throw it }
}

@InternalAPI
public fun ByteChannel.rethrowCloseCauseIfNeeded() {
    closedCause?.let { throw it }
}
