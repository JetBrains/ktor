/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * Creates a buffered channel for asynchronous reading and writing of sequences of bytes.
 */
@Suppress("DEPRECATION")
public actual fun ByteChannel(autoFlush: Boolean): ByteChannel {
    return ByteChannelNative(ChunkBuffer.Empty, autoFlush)
}

/**
 * Creates channel for reading from the specified byte array.
 */
@Suppress("DEPRECATION")
public actual fun ByteReadChannel(content: ByteArray, offset: Int, length: Int): ByteReadChannel {
    if (content.isEmpty()) return ByteReadChannel.Empty
    val head = ChunkBuffer.Pool.borrow()
    var tail = head

    var start = offset
    val end = start + length
    while (true) {
        tail.reserveEndGap(8)
        val size = minOf(end - start, tail.writeRemaining)
        (tail as Buffer).writeFully(content, start, size)
        start += size

        if (start == end) break

        val current = tail
        tail = ChunkBuffer.Pool.borrow()
        current.next = tail
    }

    return ByteChannelNative(head, false).apply { close() }
}

public actual suspend fun ByteReadChannel.joinTo(dst: ByteWriteChannel, closeOnEnd: Boolean) {
    (this as ByteChannelSequentialBase).joinToImpl((dst as ByteChannelSequentialBase), closeOnEnd)
}

/**
 * Reads up to [limit] bytes from receiver channel and writes them to [dst] channel.
 * Closes [dst] channel if fails to read or write with cause exception.
 * @return a number of copied bytes
 */
public actual suspend fun ByteReadChannel.copyTo(dst: ByteWriteChannel, limit: Long): Long {
    return (this as ByteChannelSequentialBase).copyToSequentialImpl((dst as ByteChannelSequentialBase), limit)
}

@Suppress("DEPRECATION")
internal class ByteChannelNative(
    initial: ChunkBuffer,
    autoFlush: Boolean,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
) : ByteChannelSequentialBase(initial, autoFlush, pool) {
    private var attachedJob: Job? by atomic(null)

    @OptIn(InternalCoroutinesApi::class)
    @Deprecated(IO_DEPRECATION_MESSAGE)
    override fun attachJob(job: Job) {
        attachedJob?.cancel()
        attachedJob = job
        job.invokeOnCompletion(onCancelling = true) { cause ->
            attachedJob = null
            if (cause != null) cancel(cause.unwrapCancellationException())
        }
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
    override fun readAvailable(min: Int, block: (Buffer) -> Unit): Int {
        if (availableForRead < min) {
            return -1
        }

        prepareFlushedBytes()

        var result: Int
        readable.read(min) {
            val position = it.readPosition
            block(it)
            result = it.readPosition - position
        }

        afterRead(result)
        return result
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun readAvailable(dst: CPointer<ByteVar>, offset: Int, length: Int): Int =
        readAvailable(dst, offset.toLong(), length.toLong())

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun readAvailable(dst: CPointer<ByteVar>, offset: Long, length: Long): Int {
        require(offset >= 0L)
        require(length >= 0L)
        closedCause?.let { throw it }
        if (closed && availableForRead == 0) return -1

        if (length == 0L) return 0

        if (availableForRead == 0) {
            awaitSuspend(1)
        }

        if (readable.remaining < availableForRead) {
            prepareFlushedBytes()
        }

        val size = tryReadCPointer(dst, offset, length)
        afterRead(size)
        return size
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun readFully(dst: CPointer<ByteVar>, offset: Int, length: Int) {
        readFully(dst, offset.toLong(), length.toLong())
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun readFully(dst: CPointer<ByteVar>, offset: Long, length: Long) {
        require(offset >= 0L)
        require(length >= 0L)

        return when {
            closedCause != null -> throw closedCause!!
            readable.remaining >= length -> {
                val size = tryReadCPointer(dst, offset, length)
                afterRead(size)
            }

            closed -> throw EOFException(
                "Channel is closed and not enough bytes available: required $length but $availableForRead available"
            )

            else -> readFullySuspend(dst, offset, length)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readFullySuspend(dst: CPointer<ByteVar>, offset: Long, length: Long) {
        var position = offset
        var rem = length

        while (rem > 0) {
            val rc = readAvailable(dst, position, rem).toLong()
            if (rc == -1L) break
            position += rc
            rem -= rc
        }

        if (rem > 0) {
            throw EOFException(
                "Channel is closed and not enough bytes available: required $rem but $availableForRead available"
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun writeFully(src: CPointer<ByteVar>, offset: Int, length: Int) {
        return writeFully(src, offset.toLong(), length.toLong())
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun writeFully(src: CPointer<ByteVar>, offset: Long, length: Long) {
        if (availableForWrite > 0) {
            val size = tryWriteCPointer(src, offset, length).toLong()
            afterWrite(size.toInt())

            if (length == size) {
                return
            }

            flush()
            return writeFullySuspend(src, offset + size, length - size)
        }

        return writeFullySuspend(src, offset, length)
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun writeFullySuspend(src: CPointer<ByteVar>, offset: Long, length: Long) {
        var remaining = length
        var position = offset

        while (remaining > 0) {
            awaitAtLeastNBytesAvailableForWrite(1)
            val size = tryWriteCPointer(src, position, remaining).toLong()
            afterWrite(size.toInt())
            remaining -= size
            position += size
            if (remaining > 0) flush()
        }
    }

    override fun writeAvailable(min: Int, block: (Buffer) -> Unit): Int {
        if (closed) {
            throw closedCause ?: ClosedSendChannelException("Channel closed for write")
        }

        if (availableForWrite < min) {
            return -1
        }

        val size = writable.write(min) {
            val position = it.writePosition
            block(it)
            it.writePosition - position
        }

        afterWrite(size)
        return size
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Int, length: Int): Int =
        writeAvailable(src, offset.toLong(), length.toLong())

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        if (availableForWrite > 0) {
            val size = tryWriteCPointer(src, offset, length)
            afterWrite(size)
            return size
        }

        return writeAvailableSuspend(src, offset, length)
    }

    override fun close(cause: Throwable?): Boolean {
        val close = super.close(cause)
        val job = attachedJob
        if (close && job != null && cause != null) {
            if (cause is CancellationException) {
                job.cancel(cause)
            } else {
                job.cancel("Channel is cancelled", cause)
            }
        }

        return close
    }

    override fun toString(): String {
        val hashCode = hashCode().toString(16)
        return "ByteChannel[0x$hashCode, job: $attachedJob, cause: $closedCause]"
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun writeAvailableSuspend(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        awaitAtLeastNBytesAvailableForWrite(1)
        return writeAvailable(src, offset, length)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun tryWriteCPointer(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        val size = minOf(length, availableForWrite.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val ptr: CPointer<ByteVar> = (src + offset)!!
        writable.writeFully(ptr, 0, size)
        return size
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun tryReadCPointer(dst: CPointer<ByteVar>, offset: Long, length: Long): Int {
        val size = minOf(length, availableForRead.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val ptr: CPointer<ByteVar> = (dst + offset)!!
        readable.readFully(ptr, 0, size)
        return size
    }
}
