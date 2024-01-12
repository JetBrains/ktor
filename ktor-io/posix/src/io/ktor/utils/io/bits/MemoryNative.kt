@file:Suppress("IntroduceWhenSubject")

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.experimental.*

/**
 * Memory instance with 0 size.
 */
@OptIn(ExperimentalForeignApi::class)
public actual val MEMORY_EMPTY: Memory = Memory(nativeHeap.allocArray(0), 0L)

/**
 * Represents a linear range of bytes.
 * All operations are guarded by range-checks by default however at some platforms they could be disabled
 * in release builds.
 *
 * Instance of this class has no additional state except the bytes themselves.
 */
public actual abstract class Memory internal constructor() {
    @OptIn(ExperimentalForeignApi::class)
    public abstract val pointer: CPointer<ByteVar>
    public abstract val size: Long
}

private class MemoryImpl @OptIn(ExperimentalForeignApi::class) constructor(
    override val pointer: CPointer<ByteVar>,
    override val size: Long
) : Memory() {
    init {
        requirePositiveIndex(size, "size")
    }
}

/**
 * Create memory instance from the [pointer] with specified [size].
 */
@OptIn(ExperimentalForeignApi::class)
public fun Memory(pointer: CPointer<ByteVar>, size: Long): Memory = MemoryImpl(pointer, size)

/**
 * Size of memory range in bytes.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public actual inline val Memory.size: Long get() = size

/**
 * Size of memory range in bytes represented as signed 32bit integer
 * @throws IllegalStateException when size doesn't fit into a signed 32bit integer
 */
public actual inline val Memory.size32: Int get() = size.toIntOrFail("size")

/**
 * Returns byte at [index] position.
 */
@OptIn(ExperimentalForeignApi::class)
public actual inline fun Memory.loadAt(index: Int): Byte = pointer[assertIndex(index, 1)]

/**
 * Returns byte at [index] position.
 */
@OptIn(ExperimentalForeignApi::class)
public actual inline fun Memory.loadAt(index: Long): Byte = pointer[assertIndex(index, 1)]

/**
 * Write [value] at the specified [index].
 */
@OptIn(ExperimentalForeignApi::class)
public actual inline fun Memory.storeAt(index: Int, value: Byte) {
    pointer[assertIndex(index, 1)] = value
}

/**
 * Write [value] at the specified [index]
 */
@OptIn(ExperimentalForeignApi::class)
public actual inline fun Memory.storeAt(index: Long, value: Byte) {
    pointer[assertIndex(index, 1)] = value
}

@OptIn(ExperimentalForeignApi::class)
public actual fun Memory.slice(offset: Long, length: Long): Memory {
    assertIndex(offset, length)
    if (offset == 0L && length == size) {
        return this
    }

    return Memory(pointer.plus(offset)!!, length)
}

