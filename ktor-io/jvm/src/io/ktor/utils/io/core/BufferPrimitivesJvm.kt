package io.ktor.utils.io.core

import kotlinx.io.*
import kotlinx.io.Buffer
import java.nio.*

/**
 * Write [source] buffer content moving its position.
 */
@Deprecated(
    "[writeByteBuffer] is deprecated. Consider using [transferFrom] instead",
    replaceWith = ReplaceWith("this.transferFrom(source)")
)
public fun Buffer.writeByteBuffer(source: ByteBuffer) {
    transferFrom(source)
}
