@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.core

import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.errors.EOFException
import kotlin.contracts.*

/**
 * Represents a buffer with read and write positions.
 *
 * Concurrent unsafe: the same memory could be shared between different instances of [Buffer] however you can't
 * read/write using the same [Buffer] instance from different threads.
 */
@Suppress("DEPRECATION")
@Deprecated(IO_DEPRECATION_MESSAGE)
public open class Buffer(public val memory: Memory) {

    /**
     * Current read position. It is always non-negative and will never run ahead of the [writePosition].
     * It is usually greater or equal to [startGap] reservation.
     * This position is affected by [discard], [rewind], [resetForRead], [resetForWrite], [reserveStartGap]
     * and [reserveEndGap].
     */
    public var readPosition: Int = 0
        private set

    /**
     * Current write position. It is always non-negative and will never run ahead of the [limit].
     * It is always greater or equal to the [readPosition].
     * * This position is affected by [resetForRead], [resetForWrite], [reserveStartGap]
     * and [reserveEndGap].
     */
    public var writePosition: Int = 0
        private set

    /**
     * Start gap is a reserved space in the beginning. The reserved space is usually used to write a packet length
     * in the case when it's not known before the packet constructed.
     */
    public var startGap: Int = 0
        private set

    /**
     * Write position limit. No bytes could be written ahead of this limit. When the limit is less than the [capacity]
     * then this means that there are reserved bytes in the end ([endGap]). Such a reserved space in the end could be used
     * to write size, hash and so on. Also it is useful when several buffers are connected into a chain and some
     * primitive value (e.g. `kotlin.Int`) is separated into two chunks so bytes from the second chain could be copied
     * to the reserved space of the first chunk and then the whole value could be read at once.
     */
    public var limit: Int = memory.size32
        private set

    /**
     * Number of bytes reserved in the end.
     */
    public inline val endGap: Int get() = capacity - limit

    /**
     * Buffer's capacity (including reserved [startGap] and [endGap]). Value for released buffer is unspecified.
     */
    public val capacity: Int = memory.size32

    /**
     * Number of bytes available for reading.
     */
    public inline val readRemaining: Int get() = writePosition - readPosition

    /**
     * Size of the free space available for writing in bytes.
     */
    public inline val writeRemaining: Int get() = limit - writePosition

    /**
     * Discard [count] readable bytes.
     *
     * @throws EOFException if [count] is bigger than available bytes.
     */
    public fun discardExact(count: Int = readRemaining) {
        if (count == 0) return

        val newReadPosition = readPosition + count
        if (count < 0 || newReadPosition > writePosition) {
            discardFailed(count, readRemaining)
        }
        readPosition = newReadPosition
    }

    public fun commitWritten(count: Int) {
        val newWritePosition = writePosition + count
        if (count < 0 || newWritePosition > limit) {
            commitWrittenFailed(count, writeRemaining)
        }
        writePosition = newWritePosition
    }

    /**
     * @return `true` if there is free space
     */
    @PublishedApi
    internal fun commitWrittenUntilIndex(position: Int): Boolean {
        val limit = limit
        if (position < writePosition) {
            commitWrittenFailed(position - writePosition, writeRemaining)
        }
        if (position >= limit) {
            if (position == limit) {
                writePosition = position
                return false
            }
            commitWrittenFailed(position - writePosition, writeRemaining)
        }

        writePosition = position
        return true
    }

    internal fun discardUntilIndex(position: Int) {
        if (position < 0 || position > writePosition) {
            discardFailed(position - readPosition, readRemaining)
        }

        if (readPosition != position) {
            readPosition = position
        }
    }

    /**
     * Rewind [readPosition] backward to make [count] bytes available for reading again.
     * @throws IllegalArgumentException when [count] is too big and not enough bytes available before the [readPosition]
     */
    public fun rewind(count: Int = readPosition - startGap) {
        val newReadPosition = readPosition - count
        if (newReadPosition < startGap) {
            rewindFailed(count, readPosition - startGap)
        }
        readPosition = newReadPosition
    }

    /**
     * Reserve [startGap] bytes in the beginning.
     * May move [readPosition] and [writePosition] if no bytes available for reading.
     */
    public fun reserveStartGap(startGap: Int) {
        require(startGap >= 0) { "startGap shouldn't be negative: $startGap" }

        if (readPosition >= startGap) {
            this.startGap = startGap
            return
        }

        if (readPosition == writePosition) {
            if (startGap > limit) {
                startGapReservationFailedDueToLimit(startGap)
            }

            this.writePosition = startGap
            this.readPosition = startGap
            this.startGap = startGap
            return
        }

        startGapReservationFailed(startGap)
    }

