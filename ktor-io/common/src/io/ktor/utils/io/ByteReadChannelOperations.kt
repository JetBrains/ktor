/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.utils.io

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.bytestring.*
import kotlinx.io.unsafe.*
import kotlin.coroutines.*
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
    awaitUntilReadable(Short.SIZE_BYTES)
    return readBuffer.readShort()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readInt(): Int {
    awaitUntilReadable(Int.SIZE_BYTES)
    return readBuffer.readInt()
}

/**
 * Reads a 32-bit floating-point value from the current [ByteReadChannel].
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readFloat(): Float {
    awaitUntilReadable(Float.SIZE_BYTES)
    return readBuffer.readFloat()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readLong(): Long {
    awaitUntilReadable(Long.SIZE_BYTES)
    return readBuffer.readLong()
}

/**
 * Reads a 32-bit floating-point value from the current [ByteReadChannel].
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readDouble(): Double {
    awaitUntilReadable(Double.SIZE_BYTES)
    return readBuffer.readDouble()
}

private suspend fun ByteReadChannel.awaitUntilReadable(numberOfBytes: Int) {
    while (availableForRead < numberOfBytes && awaitContent(numberOfBytes)) {
        yield()
    }

    if (availableForRead < numberOfBytes) throw EOFException("Not enough data available")
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

/**
 * Reads a line of UTF-8 characters from the `ByteReadChannel`.
 * It recognizes CR, LF and CRLF as line delimiters.
 *
 * @param max the maximum number of characters to read. Default is [Int.MAX_VALUE].
 * @return a string containing the line read, or null if channel is closed
 * @throws TooLongLineException if max is reached before encountering a newline or end of input
 */
