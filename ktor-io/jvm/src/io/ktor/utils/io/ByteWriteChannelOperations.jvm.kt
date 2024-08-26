/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.unsafe.*
import java.nio.*

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeByteBuffer(value: ByteBuffer) {
    writeBuffer.writeByteBuffer(value)
    flush()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeFully(value: ByteBuffer) {
    writeBuffer.writeByteBuffer(value)
    flush()
}

@OptIn(UnsafeIoApi::class, InternalAPI::class, InternalIoApi::class)
public suspend fun ByteWriteChannel.write(min: Int = 1, block: (buffer: ByteBuffer) -> Unit) {
    UnsafeBufferOperations.writeToTail(writeBuffer.buffer, min) { array, startIndex, endIndex ->
        val buffer = ByteBuffer.wrap(array, startIndex, endIndex - startIndex)
        block(buffer)
        return@writeToTail buffer.position() - startIndex
    }
    flush()
}

/**
 * Invokes [block] if it is possible to write at least [min] byte
 * providing byte buffer to it so lambda can write to the buffer
 * up to [ByteBuffer.remaining] bytes. If there are no [min] bytes spaces available then the invocation returns 0.
 *
 * Warning: it is not guaranteed that all of remaining bytes will be represented as a single byte buffer
 * eg: it could be 4 bytes available for write but the provided byte buffer could have only 2 remaining bytes:
 * in this case you have to invoke write again (with decreased [min] accordingly).
 *
 * @param min amount of bytes available for write, should be positive
 * @param block to be invoked when at least [min] bytes free capacity available
 *
 * @return number of consumed bytes or -1 if the block wasn't executed.
 */
@OptIn(InternalAPI::class, UnsafeIoApi::class, InternalIoApi::class)
public fun ByteWriteChannel.writeAvailable(min: Int = 1, block: (ByteBuffer) -> Unit): Int {
    require(min > 0) { "min should be positive" }
    require(min <= CHANNEL_MAX_SIZE) { "Min($min) shouldn't be greater than $CHANNEL_MAX_SIZE" }

    if (isClosedForWrite) return -1

    var result = 0
    UnsafeBufferOperations.writeToTail(writeBuffer.buffer, min) { array, startIndex, endIndex ->
        val buffer = ByteBuffer.wrap(array, startIndex, endIndex - startIndex)
        block(buffer)
        result = buffer.position() - startIndex
        return@writeToTail buffer.position() - startIndex
    }

    return result
}

@OptIn(InternalAPI::class)
public fun ByteWriteChannel.writeAvailable(buffer: ByteBuffer) {
    writeBuffer.write(buffer)
}
