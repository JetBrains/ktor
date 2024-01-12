package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*
import org.khronos.webgl.*
import kotlin.contracts.*

/**
 * Execute [block] of code providing a temporary instance of [Memory] view of this byte array range
 * starting at the specified [offset] and having the specified bytes [length].
 * By default, if neither [offset] nor [length] specified, the whole array is used.
 * An instance of [Memory] provided into the [block] should be never captured and used outside of lambda.
 */
@OptIn(ExperimentalContracts::class)
public actual inline fun <R> ByteArray.useMemory(offset: Int, length: Int, block: (Memory) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return Memory(this, offset, length).let(block)
}

/**
 * Create [Memory] view for the specified [array] range starting at [offset] and the specified bytes [length].
 */
public fun Memory(array: ByteArray, offset: Int = 0, length: Int = array.size - offset): Memory {
    @Suppress("UnsafeCastFromDynamic")
    val typedArray: Int8Array = array.asDynamic()
    return Memory(typedArray, offset, length)
}

/**
 * Create [Memory] view for the specified [buffer] range starting at [offset] and the specified bytes [length].
 */
public fun Memory(buffer: ArrayBuffer, offset: Int = 0, length: Int = buffer.byteLength - offset): Memory {
    return Memory(DataView(buffer, offset, length))
}

/**
 * Create [Memory] view for the specified [view] range starting at [offset] and the specified bytes [length].
 */
public fun Memory(view: ArrayBufferView, offset: Int = 0, length: Int = view.byteLength): Memory {
    return Memory(view.buffer, view.byteOffset + offset, length)
}

@PublishedApi
internal actual object DefaultAllocator : Allocator {
    actual override fun alloc(size: Int): Memory = Memory(DataView(ArrayBuffer(size)))

    actual override fun alloc(size: Long): Memory = Memory(DataView(ArrayBuffer(size.toIntOrFail("size"))))

    actual override fun free(instance: Memory) {
    }
}
