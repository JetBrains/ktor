package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.internal.*
import kotlinx.cinterop.*

/**
 * Create an instance of [String] from the specified [bytes] range starting at [offset] and bytes [length]
 * interpreting characters in the specified [charset].
 */
@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionName", "DEPRECATION")
public actual fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String {
    if (length == 0 && offset <= bytes.size) return ""

    return bytes.usePinned { pinned ->
        val ptr = pinned.addressOf(offset)
        val view = ChunkBuffer(ptr, length, null)
        view.resetForRead()
        val packet = ByteReadPacket(view, ChunkBuffer.NoPoolManuallyManaged)
        check(packet.remaining == length.toLong())
        charset.newDecoder().decode(packet, Int.MAX_VALUE)
    }
}

internal actual fun String.getCharsInternal(dst: CharArray, dstOffset: Int) {
    val length = length
    require(dstOffset + length <= dst.size)

    var dstIndex = dstOffset
    for (srcIndex in 0 until length) {
        dst[dstIndex++] = this[srcIndex]
    }
}