    /**
     * Reserve [endGap] bytes in the end.
     * Could move [readPosition] and [writePosition] to reserve space but only when no bytes were written or
     * all written bytes are marked as consumed (were read or discarded).
     */
    public fun reserveEndGap(endGap: Int) {
        require(endGap >= 0) { "endGap shouldn't be negative: $endGap" }

        val newLimit = capacity - endGap
        if (newLimit >= writePosition) {
            limit = newLimit
            return
        }

        if (newLimit < 0) {
            endGapReservationFailedDueToCapacity(endGap)
        }
        if (newLimit < startGap) {
            endGapReservationFailedDueToStartGap(endGap)
        }

        if (readPosition == writePosition) {
            limit = newLimit
            readPosition = newLimit
            writePosition = newLimit
            return
        }

        endGapReservationFailedDueToContent(endGap)
    }

    /**
     * Marks the whole buffer available for read and no for write
     */
    public fun resetForRead() {
        startGap = 0
        readPosition = 0

        val capacity = capacity
        writePosition = capacity
    }

    /**
     * Marks all capacity writable except the start gap reserved before. The end gap reservation is discarded.
     */
    public fun resetForWrite() {
        resetForWrite(capacity - startGap)
    }

    /**
     * Marks up to [limit] bytes of the buffer available for write and no bytes for read.
     * It does respect [startGap] already reserved. All extra bytes after the specified [limit]
     * are considered as [endGap].
     */
    public fun resetForWrite(limit: Int) {
        val startGap = startGap
        readPosition = startGap
        writePosition = startGap
        this.limit = limit
    }

    /**
     * Forget start/end gap reservations.
     */
    internal fun releaseGaps() {
        releaseStartGap(0)
        releaseEndGap()
    }

    internal fun releaseEndGap() {
        limit = capacity
    }

    internal fun releaseStartGap(newReadPosition: Int) {
        require(newReadPosition >= 0) { "newReadPosition shouldn't be negative: $newReadPosition" }
        require(newReadPosition <= readPosition) {
            "newReadPosition shouldn't be ahead of the read position: $newReadPosition > $readPosition"
        }

        readPosition = newReadPosition
        if (startGap > newReadPosition) {
            startGap = newReadPosition
        }
    }

    protected open fun duplicateTo(copy: Buffer) {
        copy.limit = limit
        copy.startGap = startGap
        copy.readPosition = readPosition
        copy.writePosition = writePosition
    }

    /**
     * Create a new [Buffer] instance pointing to the same memory and having the same positions.
     */
    public open fun duplicate(): Buffer = Buffer(memory).apply {
        duplicateTo(this)
    }

    /**
     * Peek the next unsigned byte or return `-1` if no more bytes available for reading. No bytes will be marked
     * as consumed in any case.
     * @return an unsigned byte or `-1` if not even a byte is available for reading.
     * @see tryReadByte
     * @see readByte
     */
    public fun tryPeekByte(): Int {
        val readPosition = readPosition
        if (readPosition == writePosition) return -1
        return memory[readPosition].toInt() and 0xff
    }

    /**
     * Read the next unsigned byte or return `-1` if no more bytes available for reading. The returned byte is marked
     * as consumed.
     * @return an unsigned byte or `-1` if not even a byte is available for reading.
     * @see tryPeekByte
     * @see readByte
     */
    public fun tryReadByte(): Int {
        val readPosition = readPosition
        if (readPosition == writePosition) return -1
        this.readPosition = readPosition + 1
        return memory[readPosition].toInt() and 0xff
    }

    /**
     * Read the next byte or fail with [EOFException] if it's not available. The returned byte is marked
     * as consumed.
     * @throws EOFException when not even a byte is available for reading.
     * @see tryPeekByte
     * @see tryReadByte
     */
    public fun readByte(): Byte {
        val readPosition = readPosition
        if (readPosition == writePosition) {
            throw EOFException("No readable bytes available.")
        }
        this.readPosition = readPosition + 1
        return memory[readPosition]
    }

    /**
     * Write a byte [value] at [writePosition] (incremented when written successfully).
     * @throws InsufficientSpaceException when no free space in the buffer.
     */
    public fun writeByte(value: Byte) {
        val writePosition = writePosition
        if (writePosition == limit) {
            throw InsufficientSpaceException("No free space in the buffer to write a byte")
        }
        memory[writePosition] = value
        this.writePosition = writePosition + 1
    }

    /**
     * Clear buffer's state: read/write positions, gaps and so on. Byte content is not cleaned-up.
     */
    public open fun reset() {
        releaseGaps()
        resetForWrite()
    }

