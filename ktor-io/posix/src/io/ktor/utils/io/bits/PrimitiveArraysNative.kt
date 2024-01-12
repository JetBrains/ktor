@file:Suppress("ConstantConditionIf")

package io.ktor.utils.io.bits

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.experimental.*

/**
 * Copies short integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadShortArray(
    offset: Int,
    destination: ShortArray,
    destinationOffset: Int,
    count: Int
) {
    loadShortArray(offset.toLong(), destination, destinationOffset, count)
}

/**
 * Copies short integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.loadShortArray(
    offset: Long,
    destination: ShortArray,
    destinationOffset: Int,
    count: Int
) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(destinationOffset, "destinationOffset")
    requirePositiveIndex(count, "count")

    if (count == 0) return

    requireRange(destinationOffset, count, destination.size, "destination")
    requireRange(offset, count * 2L, size, "memory")

    if (!Platform.isLittleEndian) {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 2L).convert())
        }
    } else if (Platform.canAccessUnaligned || isAlignedShort(offset)) {
        val source = pointer.plus(offset)!!.reinterpret<ShortVar>()

        for (index in 0 until count) {
            destination[index + destinationOffset] = source[index].reverseByteOrder()
        }
    } else {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 2L).convert())
        }

        for (index in destinationOffset until destinationOffset + count) {
            destination[index] = destination[index].reverseByteOrder()
        }
    }
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadIntArray(
    offset: Int,
    destination: IntArray,
    destinationOffset: Int,
    count: Int
) {
    loadIntArray(offset.toLong(), destination, destinationOffset, count)
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.loadIntArray(
    offset: Long,
    destination: IntArray,
    destinationOffset: Int,
    count: Int
) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(destinationOffset, "destinationOffset")
    requirePositiveIndex(count, "count")

    if (count == 0) return

    requireRange(destinationOffset, count, destination.size, "destination")
    requireRange(offset, count * 4L, size, "memory")

    if (!Platform.isLittleEndian) {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 4L).convert())
        }
        return
    }

    if (Platform.canAccessUnaligned || isAlignedInt(offset)) {
        val source = pointer.plus(offset)!!.reinterpret<IntVar>()

        for (index in 0 until count) {
            destination[index + destinationOffset] = source[index].reverseByteOrder()
        }
        return
    }

    destination.usePinned { pinned ->
        memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 4L).convert())
    }

    for (index in destinationOffset until destinationOffset + count) {
        destination[index] = destination[index].reverseByteOrder()
    }
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadLongArray(
    offset: Int,
    destination: LongArray,
    destinationOffset: Int,
    count: Int
) {
    loadLongArray(offset.toLong(), destination, destinationOffset, count)
}

/**
 * Copies regular integers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.loadLongArray(
    offset: Long,
    destination: LongArray,
    destinationOffset: Int,
    count: Int
) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(destinationOffset, "destinationOffset")
    requirePositiveIndex(count, "count")

    if (count == 0) return

    requireRange(destinationOffset, count, destination.size, "destination")
    requireRange(offset, count * 8L, size, "memory")

    if (!Platform.isLittleEndian) {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 8L).convert())
        }
    } else if (Platform.canAccessUnaligned || isAlignedLong(offset)) {
        val source = pointer.plus(offset)!!.reinterpret<LongVar>()

        for (index in 0 until count) {
            destination[index + destinationOffset] = source[index].reverseByteOrder()
        }
    } else {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 8L).convert())
        }

        for (index in destinationOffset until destinationOffset + count) {
            destination[index] = destination[index].reverseByteOrder()
        }
    }
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadFloatArray(
    offset: Int,
    destination: FloatArray,
    destinationOffset: Int,
    count: Int
) {
    loadFloatArray(offset.toLong(), destination, destinationOffset, count)
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.loadFloatArray(
    offset: Long,
    destination: FloatArray,
    destinationOffset: Int,
    count: Int
) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(destinationOffset, "destinationOffset")
    requirePositiveIndex(count, "count")

    if (count == 0) return

    requireRange(destinationOffset, count, destination.size, "destination")
    requireRange(offset, count * 4L, size, "memory")

    if (!Platform.isLittleEndian) {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 4L).convert())
        }
    } else if (Platform.canAccessUnaligned || isAlignedInt(offset)) {
        val source = pointer.plus(offset)!!.reinterpret<FloatVar>()

        for (index in 0 until count) {
            destination[index + destinationOffset] = source[index].reverseByteOrder()
        }
    } else {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 4L).convert())
        }

        for (index in destinationOffset until destinationOffset + count) {
            destination[index] = destination[index].reverseByteOrder()
        }
    }
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
public actual fun Memory.loadDoubleArray(
    offset: Int,
    destination: DoubleArray,
    destinationOffset: Int,
    count: Int
) {
    loadDoubleArray(offset.toLong(), destination, destinationOffset, count)
}

/**
 * Copies floating point numbers from this memory range from the specified [offset] and [count]
 * to the [destination] at [destinationOffset] interpreting numbers in the network order (Big Endian).
 * @param destinationOffset items
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.loadDoubleArray(
    offset: Long,
    destination: DoubleArray,
    destinationOffset: Int,
    count: Int
) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(destinationOffset, "destinationOffset")
    requirePositiveIndex(count, "count")

    if (count == 0) return

    requireRange(destinationOffset, count, destination.size, "destination")
    requireRange(offset, count * 8L, size, "memory")

    if (!Platform.isLittleEndian) {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 8L).convert())
        }
    } else if (Platform.canAccessUnaligned || isAlignedLong(offset)) {
        val source = pointer.plus(offset)!!.reinterpret<DoubleVar>()

        for (index in 0 until count) {
            destination[index + destinationOffset] = source[index].reverseByteOrder()
        }
    } else {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(destinationOffset), pointer + offset, (count * 8L).convert())
        }

        for (index in destinationOffset until destinationOffset + count) {
            destination[index] = destination[index].reverseByteOrder()
        }
    }
}

/**
 * Copies short integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeShortArray(
    offset: Int,
    source: ShortArray,
    sourceOffset: Int,
    count: Int
) {
    storeShortArray(offset.toLong(), source, sourceOffset, count)
}

/**
 * Copies short integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.storeShortArray(
    offset: Long,
    source: ShortArray,
    sourceOffset: Int,
    count: Int
) {
    storeArrayIndicesCheck(offset, sourceOffset, count, 2L, source.size, size)
    if (count == 0) return

    if (!Platform.isLittleEndian) {
        copy(source, pointer.plus(offset)!!, sourceOffset, count)
    } else if (Platform.canAccessUnaligned || isAlignedShort(offset)) {
        val destination = pointer.plus(offset)!!.reinterpret<ShortVar>()

        for (index in 0 until count) {
            destination[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        val destination = pointer.plus(offset)!!

        for (index in 0 until count) {
            storeShortSlowAt(destination.plus(index * 2)!!, source[index + sourceOffset])
        }
    }
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeIntArray(
    offset: Int,
    source: IntArray,
    sourceOffset: Int,
    count: Int
) {
    storeIntArray(offset.toLong(), source, sourceOffset, count)
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.storeIntArray(
    offset: Long,
    source: IntArray,
    sourceOffset: Int,
    count: Int
) {
    storeArrayIndicesCheck(offset, sourceOffset, count, 4L, source.size, size)
    if (count == 0) return

    if (!Platform.isLittleEndian) {
        copy(source, pointer.plus(offset)!!, sourceOffset, count)
    } else if (Platform.canAccessUnaligned || isAlignedInt(offset)) {
        val destination = pointer.plus(offset)!!.reinterpret<IntVar>()

        for (index in 0 until count) {
            destination[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        val destination = pointer.plus(offset)!!

        for (index in 0 until count) {
            storeIntSlowAt(destination.plus(index * 4)!!, source[index + sourceOffset])
        }
    }
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeLongArray(
    offset: Int,
    source: LongArray,
    sourceOffset: Int,
    count: Int
) {
    storeLongArray(offset.toLong(), source, sourceOffset, count)
}

/**
 * Copies regular integers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.storeLongArray(
    offset: Long,
    source: LongArray,
    sourceOffset: Int,
    count: Int
) {
    storeArrayIndicesCheck(offset, sourceOffset, count, 8L, source.size, size)
    if (count == 0) return

    if (!Platform.isLittleEndian) {
        copy(source, pointer.plus(offset)!!, sourceOffset, count)
    } else if (Platform.canAccessUnaligned || isAlignedShort(offset)) {
        val destination = pointer.plus(offset)!!.reinterpret<LongVar>()

        for (index in 0 until count) {
            destination[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        val destination = pointer.plus(offset)!!

        for (index in 0 until count) {
            storeLongSlowAt(destination.plus(index * 8L)!!, source[index + sourceOffset])
        }
    }
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeFloatArray(
    offset: Int,
    source: FloatArray,
    sourceOffset: Int,
    count: Int
) {
    storeFloatArray(offset.toLong(), source, sourceOffset, count)
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.storeFloatArray(
    offset: Long,
    source: FloatArray,
    sourceOffset: Int,
    count: Int
) {
    storeArrayIndicesCheck(offset, sourceOffset, count, 4L, source.size, size)
    if (count == 0) return

    if (!Platform.isLittleEndian) {
        copy(source, pointer.plus(offset)!!, sourceOffset, count)
    } else if (Platform.canAccessUnaligned || isAlignedInt(offset)) {
        val destination = pointer.plus(offset)!!.reinterpret<FloatVar>()

        for (index in 0 until count) {
            destination[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        val destination = pointer.plus(offset)!!

        for (index in 0 until count) {
            storeFloatSlowAt(destination.plus(index * 4)!!, source[index + sourceOffset])
        }
    }
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
public actual fun Memory.storeDoubleArray(
    offset: Int,
    source: DoubleArray,
    sourceOffset: Int,
    count: Int
) {
    storeDoubleArray(offset.toLong(), source, sourceOffset, count)
}

/**
 * Copies floating point numbers from the [source] array at [sourceOffset] to this memory at the specified [offset]
 * interpreting numbers in the network order (Big Endian).
 * @param sourceOffset items
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public actual fun Memory.storeDoubleArray(
    offset: Long,
    source: DoubleArray,
    sourceOffset: Int,
    count: Int
) {
    storeArrayIndicesCheck(offset, sourceOffset, count, 8L, source.size, size)
    if (count == 0) return

    if (!Platform.isLittleEndian) {
        copy(source, pointer.plus(offset)!!, sourceOffset, count)
    } else if (Platform.canAccessUnaligned || isAlignedShort(offset)) {
        val destination = pointer.plus(offset)!!.reinterpret<DoubleVar>()

        for (index in 0 until count) {
            destination[index] = source[index + sourceOffset].reverseByteOrder()
        }
    } else {
        val destination = pointer.plus(offset)!!

        for (index in 0 until count) {
            storeDoubleSlowAt(destination.plus(index * 8L)!!, source[index + sourceOffset])
        }
    }
}

internal inline fun requirePositiveIndex(value: Int, name: String) {
    if (value < 0) {
        throw IndexOutOfBoundsException("$name shouldn't be negative: $value")
    }
}

internal inline fun requirePositiveIndex(value: Long, name: String) {
    if (value < 0L) {
        throw IndexOutOfBoundsException("$name shouldn't be negative: $value")
    }
}

internal inline fun requireRange(offset: Int, length: Int, size: Int, name: String) {
    if (offset + length > size) {
        throw IndexOutOfBoundsException("Wrong offset/count for $name: offset $offset, length $length, size $size")
    }
}

internal inline fun requireRange(offset: Long, length: Long, size: Long, name: String) {
    if (offset + length > size) {
        throw IndexOutOfBoundsException("Wrong offset/count for $name: offset $offset, length $length, size $size")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun Memory.isAlignedShort(offset: Long) = (pointer.toLong() + offset) and 0b1 == 0L

@OptIn(ExperimentalForeignApi::class)
internal inline fun Memory.isAlignedInt(offset: Long) = (pointer.toLong() + offset) and 0b11 == 0L

@OptIn(ExperimentalForeignApi::class)
internal inline fun Memory.isAlignedLong(offset: Long) = (pointer.toLong() + offset) and 0b111 == 0L

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun copy(
    source: IntArray,
    destinationPointer: CPointer<ByteVar>,
    sourceOffset: Int,
    count: Int
) {
    source.usePinned { pinned ->
        memcpy(destinationPointer, pinned.addressOf(sourceOffset), (count * 4L).convert())
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun copy(
    source: ShortArray,
    destinationPointer: CPointer<ByteVar>,
    sourceOffset: Int,
    count: Int
) {
    source.usePinned { pinned ->
        memcpy(destinationPointer, pinned.addressOf(sourceOffset), (count * 2L).convert())
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun copy(
    source: LongArray,
    destinationPointer: CPointer<ByteVar>,
    sourceOffset: Int,
    count: Int
) {
    source.usePinned { pinned ->
        memcpy(destinationPointer, pinned.addressOf(sourceOffset), (count * 8L).convert())
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun copy(
    source: FloatArray,
    destinationPointer: CPointer<ByteVar>,
    sourceOffset: Int,
    count: Int
) {
    source.usePinned { pinned ->
        memcpy(destinationPointer, pinned.addressOf(sourceOffset), (count * 4L).convert())
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun copy(
    source: DoubleArray,
    destinationPointer: CPointer<ByteVar>,
    sourceOffset: Int,
    count: Int
) {
    source.usePinned { pinned ->
        memcpy(destinationPointer, pinned.addressOf(sourceOffset), (count * 8L).convert())
    }
}

private inline fun storeArrayIndicesCheck(
    offset: Long,
    sourceOffset: Int,
    count: Int,
    itemSize: Long,
    sourceSize: Int,
    memorySize: Long
) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(sourceOffset, "destinationOffset")
    requirePositiveIndex(count, "count")

    requireRange(sourceOffset, count, sourceSize, "source")
    requireRange(offset, count * itemSize, memorySize, "memory")
}
