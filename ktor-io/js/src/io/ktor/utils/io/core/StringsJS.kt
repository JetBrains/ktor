package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.internal.*
import org.khronos.webgl.*

/**
 * Create an instance of [String] from the specified [bytes] range starting at [offset] and bytes [length]
 * interpreting characters in the specified [charset].
 */
@Suppress("FunctionName")
public actual fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String {
    if (offset < 0 || length < 0 || offset + length > bytes.size) {
        checkIndices(offset, length, bytes)
    }

    @Suppress("UnsafeCastFromDynamic")
    val i8: Int8Array = bytes.asDynamic() // we know that K/JS generates Int8Array for ByteBuffer
    val bufferOffset = i8.byteOffset + offset
    val buffer = i8.buffer.slice(bufferOffset, bufferOffset + length)

    @Suppress("DEPRECATION")
    val view = ChunkBuffer(Memory(buffer), null, ChunkBuffer.NoPool)
    view.resetForRead()
    @Suppress("DEPRECATION")
    val packet = ByteReadPacket(view, ChunkBuffer.NoPoolManuallyManaged)

    return charset.newDecoder().decode(packet, Int.MAX_VALUE)
}

public fun checkIndices(offset: Int, length: Int, bytes: ByteArray): Nothing {
    require(offset >= 0) { throw IndexOutOfBoundsException("offset ($offset) shouldn't be negative") }
    require(length >= 0) { throw IndexOutOfBoundsException("length ($length) shouldn't be negative") }
    require(offset + length <= bytes.size) {
        throw IndexOutOfBoundsException("offset ($offset) + length ($length) > bytes.size (${bytes.size})")
    }

    throw IndexOutOfBoundsException()
}

internal actual fun String.getCharsInternal(dst: CharArray, dstOffset: Int) {
    val length = length
    require(dstOffset + length <= dst.size)

    var dstIndex = dstOffset
    for (srcIndex in 0 until length) {
        dst[dstIndex++] = this[srcIndex]
    }
}
