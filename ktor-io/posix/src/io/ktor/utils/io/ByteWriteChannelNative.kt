// ktlint-disable filename
package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.cinterop.*

/**
 * Channel for asynchronous writing of sequences of bytes.
 * This is a **single-writer channel**.
 *
 * Operations on this channel cannot be invoked concurrently, unless explicitly specified otherwise
 * in description. Exceptions are [close] and [flush].
 */
public actual interface ByteWriteChannel {
    /**
     * Returns number of bytes that can be written without suspension. Write operations do no suspend and return
     * immediately when this number is at least the number of bytes requested for write.
     */
    public actual val availableForWrite: Int

    /**
     * Returns `true` is channel has been closed and attempting to write to the channel will cause an exception.
     */
    public actual val isClosedForWrite: Boolean

    /**
     * Returns `true` if channel flushes automatically all pending bytes after every write function call.
     * If `false` then flush only happens at manual [flush] invocation or when the buffer is full.
     */
    public actual val autoFlush: Boolean

    /**
     * Number of bytes written to the channel.
     * It is not guaranteed to be atomic so could be updated in the middle of write operation.
     */
    public actual val totalBytesWritten: Long

    /**
     * A closure causes exception or `null` if closed successfully or not yet closed
     */
    public actual val closedCause: Throwable?

    /**
     * Invokes [block] if it is possible to write at least [min] byte
     * providing buffer to it so lambda can write to the buffer
     * up to [Buffer.writeRemaining] bytes. If there are no [min] bytes spaces available then the invocation returns -1.
     *
     * Warning: it is not guaranteed that all of remaining bytes will be represented as a single byte buffer
     * eg: it could be 4 bytes available for write but the provided byte buffer could have only 2 remaining bytes:
     * in this case you have to invoke write again (with decreased [min] accordingly).
     *
     * @param min amount of bytes available for write, should be positive
     * @param block to be invoked when at least [min] bytes free capacity available
     *
     * @return number of consumed bytes or -1 if the block wasn't executed.
     */
    @Suppress("DEPRECATION")
    public fun writeAvailable(min: Int, block: (Buffer) -> Unit): Int

    /**
     * Writes as much as possible and only suspends if buffer is full
     */
    public actual suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int

    /**
     * Writes as much as possible and only suspends if buffer is full
     */
    @Suppress("DEPRECATION")
    public actual suspend fun writeAvailable(src: ChunkBuffer): Int

    /**
     * Writes as much as possible and only suspends if buffer is full
     */
    @OptIn(ExperimentalForeignApi::class)
    public suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Int, length: Int): Int

    /**
     * Writes as much as possible and only suspends if buffer is full
     */
    @OptIn(ExperimentalForeignApi::class)
    public suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Long, length: Long): Int

    /**
     * Writes all [src] bytes and suspends until all bytes written. Causes flush if buffer filled up or when [autoFlush]
     * Crashes if channel get closed while writing.
     */
    public actual suspend fun writeFully(src: ByteArray, offset: Int, length: Int)

    /**
     * Writes all [src] bytes and suspends until all bytes written. Causes flush if buffer filled up or when [autoFlush]
     * Crashes if channel get closed while writing.
     */
    @OptIn(ExperimentalForeignApi::class)
    public suspend fun writeFully(src: CPointer<ByteVar>, offset: Int, length: Int)

    /**
     * Writes all [src] bytes and suspends until all bytes written. Causes flush if buffer filled up or when [autoFlush]
     * Crashes if channel get closed while writing.
     */
    @OptIn(ExperimentalForeignApi::class)
    public suspend fun writeFully(src: CPointer<ByteVar>, offset: Long, length: Long)

    @Suppress("DEPRECATION")
    @Deprecated("Use write { } instead.")
    public actual suspend fun writeSuspendSession(visitor: suspend WriterSuspendSession.() -> Unit)

    /**
     * Writes a [packet] fully or fails if channel get closed before the whole packet has been written
     */
    public actual suspend fun writePacket(packet: ByteReadPacket)

    /**
     * Writes long number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public actual suspend fun writeLong(l: Long)

    /**
     * Writes int number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public actual suspend fun writeInt(i: Int)

    /**
     * Writes short number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public actual suspend fun writeShort(s: Short)

    /**
     * Writes byte and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public actual suspend fun writeByte(b: Byte)

    /**
     * Writes double number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public actual suspend fun writeDouble(d: Double)

    /**
     * Writes float number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public actual suspend fun writeFloat(f: Float)

    public actual suspend fun awaitFreeSpace()

    /**
     * Closes this channel with an optional exceptional [cause].
     * It flushes all pending write bytes (via [flush]).
     * This is an idempotent operation -- repeated invocations of this function have no effect and return `false`.
     *
     * A channel that was closed without a [cause], is considered to be _closed normally_.
     * A channel that was closed with non-null [cause] is called a _failed channel_. Attempts to read or
     * write on a failed channel throw this cause exception.
     *
     * After invocation of this operation [isClosedForWrite] starts returning `true` and
     * all subsequent write operations throw [ClosedWriteChannelException] or the specified [cause].
     * However, [isClosedForRead][ByteReadChannel.isClosedForRead] on the side of [ByteReadChannel]
     * starts returning `true` only after all written bytes have been read.
     *
     * Please note that if the channel has been closed with cause and it has been provided by [reader] or [writer]
     * coroutine then the corresponding coroutine will be cancelled with [cause]. If no [cause] provided then no
     * cancellation will be propagated.
     */
    public actual fun close(cause: Throwable?): Boolean

    /**
     * Flushes all pending write bytes making them available for read.
     *
     * This function is thread-safe and can be invoked in any thread at any time.
     * It does nothing when invoked on a closed channel.
     */
    public actual fun flush()

    @Suppress("DEPRECATION")
    public actual suspend fun writeFully(src: Buffer)

    public actual suspend fun writeFully(memory: Memory, startIndex: Int, endIndex: Int)
}
