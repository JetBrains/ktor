@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.bits

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.contracts.*

/**
 * Execute [block] of code providing a temporary instance of [Memory] view of this byte array range
 * starting at the specified [offset] and having the specified bytes [length].
 * By default, if neither [offset] nor [length] specified, the whole array is used.
 * An instance of [Memory] provided into the [block] should be never captured and used outside of lambda.
 */
@OptIn(ExperimentalContracts::class, ExperimentalForeignApi::class)
public actual inline fun <R> ByteArray.useMemory(offset: Int, length: Int, block: (Memory) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return usePinned { pinned ->
        val memory = when {
            isEmpty() && offset == 0 && length == 0 -> MEMORY_EMPTY
            else -> Memory(pinned.addressOf(offset), length.toLong())
        }

        block(memory)
    }
}

/**
 * Create an instance of [Memory] view for memory region starting at
 * the specified [pointer] and having the specified [size] in bytes.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public inline fun Memory(pointer: CPointer<*>, size: size_t): Memory {
    require(size.convert<ULong>() <= Long.MAX_VALUE.convert<ULong>()) {
        "At most ${Long.MAX_VALUE} (kotlin.Long.MAX_VALUE) bytes range is supported."
    }

    return Memory(pointer, size.convert())
}

/**
 * Create an instance of [Memory] view for memory region starting at
 * the specified [pointer] and having the specified [size] in bytes.
 */
@OptIn(ExperimentalForeignApi::class)
public inline fun Memory(pointer: CPointer<*>, size: Int): Memory {
    return Memory(pointer.reinterpret(), size.toLong())
}

/**
 * Create an instance of [Memory] view for memory region starting at
 * the specified [pointer] and having the specified [size] in bytes.
 */
@OptIn(ExperimentalForeignApi::class)
public inline fun Memory(pointer: CPointer<*>, size: Long): Memory {
    return Memory(pointer.reinterpret(), size)
}

/**
 * Allocate memory range having the specified [size] in bytes and provide an instance of [Memory] view for this range.
 * Please note that depending of the placement type (e.g. scoped or global) this memory instance may require
 * explicit release using [free] on the same placement.
 * In particular, instances created inside of [memScoped] block do not require to be released explicitly but
 * once the scope is leaved, all produced instances should be discarded and should be never used after the scope.
 * On the contrary instances created using [nativeHeap] do require release via [nativeHeap.free].
 */
@OptIn(ExperimentalForeignApi::class)
public fun NativePlacement.allocMemory(size: Int): Memory {
    return allocMemory(size.toLong())
}

/**
 * Allocate memory range having the specified [size] in bytes and provide an instance of [Memory] view for this range.
 * Please note that depending of the placement type (e.g. scoped or global) this memory instance may require
 * explicit release using [free] on the same placement.
 * In particular, instances created inside of [memScoped] block do not require to be released explicitly but
 * once the scope is leaved, all produced instances should be discarded and should be never used after the scope.
 * On the contrary instances created using [nativeHeap] do require release via [nativeHeap.free].
 */
@OptIn(ExperimentalForeignApi::class)
public fun NativePlacement.allocMemory(size: Long): Memory {
    return Memory(allocArray(size), size)
}

/**
 * Release resources that the [memory] instance is holding.
 * This function should be only used for memory instances that are produced by [allocMemory] function
 * otherwise an undefined behaviour may occur including crash or data corruption.
 */
@OptIn(ExperimentalForeignApi::class)
public fun NativeFreeablePlacement.free(memory: Memory) {
    free(memory.pointer)
}

internal value class PlacementAllocator @OptIn(ExperimentalForeignApi::class) constructor(
    private val placement: NativeFreeablePlacement
) : Allocator {
    override fun alloc(size: Int): Memory = alloc(size.toLong())

    @OptIn(ExperimentalForeignApi::class)
    override fun alloc(size: Long): Memory = Memory(placement.allocArray(size), size)

    @OptIn(ExperimentalForeignApi::class)
    override fun free(instance: Memory) {
        placement.free(instance.pointer)
    }
}

@OptIn(ExperimentalForeignApi::class)
@PublishedApi
internal actual object DefaultAllocator : Allocator by PlacementAllocator(nativeHeap)
