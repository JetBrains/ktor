package io.ktor.utils.io.core

import io.ktor.utils.io.utils.*
import java.nio.*

public actual val PACKET_MAX_COPY_SIZE: Int = getIOIntProperty("max.copy.size", 500)

public actual typealias EOFException = java.io.EOFException

/**
 * Read exactly [n] (optional, read all remaining by default) bytes to a newly allocated byte buffer
 * @return a byte buffer containing [n] bytes
 */
public fun ByteReadPacket.readByteBuffer(
    n: Int = remaining.coerceAtMostMaxIntOrFail("Unable to make a ByteBuffer: packet is too big"),
    direct: Boolean = false
): ByteBuffer {
    val bb: ByteBuffer = if (direct) ByteBuffer.allocateDirect(n) else ByteBuffer.allocate(n)
    readFully(bb)
    bb.clear()
    return bb
}
