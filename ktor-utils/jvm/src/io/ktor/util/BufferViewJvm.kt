/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.*
import io.ktor.utils.io.core.internal.*
import kotlinx.io.*
import java.nio.channels.*

/**
 * Read from a NIO channel into the specified [buffer]
 * Could return `0` if the channel is non-blocking or [buffer] has no free space
 * @return number of bytes read (possibly 0) or -1 if EOF
 */
public fun ReadableByteChannel.read(buffer: Buffer): Int {
    if (buffer.writeRemaining == 0) return 0
    var count = 0

    buffer.writeDirect(1) { bb ->
        count = read(bb)
    }

    return count
}

/**
 * Write bytes to a NIO channel from the specified [buffer]
 * Could return `0` if the channel is non-blocking or [buffer] has no free space
 * @return number of bytes written (possibly 0)
 */
@InternalAPI
public fun WritableByteChannel.write(buffer: Buffer): Int {
    var count = 0
    buffer.readDirect { bb ->
        count = write(bb)
    }

    return count
}
