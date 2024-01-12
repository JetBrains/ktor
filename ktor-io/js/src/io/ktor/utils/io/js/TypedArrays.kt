package io.ktor.utils.io.js

import io.ktor.utils.io.core.*
import org.khronos.webgl.*

/**
 * Read exactly [n] bytes to a new array buffer instance
 */
public fun ByteReadPacket.readArrayBuffer(
    n: Int = remaining.coerceAtMostMaxIntOrFail("Unable to make a new ArrayBuffer: packet is too big")
): ArrayBuffer {
    val buffer = ArrayBuffer(n)
    readFully(buffer, 0, n)
    return buffer
}

/**
 * Write exactly [length] bytes from the specified [src] array buffer
 */
public fun BytePacketBuilder.writeFully(src: ArrayBuffer, offset: Int = 0, length: Int = src.byteLength - offset) {
    writeFully(Int8Array(src), offset, length)
}

/**
 * Write exactly [length] bytes from the specified [src] typed array
 */
public fun BytePacketBuilder.writeFully(src: Int8Array, offset: Int = 0, length: Int = src.length - offset) {
    var written = 0
    var rem = length

    while (rem > 0) {
        @Suppress("DEPRECATION")
        write(1) { bb: Buffer ->
            val size = minOf(bb.writeRemaining, rem)
            bb.writeFully(src, written + offset, size)
            written += size
            rem -= size
            size
        }
    }
}
