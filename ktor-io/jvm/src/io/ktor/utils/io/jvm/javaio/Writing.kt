package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import kotlinx.io.*
import java.io.*

/**
 * Copies up to [limit] bytes from [this] byte channel to [out] stream suspending on read channel
 * and blocking on output
 *
 * @return number of bytes copied
 */
@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.copyTo(out: OutputStream, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0) { "Limit shouldn't be negative: $limit" }
    var result = 0L
    while (!isClosedForRead) {
        if (readBuffer.exhausted()) awaitContent()
        result += readBuffer.buffer.size
        readBuffer.buffer.readTo(out)
    }

    return result
}
