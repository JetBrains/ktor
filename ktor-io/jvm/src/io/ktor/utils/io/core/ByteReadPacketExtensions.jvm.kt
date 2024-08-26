@file:Suppress("FunctionName")

package io.ktor.utils.io.core

import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import java.nio.*

public fun ByteReadPacket(byteBuffer: ByteBuffer): Source = Buffer().also {
    it.write(byteBuffer)
}

public fun Source.readAvailable(buffer: ByteBuffer): Int {
    val result = buffer.remaining()
    readAtMostTo(buffer)
    return result - buffer.remaining()
}

public fun Source.readFully(buffer: ByteBuffer) {
    while (!exhausted() && buffer.hasRemaining()) {
        readAtMostTo(buffer)
    }
}

@OptIn(UnsafeIoApi::class, InternalIoApi::class)
public fun Source.read(block: (ByteBuffer) -> Unit) {
    UnsafeBufferOperations.readFromHead(buffer) { array, start, endExclusive ->
        val wrap = ByteBuffer.wrap(array, start, endExclusive - start)
        block(wrap)

        val consumed = wrap.position() - start
        consumed
    }
}