public suspend fun ByteReadChannel.readUTF8Line(max: Int = Int.MAX_VALUE): String? {
    val result = StringBuilder()
    val completed = readUTF8LineTo(result, max)
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

    return ReaderJob(channel.onClose { job.join() }, job)
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

private const val CR: Byte = '\r'.code.toByte()
private const val LF: Byte = '\n'.code.toByte()

/**
 * Reads a line of UTF-8 characters to the specified [out] buffer.
 * It recognizes CR, LF and CRLF as a line delimiter.
 *
 * @param out the buffer to write the line to
 * @param max the maximum number of characters to read
 *
 * @return `true` if a new line separator was found or max bytes appended. `false` if no new line separator and no bytes read.
 * @throws TooLongLineException if max is reached before encountering a newline or end of input
 */
@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.readUTF8LineTo(out: Appendable, max: Int = Int.MAX_VALUE): Boolean {
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return false

    Buffer().use { lineBuffer ->
        while (!isClosedForRead) {
            while (!readBuffer.exhausted()) {
                when (val b = readBuffer.readByte()) {
                    CR -> {
                        // Check if LF follows CR after awaiting
                        if (readBuffer.exhausted()) awaitContent()
                        if (readBuffer.buffer[0] == LF) {
                            readBuffer.discard(1)
                        }
                        out.append(lineBuffer.readString())
                        return true
                    }

                    LF -> {
                        out.append(lineBuffer.readString())
                        return true
                    }

                    else -> lineBuffer.writeByte(b)
                }
            }
            if (lineBuffer.size >= max) {
                throw TooLongLineException("Line exceeds limit of $max characters")
            }

            awaitContent()
        }

        return (lineBuffer.size > 0).also { remaining ->
            if (remaining) {
                out.append(lineBuffer.readString())
            }
        }
    }
}

@OptIn(InternalAPI::class, UnsafeIoApi::class, InternalIoApi::class)
public suspend inline fun ByteReadChannel.read(crossinline block: suspend (ByteArray, Int, Int) -> Int): Int {
    if (isClosedForRead) return -1
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return -1

    var result: Int
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
 * Reads bytes from [start] to [end] into the provided [out] buffer, or fails
 * if the channel has been closed.
 *
 * Suspension occurs when there are not enough bytes available in the channel.
 *
 * @param out the buffer to write to
 * @param start the index to start writing at
 * @param end the index to write until
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readFully(out: ByteArray, start: Int = 0, end: Int = out.size) {
    var offset = start
    while (offset < end) {
        if (readBuffer.exhausted()) awaitContent()

        val count = min(end - offset, readBuffer.remaining.toInt())
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

/**
 * Reads bytes from the ByteReadChannel until a specified sequence of bytes is encountered or the specified limit is reached.
 *
 * This uses the KMP algorithm for finding the string match using a partial match table.
 *
 * @see [Knuth–Morris–Pratt algorithm](https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm)
 * @param matchString The sequence of bytes to look for.
 * @param writeChannel The channel to write the read bytes to.
 * @param limit The maximum number of bytes to read before throwing an exception.
 * @param ignoreMissing Whether to ignore the missing byteString and return the count of read bytes upon reaching the end of input.
 * @return The number of bytes read, not including the search string.
 * @throws IOException If the limit is exceeded or the byteString is not found and ignoreMissing is false.
 */
public suspend fun ByteReadChannel.readUntil(
    matchString: ByteString,
    writeChannel: ByteWriteChannel,
    limit: Long = Long.MAX_VALUE,
    ignoreMissing: Boolean = false,
): Long {
    check(matchString.size > 0) {
        "Empty match string not permitted for readUntil"
    }
    val partialMatchTable = buildPartialMatchTable(matchString)
    var matchIndex = 0
    val matchBuffer = ByteArray(matchString.size)
    var rc = 0L

    suspend fun appendPartialMatch() {
        writeChannel.writeFully(matchBuffer, 0, matchIndex)
        rc += matchIndex
        matchIndex = 0
    }

    fun resetPartialMatch(byte: Byte) {
        while (matchIndex > 0 && byte != matchString[matchIndex]) {
            matchIndex = partialMatchTable[matchIndex - 1]
        }
    }

    while (!isClosedForRead) {
        val byte = readByte()

        if (matchIndex > 0 && byte != matchString[matchIndex]) {
            appendPartialMatch()
            resetPartialMatch(byte)
        }

        if (byte == matchString[matchIndex]) {
            matchBuffer[matchIndex] = byte
            if (++matchIndex == matchString.size) {
                return rc
            }
        } else {
            writeChannel.writeByte(byte)
            rc++
        }

        if (rc > limit) {
            throw IOException("Limit of $limit bytes exceeded while scanning for \"${matchString.decodeToString()}\"")
        }
    }

    if (ignoreMissing) {
        appendPartialMatch()
        writeChannel.flush()
        return rc
    }

    throw IOException("Expected \"${matchString.toSingleLineString()}\" but encountered end of input")
}

/**
 * Helper function to build the partial match table (also known as "longest prefix suffix" table)
 */
private fun buildPartialMatchTable(byteString: ByteString): IntArray {
    val table = IntArray(byteString.size)
    var j = 0

    for (i in 1 until byteString.size) {
        while (j > 0 && byteString[i] != byteString[j]) {
            j = table[j - 1]
        }
        if (byteString[i] == byteString[j]) {
            j++
        }
        table[i] = j
    }

    return table
}

// Used in formatting errors
private fun ByteString.toSingleLineString() =
    decodeToString().replace("\n", "\\n")

/**
 * Skips the specified [byteString] in the ByteReadChannel if it is found at the current position.
 *
 * @param byteString The ByteString to look for and skip if found.
 * @return Returns `true` if the byteString was found and skipped, otherwise returns `false`.
 */
public suspend fun ByteReadChannel.skipIfFound(byteString: ByteString): Boolean {
    if (peek(byteString.size) == byteString) {
        discard(byteString.size.toLong())
        return true
    }
    return false
}

/**
 * Retrieves, but does not consume, up to the specified number of bytes from the current position in this
 * [ByteReadChannel].
 *
 * @param count The number of bytes to peek.
 * @return A [ByteString] containing the bytes that were peeked, or null if unable to peek the specified number of bytes.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.peek(count: Int): ByteString? {
    if (isClosedForRead) return null
    if (!awaitContent(count)) return null
    return readBuffer.peek().readByteString(count)
}
