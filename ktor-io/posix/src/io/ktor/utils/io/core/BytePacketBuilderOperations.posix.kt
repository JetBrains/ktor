/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlinx.cinterop.*
import kotlinx.io.*
import kotlinx.io.unsafe.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class, UnsafeIoApi::class, InternalIoApi::class)
public fun Sink.write(block: (buffer: CPointer<ByteVar>, offset: Long, length: Long) -> Long): Long {
    var result = 0L
    UnsafeBufferOperations.writeToTail(this.buffer, 1) { array, start, endExclusive ->
        array.usePinned {
            val pointer = it.addressOf(0)
            result = block(pointer, start.toLong(), endExclusive.toLong())
        }

        result.toInt()
    }

    return result
}

@OptIn(ExperimentalForeignApi::class, UnsafeIoApi::class, InternalIoApi::class, UnsafeNumber::class)
public fun Sink.writeFully(buffer: CPointer<ByteVar>, offset: Long, length: Long) {
    var consumed = 0L
    while (consumed < length) {
        UnsafeBufferOperations.writeToTail(this.buffer, 1) { array, start, endExclusive ->
            val size = minOf(length - consumed, (endExclusive - start).toLong())

            array.usePinned {
                memcpy(it.addressOf(start), buffer + offset + consumed, size.convert())
            }

            consumed += size
            size.toInt()
        }
    }
}