@OptIn(ExperimentalForeignApi::class)
public actual fun Memory.slice(offset: Int, length: Int): Memory {
    assertIndex(offset, length)
    if (offset == 0 && length.toLong() == size) {
        return this
    }

    return Memory(pointer.plus(offset)!!, length.toLong())
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 * Copying bytes from a memory to itself is allowed.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public actual fun Memory.copyTo(destination: Memory, offset: Int, length: Int, destinationOffset: Int) {
    require(offset >= 0) { "offset shouldn't be negative: $offset" }
    require(length >= 0) { "length shouldn't be negative: $length" }
    require(destinationOffset >= 0) { "destinationOffset shouldn't be negative: $destinationOffset" }

    if (offset + length > size) {
        throw IndexOutOfBoundsException("offset + length > size: $offset + $length > $size")
    }
    if (destinationOffset + length > destination.size) {
        throw IndexOutOfBoundsException(
            "dst offset + length > size: $destinationOffset + $length > ${destination.size}"
        )
    }

    if (length == 0) return

    memcpy(
        destination.pointer + destinationOffset,
        pointer + offset,
        length.convert()
    )
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 * Copying bytes from a memory to itself is allowed.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public actual fun Memory.copyTo(destination: Memory, offset: Long, length: Long, destinationOffset: Long) {
    require(offset >= 0L) { "offset shouldn't be negative: $offset" }
    require(length >= 0L) { "length shouldn't be negative: $length" }
    require(destinationOffset >= 0L) { "destinationOffset shouldn't be negative: $destinationOffset" }

    if (offset + length > size) {
        throw IndexOutOfBoundsException("offset + length > size: $offset + $length > $size")
    }
    if (destinationOffset + length > destination.size) {
        throw IndexOutOfBoundsException(
            "dst offset + length > size: $destinationOffset + $length > ${destination.size}"
        )
    }

    if (length == 0L) return

    memcpy(
        destination.pointer + destinationOffset,
        pointer + offset,
        length.convert()
    )
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun Memory.copyTo(
    destination: ByteArray,
    offset: Int,
    length: Int,
    destinationOffset: Int
) {
    if (destination.isEmpty() && destinationOffset == 0 && length == 0) {
        // NOTE: this is required since pinned.getAddressOf(0) will crash with exception
        return
    }

    destination.usePinned { pinned ->
        copyTo(
            destination = Memory(pinned.addressOf(0), destination.size.toLong()),
            offset = offset,
            length = length,
            destinationOffset = destinationOffset
        )
    }
}

/**
 * Copies bytes from this memory range from the specified [offset] and [length]
 * to the [destination] at [destinationOffset].
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun Memory.copyTo(
    destination: ByteArray,
    offset: Long,
    length: Int,
    destinationOffset: Int
) {
    if (destination.isEmpty() && destinationOffset == 0 && length == 0) {
        // NOTE: this is required since pinned.getAddressOf(0) will crash with exception
        return
    }

    destination.usePinned { pinned ->
        copyTo(
            destination = Memory(pinned.addressOf(0), destination.size.toLong()),
            offset = offset,
            length = length.toLong(),
            destinationOffset = destinationOffset.toLong()
        )
    }
}

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal inline fun Memory.assertIndex(offset: Int, valueSize: Int): Int {
    assert(offset in 0..size - valueSize) {
        throw IndexOutOfBoundsException("offset $offset outside of range [0; ${size - valueSize})")
    }
    return offset
}

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal inline fun Memory.assertIndex(offset: Long, valueSize: Long): Long {
    assert(offset in 0..size - valueSize) {
        throw IndexOutOfBoundsException("offset $offset outside of range [0; ${size - valueSize})")
    }
    return offset
}

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal inline fun Short.toBigEndian(): Short {
    return when {
        !Platform.isLittleEndian -> this
        else -> reverseByteOrder()
    }
}

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal inline fun Int.toBigEndian(): Int = when {
    !Platform.isLittleEndian -> this
    else -> reverseByteOrder()
}

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal inline fun Long.toBigEndian(): Long = when {
    !Platform.isLittleEndian -> this
    else -> reverseByteOrder()
}

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal inline fun Float.toBigEndian(): Float = when {
    !Platform.isLittleEndian -> this
    else -> reverseByteOrder()
}

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal inline fun Double.toBigEndian(): Double = when {
    !Platform.isLittleEndian -> this
    else -> reverseByteOrder()
}

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public actual fun Memory.fill(offset: Long, count: Long, value: Byte) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(count, "count")
    requireRange(offset, count, size, "fill")
    if (count.toULong() > size_t.MAX_VALUE.toULong()) {
        throw IllegalArgumentException("count is too big: it shouldn't exceed size_t.MAX_VALUE")
    }

    memset(pointer + offset, value.toInt(), count.convert())
}

/**
 * Fill memory range starting at the specified [offset] with [value] repeated [count] times.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public actual fun Memory.fill(offset: Int, count: Int, value: Byte) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(count, "count")
    requireRange(offset.toLong(), count.toLong(), size, "fill")

    if (count.toULong() > size_t.MAX_VALUE.toULong()) {
        throw IllegalArgumentException("count is too big: it shouldn't exceed size_t.MAX_VALUE")
    }

    memset(pointer + offset, value.toInt(), count.convert())
}

/**
 * Copy content bytes to the memory addressed by the [destination] pointer with
 * the specified [destinationOffset] in bytes.
 */
@OptIn(ExperimentalForeignApi::class)
public fun Memory.copyTo(
    destination: CPointer<ByteVar>,
    offset: Int,
    length: Int,
    destinationOffset: Int
) {
    copyTo(destination, offset.toLong(), length.toLong(), destinationOffset.toLong())
}

/**
 * Copy content bytes to the memory addressed by the [destination] pointer with
 * the specified [destinationOffset] in bytes.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public fun Memory.copyTo(
    destination: CPointer<ByteVar>,
    offset: Long,
    length: Long,
    destinationOffset: Long
) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")
    requirePositiveIndex(destinationOffset, "destinationOffset")
    requireRange(offset, length, size, "source memory")

    memcpy(destination + destinationOffset, pointer + offset, length.convert())
}

/**
 * Copy [length] bytes to the [destination] at the specified [destinationOffset]
 * from the memory addressed by this pointer with [offset] in bytes.
 */
@OptIn(ExperimentalForeignApi::class)
public fun CPointer<ByteVar>.copyTo(destination: Memory, offset: Int, length: Int, destinationOffset: Int) {
    copyTo(destination, offset.toLong(), length.toLong(), destinationOffset.toLong())
}

/**
 * Copy [length] bytes to the [destination] at the specified [destinationOffset]
 * from the memory addressed by this pointer with [offset] in bytes.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public fun CPointer<ByteVar>.copyTo(destination: Memory, offset: Long, length: Long, destinationOffset: Long) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")
    requirePositiveIndex(destinationOffset, "destinationOffset")
    requireRange(destinationOffset, length, destination.size, "source memory")

    memcpy(destination.pointer + destinationOffset, this + offset, length.convert())
}
