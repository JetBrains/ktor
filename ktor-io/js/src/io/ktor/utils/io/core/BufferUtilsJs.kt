@file:Suppress("ReplaceRangeToWithUntil", "RedundantModalityModifier")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import org.khronos.webgl.*
import kotlin.contracts.*

@Suppress("DEPRECATION")
public fun Buffer.readFully(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset) {
    read { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw EOFException("Not enough bytes available to read $length bytes")
        }

        memory.copyTo(dst, start, length, offset)
        length
    }
}

@Suppress("DEPRECATION")
public fun Buffer.readFully(dst: ArrayBufferView, offset: Int = 0, length: Int = dst.byteLength - offset) {
    read { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw EOFException("Not enough bytes available to read $length bytes")
        }

        memory.copyTo(dst, start, length, offset)
        length
    }
}

@Suppress("DEPRECATION")
public fun Buffer.readAvailable(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset): Int {
    if (!canRead()) return -1
    val readSize = minOf(length, readRemaining)
    readFully(dst, offset, readSize)
    return readSize
}

@Suppress("DEPRECATION")
public fun Buffer.readAvailable(dst: ArrayBufferView, offset: Int = 0, length: Int = dst.byteLength - offset): Int {
    if (!canRead()) return -1
    val readSize = minOf(length, readRemaining)
    readFully(dst, offset, readSize)
    return readSize
}

@Suppress("DEPRECATION")
public fun Buffer.writeFully(src: ArrayBuffer, offset: Int = 0, length: Int = src.byteLength) {
    write { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw InsufficientSpaceException("Not enough free space to write $length bytes")
        }

        src.copyTo(memory, offset, length, start)
        length
    }
}

@Suppress("DEPRECATION")
public fun Buffer.writeFully(src: ArrayBufferView, offset: Int = 0, length: Int = src.byteLength - offset) {
    write { memory, dstOffset, endExclusive ->
        if (endExclusive - dstOffset < length) {
            throw InsufficientSpaceException("Not enough free space to write $length bytes")
        }

        src.copyTo(memory, offset, length, dstOffset)
        length
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalContracts::class)
public inline fun Buffer.writeDirect(block: (DataView) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write { memory, start, endExclusive ->
        block(memory.slice(start, endExclusive - start).view)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalContracts::class)
public inline fun Buffer.readDirect(block: (DataView) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return read { memory, start, endExclusive ->
        block(memory.slice(start, endExclusive - start).view)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalContracts::class)
public inline fun Buffer.writeDirectInt8Array(block: (Int8Array) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write { memory, start, endExclusive ->
        block(Int8Array(memory.view.buffer, memory.view.byteOffset + start, endExclusive - start))
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalContracts::class)
public inline fun Buffer.readDirectInt8Array(block: (Int8Array) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return read { memory, start, endExclusive ->
        block(Int8Array(memory.view.buffer, memory.view.byteOffset + start, endExclusive - start))
    }
}