    override fun toString(): String {
        return "Buffer[0x${hashCode().toString(16)}]" +
            "($readRemaining used, $writeRemaining free, ${startGap + endGap} reserved of $capacity)"
    }

    public companion object {
        /**
         * Number of bytes usually reserved in the end of chunk
         * when several instances of [io.ktor.utils.io.core.internal.ChunkBuffer] are connected into a chain (usually inside of [ByteReadPacket]
         * or [BytePacketBuilder])
         */
        public const val ReservedSize: Int = 8

        /**
         * The empty buffer singleton: it has zero capacity for read and write.
         */
        public val Empty: Buffer get() = ChunkBuffer.Empty
    }
}

/**
 * @return `true` if there are available bytes to be read
 */
@Suppress("DEPRECATION")
public inline fun Buffer.canRead(): Boolean = writePosition > readPosition

/**
 * @return `true` if there is free room to for write
 */
@Suppress("DEPRECATION")
public inline fun Buffer.canWrite(): Boolean = limit > writePosition

/**
 * Apply [block] of code with buffer's memory providing read range indices. The returned value of [block] lambda should
 * return number of bytes to be marked as consumed.
 * No read/write functions on this buffer should be called inside of [block] otherwise an undefined behaviour may occur
 * including data damage.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalContracts::class)
public inline fun Buffer.read(block: (memory: Memory, start: Int, endExclusive: Int) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val rc = block(memory, readPosition, writePosition)
    discardExact(rc)
    return rc
}

/**
 * Apply [block] of code with buffer's memory providing write range indices. The returned value of [block] lambda should
 * return number of bytes were written.
 * o read/write functions on this buffer should be called inside of [block] otherwise an undefined behaviour may occur
 * including data damage.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalContracts::class)
public inline fun Buffer.write(block: (memory: Memory, start: Int, endExclusive: Int) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val rc = block(memory, writePosition, limit)
    commitWritten(rc)
    return rc
}

internal fun discardFailed(count: Int, readRemaining: Int): Nothing {
    throw EOFException("Unable to discard $count bytes: only $readRemaining available for reading")
}

internal fun commitWrittenFailed(count: Int, writeRemaining: Int): Nothing {
    throw EOFException("Unable to discard $count bytes: only $writeRemaining available for writing")
}

internal fun rewindFailed(count: Int, rewindRemaining: Int): Nothing {
    throw IllegalArgumentException("Unable to rewind $count bytes: only $rewindRemaining could be rewinded")
}

@Suppress("DEPRECATION")
internal fun Buffer.startGapReservationFailedDueToLimit(startGap: Int): Nothing {
    if (startGap > capacity) {
        throw IllegalArgumentException("Start gap $startGap is bigger than the capacity $capacity")
    }

    throw IllegalStateException(
        "Unable to reserve $startGap start gap: there are already $endGap bytes reserved in the end"
    )
}

@Suppress("DEPRECATION")
internal fun Buffer.startGapReservationFailed(startGap: Int): Nothing {
    throw IllegalStateException(
        "Unable to reserve $startGap start gap: " +
            "there are already $readRemaining content bytes starting at offset $readPosition"
    )
}

@Suppress("DEPRECATION")
internal fun Buffer.endGapReservationFailedDueToCapacity(endGap: Int) {
    throw IllegalArgumentException("End gap $endGap is too big: capacity is $capacity")
}

@Suppress("DEPRECATION")
internal fun Buffer.endGapReservationFailedDueToStartGap(endGap: Int) {
    throw IllegalArgumentException(
        "End gap $endGap is too big: there are already $startGap bytes reserved in the beginning"
    )
}

@Suppress("DEPRECATION")
internal fun Buffer.endGapReservationFailedDueToContent(endGap: Int) {
    throw IllegalArgumentException(
        "Unable to reserve end gap $endGap:" +
            " there are already $readRemaining content bytes at offset $readPosition"
    )
}

@Suppress("DEPRECATION")
internal fun Buffer.restoreStartGap(size: Int) {
    releaseStartGap(readPosition - size)
}

public class InsufficientSpaceException(message: String = "Not enough free space") : Exception(message) {
    public constructor(
        size: Int,
        availableSpace: Int
    ) : this("Not enough free space to write $size bytes, available $availableSpace bytes.")

    public constructor(
        name: String,
        size: Int,
        availableSpace: Int
    ) : this("Not enough free space to write $name of $size bytes, available $availableSpace bytes.")

    public constructor(
        size: Long,
        availableSpace: Long
    ) : this("Not enough free space to write $size bytes, available $availableSpace bytes.")
}
