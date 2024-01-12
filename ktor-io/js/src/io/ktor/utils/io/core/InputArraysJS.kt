package io.ktor.utils.io.core

import org.khronos.webgl.*

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "DEPRECATION")
public fun Input.readFully(dst: Int8Array, offset: Int = 0, length: Int = dst.length - offset) {
    readFully(dst as ArrayBufferView, offset, length)
}

@Suppress("DEPRECATION")
public fun Input.readFully(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset) {
    if (remaining < length) {
        throw IllegalArgumentException("Not enough bytes available ($remaining) to read $length bytes")
    }

    var copied = 0
    takeWhile { buffer: Buffer ->
        val rc = buffer.readAvailable(dst, offset + copied, length - copied)
        if (rc > 0) copied += rc
        copied < length
    }
}

@Suppress("DEPRECATION")
public fun Input.readFully(dst: ArrayBufferView, byteOffset: Int = 0, byteLength: Int = dst.byteLength - byteOffset) {
    require(byteLength <= dst.byteLength) {
        throw IndexOutOfBoundsException("length $byteLength is greater than view size ${dst.byteLength}")
    }

    return readFully(dst.buffer, dst.byteOffset + byteOffset, byteLength)
}

@Suppress("unused", "DEPRECATION")
public fun Input.readAvailable(dst: Int8Array, offset: Int = 0, length: Int = dst.length - offset): Int {
    val remaining = remaining
    if (remaining == 0L) return -1
    val size = minOf(remaining, length.toLong()).toInt()
    readFully(dst, offset, size)
    return size
}

@Suppress("DEPRECATION")
public fun Input.readAvailable(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset): Int {
    val remaining = remaining
    if (remaining == 0L) return -1
    val size = minOf(remaining, length.toLong()).toInt()
    readFully(dst, offset, size)
    return size
}

@Suppress("unused", "DEPRECATION")
public fun Input.readAvailable(
    dst: ArrayBufferView,
    byteOffset: Int = 0,
    byteLength: Int = dst.byteLength - byteOffset
): Int {
    val remaining = remaining
    if (remaining == 0L) return -1
    val size = minOf(remaining, byteLength.toLong()).toInt()
    readFully(dst, byteOffset, size)
    return size
}
