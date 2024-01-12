/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.cio

import io.ktor.utils.io.*
import java.nio.*

/**
 * Read data chunks from [ByteReadChannel] using buffer
 */
@InternalAPI
public suspend inline fun ByteReadChannel.pass(buffer: ByteBuffer, block: (ByteBuffer) -> Unit) {
    while (!isClosedForRead) {
        buffer.clear()
        readAvailable(buffer)

        buffer.flip()
        block(buffer)
    }

    closedCause?.let { throw it }
}
